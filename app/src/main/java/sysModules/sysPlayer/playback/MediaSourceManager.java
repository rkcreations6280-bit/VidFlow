package sysModules.sysPlayer.playback;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.mediasource.FailedMediaSource;
import sysModules.sysPlayer.mediasource.FailedMediaSource.MediaSourceResolutionException;
import sysModules.sysPlayer.mediasource.FailedMediaSource.StreamInfoLoadException;
import sysModules.sysPlayer.mediasource.LoadedMediaSource;
import sysModules.sysPlayer.mediasource.ManagedMediaSource;
import sysModules.sysPlayer.mediasource.ManagedMediaSourcePlaylist;
import sysModules.sysPlayer.mediaitem.MediaItemTag;
import sysModules.sysPlayer.queue.PlayQueue;
import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.model.QueueEvent;
import sysModules.sysPlayer.queue.QueueListener;

/**
 * Manages the lifecycle of {@link ManagedMediaSource} instances in a
 * {@link ManagedMediaSourcePlaylist}, coordinating preloading, blocking/unblocking,
 * and synchronization with the play queue.
 * <p>
 * This is the callback-based equivalent of NewPipe's RxJava-based MediaSourceManager.
 * All reactive streams are replaced with {@link Handler} postDelayed and direct callbacks.
 * <p>
 * <strong>Key concepts:</strong>
 * <ul>
 * <li>WINDOW_SIZE: how many sources before/after the current to preload (default 1)</li>
 * <li>Block/unblock: stops the player when the current source isn't ready, resumes when it is</li>
 * <li>Debounced loading: rapid queue events are collapsed into a single load pass</li>
 * <li>Edge signal: periodic check that triggers loading when playback approaches the end</li>
 * </ul>
 */
public class MediaSourceManager {
    private static final LoggerUtils logger = LoggerUtils.from(MediaSourceManager.class);

    private static final int WINDOW_SIZE = 1;
    private static final int MAXIMUM_LOADER_SIZE = WINDOW_SIZE * 2 + 1;

