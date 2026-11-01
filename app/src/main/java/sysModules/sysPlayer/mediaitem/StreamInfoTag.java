package sysModules.sysPlayer.mediaitem;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Collections;
import java.util.List;

/**
 * A {@link MediaItemTag} for resolved streams that are ready for playback.
 * Carries the full {@link StreamInfo} with video quality and audio track
 * selection data. Used by {@link sysModules.sysPlayer.mediasource.LoadedMediaSource}
 * to attach stream metadata to the media item for retrieval during playback.
 */
public final class StreamInfoTag {

    @NonNull private final StreamInfo streamInfo;
    @Nullable private final List<VideoStream> sortedVideoStreams;
    private final int selectedVideoIndex;
    @Nullable private final List<AudioStream> sortedAudioStreams;
    private final int selectedAudioIndex;
    @Nullable private final Object extras;

    private StreamInfoTag(@NonNull final StreamInfo streamInfo,
                          @Nullable final List<VideoStream> sortedVideoStreams,
                          final int selectedVideoIndex,
                          @Nullable final List<AudioStream> sortedAudioStreams,
                          final int selectedAudioIndex,
                          @Nullable final Object extras) {
        this.streamInfo = streamInfo;
        this.sortedVideoStreams = sortedVideoStreams;
        this.selectedVideoIndex = selectedVideoIndex;
        this.sortedAudioStreams = sortedAudioStreams;
        this.selectedAudioIndex = selectedAudioIndex;
        this.extras = extras;
    }

    @NonNull
    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo,
                                   @NonNull final List<VideoStream> sortedVideoStreams,
                                   final int selectedVideoIndex,
                                   @NonNull final List<AudioStream> sortedAudioStreams,
                                   final int selectedAudioIndex) {
        return new StreamInfoTag(streamInfo, sortedVideoStreams, selectedVideoIndex,
                sortedAudioStreams, selectedAudioIndex, null);
    }

    @NonNull
    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo,
                                   @NonNull final List<AudioStream> sortedAudioStreams,
                                   final int selectedAudioIndex) {
        return new StreamInfoTag(streamInfo, null, -1,
                sortedAudioStreams, selectedAudioIndex, null);
    }

    @NonNull
    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo) {
        return new StreamInfoTag(streamInfo, null, -1, null, -1, null);
    }

    // ─── Static extraction ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T from(@NonNull final MediaItem mediaItem) {
        final MediaItem.LocalConfiguration config = mediaItem.localConfiguration;
        if (config == null) return null;
        try {
            return (T) config.tag;
        } catch (final ClassCastException e) {
            return null;
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────

    @NonNull
    public StreamInfo getStreamInfo() {
        return streamInfo;
    }

    @Nullable
    public List<VideoStream> getSortedVideoStreams() {
        return sortedVideoStreams;
    }

    public int getSelectedVideoIndex() {
        return selectedVideoIndex;
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        if (sortedVideoStreams == null || selectedVideoIndex < 0
                || selectedVideoIndex >= sortedVideoStreams.size()) {
            return null;
        }
        return sortedVideoStreams.get(selectedVideoIndex);
    }

    @Nullable
    public List<AudioStream> getSortedAudioStreams() {
        return sortedAudioStreams;
    }

    public int getSelectedAudioIndex() {
        return selectedAudioIndex;
    }

    @Nullable
    public AudioStream getSelectedAudioStream() {
        if (sortedAudioStreams == null || selectedAudioIndex < 0
                || selectedAudioIndex >= sortedAudioStreams.size()) {
            return null;
        }
        return sortedAudioStreams.get(selectedAudioIndex);
    }

    public int getServiceId() {
        return streamInfo.getServiceId();
    }

    public String getTitle() {
        return streamInfo.getName();
    }

    public String getUploaderName() {
        return streamInfo.getUploaderName();
    }

    public long getDurationSeconds() {
        return streamInfo.getDuration();
    }

    public String getStreamUrl() {
        return streamInfo.getUrl();
    }

    public String getThumbnailUrl() {
        return streamInfo.getThumbnails() != null && !streamInfo.getThumbnails().isEmpty()
                ? streamInfo.getThumbnails().get(0).getUrl() : "";
    }

    public String getUploaderUrl() {
        return streamInfo.getUploaderUrl();
    }

    @NonNull
    public StreamType getStreamType() {
        return streamInfo.getStreamType();
    }

    @NonNull
    public List<Exception> getErrors() {
        return Collections.emptyList();
    }

    // ─── Tag propagation ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getExtras(@NonNull final Class<T> type) {
        return extras != null ? type.cast(extras) : null;
    }

    @NonNull
    public StreamInfoTag withExtras(@NonNull final Object extra) {
        return new StreamInfoTag(streamInfo, sortedVideoStreams, selectedVideoIndex,
                sortedAudioStreams, selectedAudioIndex, extra);
    }

    @NonNull
    public MediaItem asMediaItem() {
        return new MediaItem.Builder()
                .setMediaId("StreamInfoTag@" + streamInfo.getServiceId()
                        + "/" + streamInfo.getUrl())
                .setUri(Uri.parse("tag://streaminfo"))
                .setTag(this)
                .build();
    }
}
