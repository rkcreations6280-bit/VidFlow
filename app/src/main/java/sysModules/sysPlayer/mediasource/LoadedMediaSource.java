package sysModules.sysPlayer.mediasource;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.WrappingMediaSource;

import sysModules.sysPlayer.mediaitem.MediaItemTag;
import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * A {@link WrappingMediaSource} that wraps an actual media source with expiration
 * and queue-item tracking. Used by {@link sysModules.sysPlayer.playback.MediaSourceManager}
 * to manage preloaded sources in the playlist timeline.
 */
public class LoadedMediaSource extends WrappingMediaSource implements ManagedMediaSource {
    private final PlayQueueItem stream;
    private final MediaItem mediaItem;
    private final long expireTimestamp;

    /**
     * @param source          the child media source with actual media
     * @param tag             metadata for the source
     * @param stream          the queue item associated with this source
     * @param expireTimestamp  epoch millis when this source may no longer be valid
     */
    public LoadedMediaSource(@NonNull final MediaSource source,
                             @NonNull final MediaItemTag tag,
                             @NonNull final PlayQueueItem stream,
                             final long expireTimestamp) {
        super(source);
        this.stream = stream;
        this.expireTimestamp = expireTimestamp;
        this.mediaItem = tag.withExtras(this).asMediaItem();
    }

    @NonNull
    public PlayQueueItem getStream() {
        return stream;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() >= expireTimestamp;
    }

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return !newIdentity.isSameItem(stream) || (isInterruptable && isExpired());
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem otherStream) {
        return otherStream.isSameItem(this.stream);
    }
}