    private static final long LOAD_DEBOUNCE_MILLIS = 400L;
    private static final long PLAYBACK_NEAR_END_GAP_MILLIS =
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);
    private static final long PROGRESS_UPDATE_INTERVAL_MILLIS =
            TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS);

    private static final long CACHE_EXPIRATION_YOUTUBE_MILLIS = 300_000L;
    private static final long CACHE_EXPIRATION_DEFAULT_MILLIS = 180_000L;
    private static final long RETRY_WAIT_MILLIS = 3_000L;

    @NonNull
    private final PlaybackListener playbackListener;
    @NonNull
    private final PlayQueue playQueue;
    @NonNull
    private final Handler mainHandler;

    // ─── Debounced loading (replaces PublishSubject + debounce) ──────────
    private final Runnable debouncedLoadRunnable = this::loadImmediate;
    private final Runnable edgeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (disposed) return;
            if (playbackListener.isApproachingPlaybackEdge(PLAYBACK_NEAR_END_GAP_MILLIS)) {
                maybeLoadNeighbors();
            }
            mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MILLIS);
        }
    };

    // ─── Queue listener (replaces RxJava Subscription) ──────────────────
    private final QueueListener queueListener = this::onPlayQueueChanged;

    // ─── Loader management (replaces CompositeDisposable) ────────────────
    private final Set<PlayQueueItem> loadingItems =
            Collections.synchronizedSet(new HashSet<>());
    private final java.util.concurrent.ConcurrentHashMap<PlayQueueItem, Integer> loadingIndices =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Thread> loaderThreads = new CopyOnWriteArrayList<>();

    // ─── State ───────────────────────────────────────────────────────────
    @NonNull
    private final ManagedMediaSourcePlaylist playlist;
    private volatile boolean isBlocked;
    private volatile boolean disposed;
    private final Map<Integer, ManagedMediaSource> resolvedSources = new ConcurrentHashMap<>();

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this.playbackListener = listener;
        this.playQueue = playQueue;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.playlist = new ManagedMediaSourcePlaylist();
        this.isBlocked = false;
        this.disposed = false;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    public void dispose() {
        disposed = true;
        mainHandler.removeCallbacks(debouncedLoadRunnable);
        mainHandler.removeCallbacks(edgeCheckRunnable);
        cancelAllLoaders();
        // Cancel any pending fetch threads in the queue items
        for (PlayQueueItem item : playQueue.getStreams()) {
            item.cancelFetch();
        }
        playQueue.removeListener(queueListener);
    }

    public void init() {
        playQueue.addListener(queueListener);
        mainHandler.post(edgeCheckRunnable);
        // Initial population and load
        onPlayQueueChanged(new QueueEvent.InitEvent());
    }

    @NonNull
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    @NonNull
    public com.google.android.exoplayer2.source.ConcatenatingMediaSource getParentMediaSource() {
        return playlist.getParentMediaSource();
    }

    // ─── Queue event handling (replaces RxJava Subscriber) ────────────────

    private void onPlayQueueChanged(final QueueEvent event) {
        if (disposed) return;
        logger.debug("onPlayQueueChanged: type=" + event.getType()
                + " queue.size=" + playQueue.size()
                + " queue.index=" + playQueue.getIndex()
                + " queue.isEmpty=" + playQueue.isEmpty()
                + " queue.isComplete=" + playQueue.isComplete());

        if (playQueue.isEmpty() && playQueue.isComplete()) {
            playbackListener.onPlaybackShutdown();
            return;
        }

        // Event-specific playlist manipulation
        switch (event.getType()) {
            case INIT:
            case ERROR:
                maybeBlock();
                // fall through
            case APPEND:
                populateSources();
                break;
            case SELECT:
                maybeRenewCurrentIndex();
                break;
            case REMOVE:
                final QueueEvent.RemoveEvent removeEvent = (QueueEvent.RemoveEvent) event;
                playlist.remove(removeEvent.getRemoveIndex());
                break;
            case MOVE:
                final QueueEvent.MoveEvent moveEvent = (QueueEvent.MoveEvent) event;
                playlist.move(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case REORDER:
                final QueueEvent.ReorderEvent reorderEvent = (QueueEvent.ReorderEvent) event;
                playlist.move(reorderEvent.getFromSelectedIndex(),
                        reorderEvent.getToSelectedIndex());
                break;
            case RECOVERY:
            default:
                break;
        }

        // Loading strategy: critical events load immediately, others debounced
        switch (event.getType()) {
            case INIT:
            case REORDER:
            case ERROR:
            case SELECT:
                loadImmediate();
                break;
            case APPEND:
            case REMOVE:
            case MOVE:
            case RECOVERY:
            default:
                loadDebounced();
                break;
        }

        // Notify queue edits
        switch (event.getType()) {
            case APPEND:
            case REMOVE:
            case MOVE:
            case REORDER:
                playbackListener.onPlayQueueEdited();
                break;
        }

        if (!isPlayQueueReady()) {
            maybeBlock();
            playQueue.fetch();
        }
    }

    // ─── Playback locking ────────────────────────────────────────────────

    private boolean isPlayQueueReady() {
        final boolean isWindowLoaded =
                playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
        return playQueue.isComplete() || isWindowLoaded;
    }

    private boolean isPlaybackReady() {
        if (playlist.size() != playQueue.size()) {
            logger.debug("isPlaybackReady: size mismatch playlist=" + playlist.size()
                    + " queue=" + playQueue.size());
            return false;
        }
        final int currentIndex = playQueue.getIndex();
        final ManagedMediaSource resolved = resolvedSources.get(currentIndex);
        if (resolved == null) {
            logger.debug("isPlaybackReady: no resolved source for index " + currentIndex);
            return false;
        }
        final PlayQueueItem playQueueItem = playQueue.getItem();
        if (playQueueItem == null) {
            logger.debug("isPlaybackReady: queue item is null");
            return false;
        }
        logger.debug("isPlaybackReady: sourceType=" + resolved.getClass().getSimpleName()
                + " index=" + currentIndex + " ready=true");
        return true;
    }

    private void maybeBlock() {
        if (isBlocked) return;
        logger.debug("maybeBlock() called");
        playbackListener.onPlaybackBlock();
        resetSources();
        isBlocked = true;
    }

    private boolean maybeUnblock() {
        if (isBlocked) {
            isBlocked = false;
            logger.debug("maybeUnblock() called");
            playbackListener.onPlaybackUnblock(playlist.getParentMediaSource());
            return true;
        }
        return false;
    }

    // ─── Synchronization ─────────────────────────────────────────────────

    private void maybeSync(final boolean wasBlocked) {
        final PlayQueueItem currentItem = playQueue.getItem();
        if (isBlocked || currentItem == null) return;
        playbackListener.onPlaybackSynchronize(currentItem, wasBlocked);
    }

    private synchronized void maybeSynchronizePlayer() {
        final boolean queueReady = isPlayQueueReady();
        final boolean playbackReady = isPlaybackReady();
        logger.debug("maybeSynchronizePlayer: queueReady=" + queueReady
                + " playbackReady=" + playbackReady
                + " isBlocked=" + isBlocked);
        if (queueReady && playbackReady) {
            final boolean wasBlocked = maybeUnblock();
            maybeSync(wasBlocked);
        }
    }

    // ─── Loading (replaces RxJava debounce + interval) ───────────────────

    private void loadDebounced() {
        mainHandler.removeCallbacks(debouncedLoadRunnable);
        mainHandler.postDelayed(debouncedLoadRunnable, LOAD_DEBOUNCE_MILLIS);
    }

    private void loadImmediate() {
        if (disposed) return;
        logger.debug("loadImmediate() called, queue.size=" + playQueue.size()
                + " queue.index=" + playQueue.getIndex()
                + " playlist.size=" + playlist.size()
                + " disposed=" + disposed);
        final ItemsToLoad itemsToLoad = getItemsToLoad(playQueue);
        if (itemsToLoad == null) {
            logger.debug("loadImmediate() itemsToLoad is null, returning");
            return;
        }

        maybeClearLoaders();

        logger.debug("loadImmediate() loading center: " + itemsToLoad.center.getTitle()
                + ", neighbors: " + itemsToLoad.neighbors.size());
        maybeLoadItem(itemsToLoad.center);
        for (final PlayQueueItem item : itemsToLoad.neighbors) {
            maybeLoadItem(item);
        }
    }

    private void maybeLoadNeighbors() {
        if (disposed) return;
        final ItemsToLoad itemsToLoad = getItemsToLoad(playQueue);
        if (itemsToLoad == null) return;
        for (final PlayQueueItem item : itemsToLoad.neighbors) {
            maybeLoadItem(item);
        }
    }

    private void maybeLoadItem(@NonNull final PlayQueueItem item) {
        final int itemIndex = playQueue.indexOf(item);
        if (itemIndex >= playlist.size()) {
            logger.debug("maybeLoadItem() index " + itemIndex
                    + " >= playlist.size " + playlist.size() + ", skipping: " + item.getTitle());
            return;
        }

        final boolean alreadyLoading = loadingItems.contains(item);
        final boolean correctionNeeded = isCorrectionNeeded(item);
        logger.debug("maybeLoadItem() item=" + item.getTitle()
                + " index=" + itemIndex
                + " alreadyLoading=" + alreadyLoading
                + " correctionNeeded=" + correctionNeeded);

        if (!alreadyLoading && correctionNeeded) {
            logger.debug("Loading item: " + item.getTitle());
            loadingItems.add(item);
            loadingIndices.put(item, itemIndex);

            final Thread loaderThread = new Thread(() -> {
                logger.debug("Loader thread started for: " + item.getTitle()
                        + " url=" + item.getUrl() + " serviceId=" + item.getServiceId());
                item.fetchStreamInfo(new PlayQueueItem.StreamCallback() {
                    @Override
                    public void onSuccess(StreamInfo info) {
                        logger.debug("StreamInfo loaded for: " + item.getTitle()
                                + " name=" + info.getName());
                        if (disposed) return;
                        final com.google.android.exoplayer2.source.MediaSource resolved =
                                playbackListener.sourceOf(item, info);
                        mainHandler.post(() -> {
                            if (disposed) return;
                            final ManagedMediaSource source;
                            if (resolved != null) {
                                final MediaItemTag tag = MediaItemTag.from(resolved.getMediaItem());
                                if (tag != null) {
                                    final long expiration = System.currentTimeMillis()
                                            + getCacheExpirationMillis(info.getServiceId());
                                    source = new LoadedMediaSource(resolved, tag, item, expiration);
                                } else {
                                    source = FailedMediaSource.of(item,
                                            new MediaSourceResolutionException(
                                                    "Tag is null for resolved source"));
                                }
                            } else {
                                source = FailedMediaSource.of(item,
                                        new MediaSourceResolutionException(
                                                "Unable to resolve source from stream info. "
                                                        + "URL: " + item.getUrl()));
                            }
                            onMediaSourceReceived(item, source);
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        logger.error("StreamInfo load failed for: " + item.getTitle()
                                + " error=" + error.getMessage(), error);
                        if (disposed) return;
                        mainHandler.post(() -> {
                            final ManagedMediaSource source;
                            if (error instanceof ExtractionException) {
                                source = FailedMediaSource.of(item,
                                        new StreamInfoLoadException(error));
                            } else {
                                source = FailedMediaSource.of(item,
                                        new Exception(error), RETRY_WAIT_MILLIS);
                            }
                            onMediaSourceReceived(item, source);
                        });
                    }
                });
            }, "MediaSourceLoader-" + item.getTitle());
            loaderThreads.add(loaderThread);
            loaderThread.start();
        }
    }

    private void onMediaSourceReceived(@NonNull final PlayQueueItem item,
                                       @NonNull final ManagedMediaSource mediaSource) {
        loadingItems.remove(item);
        final Integer storedIndex = loadingIndices.remove(item);
        final int itemIndex = storedIndex != null ? storedIndex : playQueue.indexOf(item);
        resolvedSources.put(itemIndex, mediaSource);
        logger.debug("onMediaSourceReceived: item=" + item.getTitle()
                + " sourceType=" + mediaSource.getClass().getSimpleName()
                + " itemIndex=" + itemIndex
                + " correctionNeeded=" + isCorrectionNeeded(item));
        if (isCorrectionNeeded(item)) {
            logger.debug("Updating index " + itemIndex + " with " + item.getTitle());
            playlist.update(itemIndex, mediaSource, mainHandler, this::maybeSynchronizePlayer);
        }
    }

    private boolean isCorrectionNeeded(@NonNull final PlayQueueItem item) {
        final int index = playQueue.indexOf(item);
        final ManagedMediaSource mediaSource = playlist.get(index);
        if (mediaSource == null) {
            final ManagedMediaSource resolved = resolvedSources.get(index);
            if (resolved != null) {
                logger.debug("isCorrectionNeeded: restoring from cache at index " + index);
                playlist.update(index, resolved, mainHandler, this::maybeSynchronizePlayer);
                return false;
            }
            logger.debug("isCorrectionNeeded: no source at index " + index + ", correction IS needed");
            return true;
        }
        final boolean needed = mediaSource.shouldBeReplacedWith(item, index != playQueue.getIndex());
        logger.debug("isCorrectionNeeded: index=" + index
                + " sourceType=" + mediaSource.getClass().getSimpleName()
                + " needed=" + needed);
        return needed;
    }

    private void maybeRenewCurrentIndex() {
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem();
        final ManagedMediaSource currentSource = playlist.get(currentIndex);
        if (currentItem == null || currentSource == null) return;

        if (!currentSource.shouldBeReplacedWith(currentItem, true)) {
            maybeSynchronizePlayer();
            return;
        }

        logger.debug("Reloading currently playing index " + currentIndex);
        playlist.invalidate(currentIndex, mainHandler, this::loadImmediate);
    }

    private void maybeClearLoaders() {
        if (!loadingItems.contains(playQueue.getItem())
                && loaderThreads.size() > MAXIMUM_LOADER_SIZE) {
            cancelAllLoaders();
        }
    }

    private void cancelAllLoaders() {
        for (final Thread t : loaderThreads) {
            t.interrupt();
        }
        loaderThreads.clear();
        loadingItems.clear();
        loadingIndices.clear();
    }

    // ─── Playlist helpers ────────────────────────────────────────────────

    private void resetSources() {
        // Clear the playlist by removing all sources and re-expanding with placeholders.
        // We can't replace the playlist reference since the player already holds
        // getParentMediaSource(). Instead, clear it and re-populate.
        while (playlist.size() > 0) {
            playlist.remove(0);
        }
        populateSources();
    }

    private void populateSources() {
        while (playlist.size() < playQueue.size()) {
            playlist.expand();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    @Nullable
    private static ItemsToLoad getItemsToLoad(@NonNull final PlayQueue playQueue) {
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) return null;

        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightLimit = currentIndex + WINDOW_SIZE + 1;

        final List<PlayQueueItem> allStreams = playQueue.getStreams();
        final int rightBound = Math.min(allStreams.size(), rightLimit);

        final Set<PlayQueueItem> neighbors = new HashSet<>(
                allStreams.subList(leftBound, rightBound));

        // Round-robin for small queues
        final int excess = rightLimit - allStreams.size();
        if (excess > 0) {
            neighbors.addAll(allStreams.subList(0, Math.min(allStreams.size(), excess)));
        }
        neighbors.remove(currentItem);

        return new ItemsToLoad(currentItem, neighbors);
    }

    private static long getCacheExpirationMillis(final int serviceId) {
        // YouTube service ID is 0
        if (serviceId == 0) {
            return CACHE_EXPIRATION_YOUTUBE_MILLIS;
        }
        return CACHE_EXPIRATION_DEFAULT_MILLIS;
    }

    private static class ItemsToLoad {
        @NonNull private final PlayQueueItem center;
        @NonNull private final Set<PlayQueueItem> neighbors;

        ItemsToLoad(@NonNull final PlayQueueItem center,
                    @NonNull final Set<PlayQueueItem> neighbors) {
            this.center = center;
            this.neighbors = neighbors;
        }
    }
}
