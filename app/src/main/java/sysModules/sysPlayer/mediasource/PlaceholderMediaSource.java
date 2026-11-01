package sysModules.sysPlayer.mediasource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.Arrays;

import sysModules.sysPlayer.mediaitem.PlaceholderTag;
import sysModules.sysPlayer.queue.PlayQueueItem;

public final class PlaceholderMediaSource extends BaseMediaSource
        implements ManagedMediaSource {

    public static final PlaceholderMediaSource COPY = new PlaceholderMediaSource();
    private static final MediaItem MEDIA_ITEM =
            PlaceholderTag.EMPTY.withExtras(COPY).asMediaItem();

    private PlaceholderMediaSource() { }

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return MEDIA_ITEM;
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        refreshSourceInfo(new SinglePeriodTimeline(
                C.TIME_UNSET,
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* manifest= */ null,
                MEDIA_ITEM));
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() { }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id,
                                    final Allocator allocator,
                                    final long startPositionUs) {
        return new SilentMediaPeriod();
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) { }

    @Override
    protected void releaseSourceInternal() { }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return true;
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem stream) {
        return false;
    }

    private static final class SilentMediaPeriod implements MediaPeriod {

        @Nullable private Callback callback;

        @Override
        public void prepare(@NonNull final Callback callback, final long positionUs) {
            this.callback = callback;
            callback.onPrepared(this);
        }

        @Override
        public void maybeThrowPrepareError() { }

        @NonNull
        @Override
        public TrackGroupArray getTrackGroups() {
            return TrackGroupArray.EMPTY;
        }

        @Override
        public long selectTracks(@Nullable final ExoTrackSelection[] selections,
                                 final boolean[] mayRetainStreamFlags,
                                 @Nullable final SampleStream[] streams,
                                 final boolean[] streamResetFlags,
                                 final long positionUs) {
            Arrays.fill(streams, null);
            return positionUs;
        }

        @Override
        public void discardBuffer(final long positionUs, final boolean toKeyframe) { }

        @Override
        public long readDiscontinuity() {
            return C.TIME_UNSET;
        }

        @Override
        public long seekToUs(final long positionUs) {
            return positionUs;
        }

        @Override
        public long getAdjustedSeekPositionUs(final long positionUs,
                                              @NonNull final SeekParameters seekParameters) {
            return positionUs;
        }

        public long getDurationUs() {
            return C.TIME_UNSET;
        }

        @Override
        public long getBufferedPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public long getNextLoadPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public boolean continueLoading(final long positionUs) {
            return false;
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public void reevaluateBuffer(final long positionUs) { }
    }
}
