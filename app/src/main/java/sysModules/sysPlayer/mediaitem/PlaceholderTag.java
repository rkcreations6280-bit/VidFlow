package sysModules.sysPlayer.mediaitem;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Collections;
import java.util.List;

/**
 * A placeholder {@link MediaItemTag} used for unresolved streams in the playlist.
 * All metadata methods return dummy values. The placeholder signals that the
 * corresponding queue item has not yet been fetched or resolved.
 */
public final class PlaceholderTag {

    public static final PlaceholderTag EMPTY = new PlaceholderTag(null);
    private static final String UNKNOWN = "Placeholder";

    @Nullable private final Object extras;

    private PlaceholderTag(@Nullable final Object extras) {
        this.extras = extras;
    }

    // ─── Getters ──────────────────────────────────────────────────────────

    @NonNull
    public List<Exception> getErrors() {
        return Collections.emptyList();
    }

    public int getServiceId() {
        return -1;
    }

    public String getTitle() {
        return UNKNOWN;
    }

    public String getUploaderName() {
        return UNKNOWN;
    }

    public long getDurationSeconds() {
        return 0;
    }

    public String getStreamUrl() {
        return UNKNOWN;
    }

    public String getThumbnailUrl() {
        return UNKNOWN;
    }

    public String getUploaderUrl() {
        return UNKNOWN;
    }

    @NonNull
    public StreamType getStreamType() {
        return StreamType.NONE;
    }

    // ─── Tag propagation ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getExtras(@NonNull final Class<T> type) {
        return extras != null ? type.cast(extras) : null;
    }

    @NonNull
    public PlaceholderTag withExtras(@NonNull final Object extra) {
        return new PlaceholderTag(extra);
    }

    @NonNull
    public MediaItem asMediaItem() {
        return new MediaItem.Builder()
                .setMediaId("PlaceholderTag")
                .setUri(Uri.parse("tag://placeholder"))
                .setTag(this)
                .build();
    }
}
