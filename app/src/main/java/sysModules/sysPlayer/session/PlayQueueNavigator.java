package sysModules.sysPlayer.session;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;

import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.extractor.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.model.QueueEvent;
import sysModules.sysPlayer.queue.PlayQueue;
import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.queue.QueueListener;

/**
 * Queue navigator that bridges a media session with a play queue, enabling system UI
 * components (lock screen, Android Auto, Wear OS, Bluetooth devices) to control
 * queue navigation. It supports skipping to next/previous tracks and jumping to
 * specific queue items, while publishing a sliding window of the full queue to
 * the media session for performance and UI clarity.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Publishes a floating (sliding) window of the play queue to the media session.</li>
 * <li>Handles skip-to-next, skip-to-previous, and skip-to-queue-item commands.</li>
 * <li>Listens to queue changes and timeline updates to refresh the published window.</li>
 * <li>Reports the currently active queue item ID for "now playing" indication.</li>
 * </ul>
 *
 * <p>The {@code @SuppressWarnings("deprecation")} annotation silences warnings
 * related to deprecated media session APIs that are still required for backward
 * compatibility with older Android versions.
 *
 * @see MediaSessionConnector.QueueNavigator
 * @see QueueListener
 * @see #publishFloatingQueueWindow()
 * @see #getActiveQueueItemId(Player)
 */
