package sysModules.sysPlayer.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * Callback interface for the {@link MediaSourceManager} to communicate with the
 * player about playback state, source resolution, and queue lifecycle.
 * <p>
 * Implementations (typically the playback service) handle blocking/unblocking the
 * player, synchronizing queue index, resolving streams to media sources, and
 * shutdown.
 */
public interface PlaybackListener {
    /**
     * Checks if the current stream is approaching the end of its playback duration.
     * Called periodically by the media source manager to trigger preloading of the
     * next source.
     *
     * @param timeToEndMillis time threshold: true if within this many ms of the end
     * @return true if the player is approaching the playback edge
     */
    boolean isApproachingPlaybackEdge(long timeToEndMillis);

    /**
     * Signals that the current source is not ready yet. The listener should stop
     * the player and mark the playback as blocked.
     */
    void onPlaybackBlock();

    /**
     * Signals that a new source is ready. The listener should set the source on
     * the player and prepare it for playback.
     *
     * @param mediaSource the ready source
     */
    void onPlaybackUnblock(MediaSource mediaSource);

    /**
     * Signals that the queue index may have changed and the player should seek
     * to the correct position.
     *
     * @param item       the item the player should be playing
     * @param wasBlocked true if the player was just unblocked
     */
    void onPlaybackSynchronize(@NonNull PlayQueueItem item, boolean wasBlocked);

    /**
     * Requests the listener to resolve a stream info into a media source.
     *
     * @param item the queue item being resolved
     * @param info the stream info fetched for the item
     * @return the resolved media source, or null if resolution failed
     */
    @Nullable
    MediaSource sourceOf(PlayQueueItem item, StreamInfo info);

    /**
     * Signals that the play queue is empty and complete. The listener should
     * shut down the playback service.
     */
    void onPlaybackShutdown();

    /**
     * Signals that the play queue was edited (items added, removed, or moved).
     * The listener should update notification buttons or other queue-dependent UI.
     */
    void onPlayQueueEdited();
}
