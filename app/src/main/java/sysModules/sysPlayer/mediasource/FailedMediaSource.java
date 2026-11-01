package sysModules.sysPlayer.mediasource;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import sysModules.sysPlayer.mediaitem.ExceptionTag;
import sysModules.sysPlayer.queue.PlayQueueItem;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MediaSource} representing a stream that failed to resolve.
 * <p>
 * For known errors (extraction failures), plays 2 seconds of silence and carries
 * the error in the {@link MediaItem} metadata. For unknown errors (network issues),
 * propagates the exception via {@link #maybeThrowSourceInfoRefreshError()} so the
 * player can react in {@link com.google.android.exoplayer2.Player.Listener#onPlayerError}.
 */
public class FailedMediaSource extends BaseMediaSource implements ManagedMediaSource {
    private static final String TAG = "FailedMediaSource";

    /** Duration of silence played when a known extraction error occurs. */
    public static final long SILENCE_DURATION_US = TimeUnit.SECONDS.toMicros(2);

    private final PlayQueueItem playQueueItem;
    private final Exception error;
    private final long retryTimestamp;
    private final MediaItem mediaItem;
    private final MediaSource silenceSource;

    /**
     * @param playQueueItem  the queue item that failed
     * @param error          the exception causing the failure
     * @param retryTimestamp epoch millis when this source can be retried
     */
    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final Exception error,
                             final long retryTimestamp) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = retryTimestamp;
        this.mediaItem = ExceptionTag.of(playQueueItem, List.of(error))
                .withExtras(this)
                .asMediaItem();
        this.silenceSource = new SilenceMediaSource.Factory()
                .setDurationUs(SILENCE_DURATION_US)
                .createMediaSource();
    }

    /** Creates a failed source for a known extraction error (never retries). */
    @NonNull
    public static FailedMediaSource of(@NonNull final PlayQueueItem item,
                                       @NonNull final FailedMediaSourceException error) {
        return new FailedMediaSource(item, error, Long.MAX_VALUE);
    }

    /** Creates a failed source that can retry after {@code retryWaitMillis}. */
    @NonNull
    public static FailedMediaSource of(@NonNull final PlayQueueItem item,
                                       @NonNull final Exception error,
                                       final long retryWaitMillis) {
        return new FailedMediaSource(item, error,
                System.currentTimeMillis() + retryWaitMillis);
    }

    @NonNull
    public PlayQueueItem getStream() {
        return playQueueItem;
    }

    @NonNull
    public Exception getError() {
        return error;
    }

    private boolean canRetry() {
        return System.currentTimeMillis() >= retryTimestamp;
    }

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    /**
     * Prepares with a silent timeline for known errors, or does nothing for
     * unknown errors (which will be thrown in {@link #maybeThrowSourceInfoRefreshError}).
     */
    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        Log.e(TAG, "Loading failed source: ", error);
        if (error instanceof FailedMediaSourceException) {
            refreshSourceInfo(new SinglePeriodTimeline(
                    SILENCE_DURATION_US,
                    /* isSeekable= */ true,
                    /* isDynamic= */ false,
                    /* useLiveConfiguration= */ false,
                    /* manifest= */ null,
                    mediaItem));
        }
    }

    /**
     * For unknown errors (network), propagates the exception so ExoPlayer surfaces
     * it via {@code onPlayerError}. For known errors (extraction), does nothing
     * since the error is carried in the MediaItem metadata.
     */
    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (!(error instanceof FailedMediaSourceException)) {
            throw new IOException(error);
        }
    }

    @NonNull
    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id,
                                    final Allocator allocator,
                                    final long startPositionUs) {
        return silenceSource.createPeriod(id, allocator, startPositionUs);
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        /* Keep reusing the silent MediaPeriod */
    }

    @Override
    protected void releaseSourceInternal() {
        /* No cleanup needed */
    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return !newIdentity.isSameItem(playQueueItem) || canRetry();
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem stream) {
        return stream.isSameItem(playQueueItem);
    }

    // ─── Exception subtypes ───────────────────────────────────────────────

    public static class FailedMediaSourceException extends Exception {
        FailedMediaSourceException(final String message) {
            super(message);
        }

        FailedMediaSourceException(final Throwable cause) {
            super(cause);
        }
    }

    public static final class MediaSourceResolutionException
            extends FailedMediaSourceException {
        public MediaSourceResolutionException(final String message) {
            super(message);
        }
    }

    public static final class StreamInfoLoadException
            extends FailedMediaSourceException {
        public StreamInfoLoadException(final Throwable cause) {
            super(cause);
        }
    }
}