@SuppressWarnings("deprecation") public final class PlayQueueNavigator
	implements MediaSessionConnector.QueueNavigator, QueueListener {
	
	private static final LoggerUtils logger = LoggerUtils.from(PlayQueueNavigator.class);
	private static final int MAX_QUEUE_SIZE = 10;
	
	private final MediaSessionCompat mediaSession;
	private final PlayQueue playQueue;
	private final MediaEngine engine;
	
	/**
	 * Constructs a PlayQueueNavigator that bridges a media session with a play queue
	 * and media engine. This navigator enables system UI components (lock screen,
	 * Android Auto, Wear OS, headphones) to control queue navigation including
	 * skipping to next/previous tracks and jumping to specific queue items.
	 *
	 * <p><strong>Initialization steps:</strong>
	 * <ul>
	 * <li>Stores references to the media session, play queue, and media engine.</li>
	 * <li>Registers this navigator as a listener to the play queue for change events.</li>
	 * <li>Publishes the initial floating queue window to the media session.</li>
	 * </ul>
	 *
	 * @param mediaSession The media session to which queue navigation commands
	 *                     and the queue itself will be published.
	 * @param playQueue    The play queue containing all media items.
	 * @param engine       The media engine that executes playback commands.
	 */
	public PlayQueueNavigator(@NonNull MediaSessionCompat mediaSession,
	                          @NonNull PlayQueue playQueue,
	                          @NonNull MediaEngine engine) {
		this.mediaSession = mediaSession;
		this.playQueue = playQueue;
		this.engine = engine;
		playQueue.addListener(this);
		publishFloatingQueueWindow();
	}
	
	/**
	 * Returns the set of queue navigation actions supported by this navigator.
	 * This method informs the media session connector which transport controls
	 * should be enabled and displayed in system UI components.
	 *
	 * @param player The current {@link Player} instance (maybe {@code null}).
	 * @return A bitmask of supported navigation actions.
	 * @see MediaSessionConnector.QueueNavigator#getSupportedQueueNavigatorActions(Player)
	 */
	@Override
	public long getSupportedQueueNavigatorActions(@Nullable Player player) {
		return ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_SKIP_TO_QUEUE_ITEM;
	}
	
	/**
	 * Called when the player's timeline changes (e.g., when the queue is updated,
	 * media items are added or removed, or the playlist structure is modified).
	 * This method republishes the floating queue window to ensure the media session's
	 * queue reflects the most current timeline state.
	 *
	 * <p>Changes that trigger this callback include dynamic playlist modifications,
	 * shuffling, or repeating mode changes that affect the queue order.
	 *
	 * @param player The current {@link Player} instance.
	 * @see #publishFloatingQueueWindow()
	 */
	@Override
	public void onTimelineChanged(@NonNull Player player) {
		publishFloatingQueueWindow();
	}
	
	/**
	 * Called when the currently playing media item index changes (e.g., when moving
	 * to the next or previous track, or when the user seeks to a specific queue item).
	 * This method republishes the floating queue window to recenter the sliding
	 * window around the new current position.
	 *
	 * <p>The updated queue ensures system UI controls display the correct context
	 * (previous/next items relative to the newly playing track).
	 *
	 * @param player The current {@link Player} instance.
	 * @see #publishFloatingQueueWindow()
	 */
	@Override
	public void onCurrentMediaItemIndexChanged(@NonNull Player player) {
		publishFloatingQueueWindow();
	}
	
	/**
	 * Returns the queue ID of the currently active (playing) media item. This method
	 * is called by the media session connector to determine which item in the queue
	 * should be marked as "now playing" in system UI components.
	 *
	 * <p>The returned ID corresponds to the current index in the play queue, which
	 * was used as the {@link MediaSessionCompat.QueueItem#getQueueId()} when building
	 * the queue items in {@link #publishFloatingQueueWindow()}.
	 *
	 * @param player The current {@link Player} instance (maybe {@code null}).
	 * @return The current play queue index as a long value.
	 */
	@Override
	public long getActiveQueueItemId(@Nullable Player player) {
		return playQueue.getIndex();
	}
	
	/**
	 * Handles the "skip to previous" transport control command. This method is
	 * invoked when the user taps the previous track button on system media controls
	 * (lock screen, headphones, Android Auto, etc.). It delegates directly to the
	 * media engine to play the previous item in the queue.
	 *
	 * @param player The current {@link Player} instance (unused in this implementation).
	 * @see MediaEngine#playPrevious()
	 */
	@Override
	public void onSkipToPrevious(@NonNull Player player) {
		engine.playPrevious();
	}
	
	/**
	 * Handles the "skip to queue item" transport control command. This method is
	 * invoked when the user selects a specific item from the media session's queue.
	 * The {@code id} parameter corresponds to the queue position index set during
	 * {@link #buildDescription(PlayQueueItem, int)} and stored in
	 * {@link MediaSessionCompat.QueueItem#getQueueId()}.
	 *
	 * <p>The index is cast from {@code long} to {@code int} and applied to the
	 * play queue, which triggers playback of the selected item.
	 *
	 * @param player The current {@link Player} instance (unused).
	 * @param id     The queue ID (position index) of the selected item.
	 * @see #publishFloatingQueueWindow()
	 * @see PlayQueue#setIndex(int)
	 */
	@Override
	public void onSkipToQueueItem(@NonNull Player player, long id) {
		playQueue.setIndex((int) id);
	}
	
	/**
	 * Handles the "skip to next" transport control command. This method is invoked
	 * when the user taps the next track button on system media controls. It delegates
	 * directly to the media engine to play the next item in the queue.
	 *
	 * @param player The current {@link Player} instance (unused).
	 * @see MediaEngine#playNext()
	 */
	@Override
	public void onSkipToNext(@NonNull Player player) {
		engine.playNext();
	}
	
	/**
	 * Handles custom media session commands that are not part of the standard
	 * transport controls. This implementation returns {@code false} for all commands,
	 * indicating that no custom commands are supported or handled by this connector.
	 *
	 * <p>Override this method in subclasses to support custom commands such as
	 * "thumbs up", "add to playlist", or "share". The default behavior rejects
	 * all custom commands.
	 *
	 * @param player  The current {@link Player} instance.
	 * @param command The custom command string identifier.
	 * @param extras  Optional extras bundle containing command arguments.
	 * @param cb      Optional result receiver for sending back results.
	 * @return {@code false} always, indicating the command was not handled.
	 */
	@Override
	public boolean onCommand(@NonNull Player player,
	                         @NonNull String command,
	                         @Nullable Bundle extras,
	                         @Nullable ResultReceiver cb) {
		return false;
	}
	
	/**
	 * Releases resources held by this connector and unregisters it from the
	 * play queue. This method should be called when the media session is
	 * being destroyed or the player is being shut down to prevent memory leaks.
	 *
	 * <p>Specifically, it removes this instance as a listener from the
	 * {@link PlayQueue} to stop receiving queue change events.
	 */
	public void dispose() {
		playQueue.removeListener(this);
	}
	
	/**
	 * Called when the play queue changes (items added, removed, moved, or reordered).
	 * This method updates the media session's queue by republishing the floating
	 * window centered around the currently playing item.
	 *
	 * <p>The updated queue reflects the new queue state while maintaining the
	 * sliding window approach defined in {@link #publishFloatingQueueWindow()}.
	 *
	 * @param event The {@link QueueEvent} describing the type of queue change.
	 * @see #publishFloatingQueueWindow()
	 */
	@Override
	public void onQueueChanged(@NonNull QueueEvent event) {
		publishFloatingQueueWindow();
	}
	
	/**
	 * Publishes a floating (sliding) window of the play queue to the media session.
	 * This method constructs a subset of the full queue centered around the currently
	 * playing item, limiting the total number of items to {@link #MAX_QUEUE_SIZE}
	 * for performance and UI clarity in system media controls.
	 *
	 * <p><strong>Window calculation:</strong>
	 * <ul>
	 * <li>If the queue is empty, clears the session queue and returns early.</li>
	 * <li>Window size = min(MAX_QUEUE_SIZE, total stream count).</li>
	 * <li>Half window = (windowSize - 1) / 2 (items before and after current).</li>
	 * <li>Start index is clamped to ensure the window fits within queue bounds.</li>
	 * </ul>
	 *
	 * <p>Example: With MAX_QUEUE_SIZE = 11, current index = 20, total = 100 →
	 * half = 5, start = max(0, min(15, 100-11)) = 15. Window indices 15-25.
	 *
	 * <p>Each queue item is converted to a {@link MediaSessionCompat.QueueItem} using
	 * {@link #buildDescription(PlayQueueItem, int)} with its position index.
	 */
	private void publishFloatingQueueWindow() {
		List<PlayQueueItem> streams = playQueue.getStreams();
		if (streams.isEmpty()) {
			mediaSession.setQueue(Collections.emptyList());
			return;
		}
		
		int currentIndex = playQueue.getIndex();
		int windowCount = streams.size();
		int queueSize = Math.min(MAX_QUEUE_SIZE, windowCount);
		
		int half = (queueSize - 1) / 2;
		int startIndex = Math.max(0,
			Math.min(currentIndex - half, windowCount - queueSize));
		
		List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
		for (int i = startIndex; i < startIndex + queueSize; i++) {
			PlayQueueItem item = streams.get(i);
			if (item != null) {
				queue.add(new MediaSessionCompat.QueueItem(
					buildDescription(item, i), i));
			}
		}
		mediaSession.setQueue(queue);
	}
	
	/**
	 * Builds a {@link MediaDescriptionCompat} object from a {@link PlayQueueItem} for use
	 * in media session metadata. This description is used by system UI components
	 * (lock screen, Android Auto, Wear OS) to display track information.
	 *
	 * <p><strong>Fields populated:</strong>
	 * <ul>
	 * <li>Media ID – Index as string for unique identification.</li>
	 * <li>Title – Track title from {@link PlayQueueItem#getTitle()}.</li>
	 * <li>Subtitle – Uploader/channel name from {@link PlayQueueItem#getUploader()}.</li>
	 * <li>Extras bundle – Contains title, artist, duration (ms), track number,
	 *     and total track count.</li>
	 * <li>Icon URI – First available thumbnail URL (if present).</li>
	 * </ul>
	 *
	 * <p>Duration is converted from seconds to milliseconds (× 1000L) to match
	 * {@link MediaMetadataCompat} expectations. Any exception during thumbnail URL
	 * parsing is caught and logged without crashing the build process.
	 *
	 * @param item  The {@link PlayQueueItem} containing track metadata.
	 * @param index The position of this item within the play queue (0-based).
	 * @return A fully constructed {@link MediaDescriptionCompat} instance.
	 */
	private MediaDescriptionCompat buildDescription(@NonNull PlayQueueItem item, int index) {
		MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
			.setMediaId(String.valueOf(index))
			.setTitle(item.getTitle())
			.setSubtitle(item.getUploader());
		
		Bundle extras = new Bundle();
		extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle());
		extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getUploader());
		extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getDuration() * 1000L);
		extras.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, index + 1L);
		extras.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, playQueue.size());
		builder.setExtras(extras);
		
		List<Image> thumbnails = item.getThumbnails();
		if (!thumbnails.isEmpty()) {
			try {
				String thumbUrl = thumbnails.get(0).getUrl();
				builder.setIconUri(Uri.parse(thumbUrl));
			} catch (Exception error) {
				logger.error("Error parsing thumbnail URL: ", error);
			}
		}
		
		return builder.build();
	}
}
