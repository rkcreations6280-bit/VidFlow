package sysModules.sysPlayer.mediasource;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.MediaSource;

import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * A {@link MediaSource} that is managed by the {@link sysModules.sysPlayer.playback.MediaSourceManager}.
 * Implementations track which queue item they belong to and whether they need replacement
 * (due to expiration or identity change).
 */
public interface ManagedMediaSource extends MediaSource {
    /**
     * Determines whether this source should be replaced.
     *
     * @param newIdentity     the queue item this source should represent
     * @param isInterruptable whether the source is potentially being played right now
     * @return true if this source needs replacement
     */
    boolean shouldBeReplacedWith(@NonNull PlayQueueItem newIdentity, boolean isInterruptable);

    /**
     * Checks if this source encapsulates the given queue item.
     *
     * @param stream the queue item to check
     * @return true if this source is for the specified item
     */
    boolean isStreamEqual(@NonNull PlayQueueItem stream);
}
