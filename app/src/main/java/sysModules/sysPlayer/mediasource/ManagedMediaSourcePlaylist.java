package sysModules.sysPlayer.mediasource;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.MediaSource;

/**
 * Manages a {@link ConcatenatingMediaSource} of {@link ManagedMediaSource} instances,
 * keeping the ExoPlayer playlist timeline in sync with the play queue.
 * <p>
 * Operations (expand, append, remove, move, update, invalidate) are synchronized
 * to ensure thread safety when called from both the main thread and loader threads.
 */
public class ManagedMediaSourcePlaylist {

    @NonNull
    private final ConcatenatingMediaSource internalSource;

    public ManagedMediaSourcePlaylist() {
        internalSource = new ConcatenatingMediaSource(
                /* isPlaylistAtomic= */ false,
                new ShuffleOrder.UnshuffledShuffleOrder(0));
    }

    // ─── Delegations ──────────────────────────────────────────────────────

    public int size() {
        return internalSource.getSize();
    }

    /**
     * Returns the {@link ManagedMediaSource} at the given index, or null if out of bounds.
     */
    @Nullable
    public ManagedMediaSource get(final int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        final MediaSource source = internalSource.getMediaSource(index);
        if (source instanceof ManagedMediaSource) {
            return (ManagedMediaSource) source;
        }
        return null;
    }

    @NonNull
    public ConcatenatingMediaSource getParentMediaSource() {
        return internalSource;
    }

    // ─── Playlist manipulation ────────────────────────────────────────────

    /**
     * Appends a {@link PlaceholderMediaSource} to match queue size growth.
     */
    public synchronized void expand() {
        append(PlaceholderMediaSource.COPY);
    }

    /**
     * Appends a source to the end of the playlist.
     */
    public synchronized void append(@NonNull final ManagedMediaSource source) {
        internalSource.addMediaSource(source);
    }

    /**
     * Removes the source at the given index. Ignored if out of bounds.
     */
    public synchronized void remove(final int index) {
        if (index < 0 || index >= internalSource.getSize()) {
            return;
        }
        internalSource.removeMediaSource(index);
    }

    /**
     * Moves a source from one index to another. Ignored if either index is out of bounds.
     */
    public synchronized void move(final int source, final int target) {
        if (source < 0 || target < 0
                || source >= internalSource.getSize()
                || target >= internalSource.getSize()) {
            return;
        }
        internalSource.moveMediaSource(source, target);
    }

    /**
     * Invalidates the source at the given index by replacing it with a placeholder.
     */
    public synchronized void invalidate(final int index,
                                        @Nullable final Handler handler,
                                        @Nullable final Runnable finalizingAction) {
        if (get(index) instanceof PlaceholderMediaSource) {
            return;
        }
        update(index, PlaceholderMediaSource.COPY, handler, finalizingAction);
    }

    /**
     * Updates the source at the given index with a new source. This performs an atomic
     * add-then-remove to avoid timeline gaps.
     */
    public synchronized void update(final int index,
                                    @NonNull final ManagedMediaSource source) {
        update(index, source, null, null);
    }

    /**
     * Updates the source at the given index. The {@code finalizingAction} runs on
     * {@code handler} after the timeline change is complete.
     */
    public synchronized void update(final int index,
                                    @NonNull final ManagedMediaSource source,
                                    @Nullable final Handler handler,
                                    @Nullable final Runnable finalizingAction) {
        if (index < 0 || index >= internalSource.getSize()) {
            return;
        }
        // Add new source at index+1, then remove old at index.
        // This ensures ExoPlayer processes them atomically on its thread.
        internalSource.addMediaSource(index + 1, source);
        internalSource.removeMediaSource(index, handler, finalizingAction);
    }
}
