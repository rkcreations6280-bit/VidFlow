package sysModules.sysPlayer.mediaitem;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;

import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * A {@link MediaItemTag} for streams that failed to load. Carries metadata from an
 * underlying {@link PlayQueueItem} and the list of errors
 * that caused the failure. Used by {@link sysModules.sysPlayer.mediasource.FailedMediaSource}
 * to attach error information to the media item during playback.
 */
public final class ExceptionTag {

    @NonNull private final PlayQueueItem item;
    @NonNull private final List<Exception> errors;
    @Nullable private final Object extras;

    private ExceptionTag(@NonNull final PlayQueueItem item,
                         @NonNull final List<Exception> errors,
                         @Nullable final Object extras) {
        this.item = item;
        this.errors = errors;
        this.extras = extras;
    }

    @NonNull
    public static ExceptionTag of(@NonNull final PlayQueueItem playQueueItem,
                                  @NonNull final List<Exception> errors) {
        return new ExceptionTag(playQueueItem, errors, null);
    }

    // ─── Getters ──────────────────────────────────────────────────────────

    @NonNull
    public PlayQueueItem getStream() {
        return item;
    }

    @NonNull
    public List<Exception> getErrors() {
        return errors;
    }

    public int getServiceId() {
        return item.getServiceId();
    }

    public String getTitle() {
        return item.getTitle();
    }

    public String getUploaderName() {
        return item.getUploader();
    }

    public long getDurationSeconds() {
        return item.getDuration();
    }

    public String getStreamUrl() {
        return item.getUrl();
    }

    public String getThumbnailUrl() {
        return "";
    }

    public String getUploaderUrl() {
        return item.getUploaderUrl() != null ? item.getUploaderUrl() : "";
    }

    @NonNull
    public StreamType getStreamType() {
        return item.getStreamType();
    }

    // ─── Tag propagation ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getExtras(@NonNull final Class<T> type) {
        return extras != null ? type.cast(extras) : null;
    }

    @NonNull
    public ExceptionTag withExtras(@NonNull final Object extra) {
        return new ExceptionTag(item, errors, extra);
    }

    @NonNull
    public MediaItem asMediaItem() {
        return new MediaItem.Builder()
                .setMediaId("ExceptionTag@" + item.getServiceId() + "/" + item.getUrl())
                .setUri(Uri.parse("tag://exception"))
                .setTag(this)
                .build();
    }
}
