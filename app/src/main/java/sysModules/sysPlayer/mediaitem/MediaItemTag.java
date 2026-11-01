package sysModules.sysPlayer.mediaitem;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Collections;
import java.util.List;

public class MediaItemTag {

    private final int serviceId;
    private final int videoIndex;
    private final int audioIndex;
    @Nullable private final VideoStream selectedVideo;
    @Nullable private final AudioStream selectedAudio;
    @NonNull private final List<VideoStream> sortedVideoStreams;
    @NonNull private final List<AudioStream> sortedAudioStreams;
    @Nullable private final Object extras;

    public MediaItemTag(final int serviceId,
                        final int videoIndex,
                        final int audioIndex,
                        @Nullable final VideoStream selectedVideo,
                        @Nullable final AudioStream selectedAudio,
                        @NonNull final List<VideoStream> sortedVideoStreams,
                        @NonNull final List<AudioStream> sortedAudioStreams) {
        this(serviceId, videoIndex, audioIndex, selectedVideo, selectedAudio,
                sortedVideoStreams, sortedAudioStreams, null);
    }

    private MediaItemTag(final int serviceId,
                         final int videoIndex,
                         final int audioIndex,
                         @Nullable final VideoStream selectedVideo,
                         @Nullable final AudioStream selectedAudio,
                         @NonNull final List<VideoStream> sortedVideoStreams,
                         @NonNull final List<AudioStream> sortedAudioStreams,
                         @Nullable final Object extras) {
        this.serviceId = serviceId;
        this.videoIndex = videoIndex;
        this.audioIndex = audioIndex;
        this.selectedVideo = selectedVideo;
        this.selectedAudio = selectedAudio;
        this.sortedVideoStreams = sortedVideoStreams;
        this.sortedAudioStreams = sortedAudioStreams;
        this.extras = extras;
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

    // ─── Factory ──────────────────────────────────────────────────────────

    public static MediaItemTag of(final int serviceId,
                                  final int videoIndex,
                                  final int audioIndex,
                                  @Nullable final VideoStream selectedVideo,
                                  @Nullable final AudioStream selectedAudio,
                                  @NonNull final List<VideoStream> sortedVideoStreams,
                                  @NonNull final List<AudioStream> sortedAudioStreams) {
        return new MediaItemTag(serviceId, videoIndex, audioIndex, selectedVideo, selectedAudio,
                sortedVideoStreams, sortedAudioStreams);
    }

    // ─── Getters ──────────────────────────────────────────────────────────

    public int getServiceId() {
        return serviceId;
    }

    public int getVideoIndex() {
        return videoIndex;
    }

    public int getAudioIndex() {
        return audioIndex;
    }

    @Nullable
    public VideoStream getSelectedVideo() {
        return selectedVideo;
    }

    @Nullable
    public AudioStream getSelectedAudio() {
        return selectedAudio;
    }

    @NonNull
    public List<VideoStream> getSortedVideoStreams() {
        return sortedVideoStreams;
    }

    @NonNull
    public List<AudioStream> getSortedAudioStreams() {
        return sortedAudioStreams;
    }

    @Nullable
    public <T> T getExtras(@NonNull final Class<T> type) {
        return extras != null ? type.cast(extras) : null;
    }

    @NonNull
    public List<Exception> getErrors() {
        return Collections.emptyList();
    }

    public String getTitle() {
        final VideoStream v = selectedVideo;
        return v != null ? v.getUrl() : "";
    }

    public String getUploaderName() {
        return "";
    }

    public long getDurationSeconds() {
        return 0;
    }

    public String getStreamUrl() {
        final VideoStream v = selectedVideo;
        return v != null ? v.getUrl() : "";
    }

    public String getThumbnailUrl() {
        return "";
    }

    public String getUploaderUrl() {
        return "";
    }

    @NonNull
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    // ─── Tag propagation ──────────────────────────────────────────────────

    @NonNull
    public MediaItemTag withExtras(@NonNull final Object extra) {
        return new MediaItemTag(serviceId, videoIndex, audioIndex, selectedVideo, selectedAudio,
                sortedVideoStreams, sortedAudioStreams, extra);
    }

    @NonNull
    public MediaItem asMediaItem() {
        return new MediaItem.Builder()
                .setMediaId("MediaItemTag@" + serviceId)
                .setUri(Uri.parse("tag://mediaitem"))
                .setTag(this)
                .build();
    }
}
