package sysModules.sysPlayer.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.mediaitem.MediaItemTag;

@SuppressWarnings("ALL") public final class AudioPlaybackResolver {
    private static final LoggerUtils logger = LoggerUtils.from(AudioPlaybackResolver.class);

    private static final List<Integer> SUPPORTED_ITAG_IDS = List.of(
            17, 36, 18, 34, 35, 59, 78, 22, 37, 38, 43, 44, 45, 46, 171,
            172, 139, 140, 141, 249, 250, 251, 160, 133, 134, 135, 212,
            136, 298, 137, 299, 266, 278, 242, 243, 244, 245, 246, 247,
            248, 271, 272, 302, 303, 308, 313, 315
    );

    private final PlayerDataSource dataSource;

    public AudioPlaybackResolver(@NonNull PlayerDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Nullable
    public MediaSource resolve(@NonNull StreamInfo info) {
        final List<AudioStream> audioStreams = getFilteredAudioStreams(
                info.getAudioStreams(), info.getServiceId());
        final List<VideoStream> videoStreams = getFilteredVideoStreams(
                info.getVideoStreams(), info.getVideoOnlyStreams(), info.getServiceId());

        if (audioStreams.isEmpty() && videoStreams.isEmpty()) {
            logger.warning("No playable audio or video streams for " + info.getName());
            return null;
        }

        final AudioStream selectedAudio = audioStreams.isEmpty() ? null : audioStreams.get(0);
        final VideoStream videoFallback = selectedAudio != null ? null
                : (videoStreams.isEmpty() ? null : videoStreams.get(0));

        final Stream playStream = selectedAudio != null ? selectedAudio : videoFallback;
        if (playStream == null) return null;

        final String cacheKey = PlaybackResolver.cacheKeyOf(info, playStream);
        final MediaItemTag tag = new MediaItemTag(info.getServiceId(), -1,
                selectedAudio != null ? 0 : -1, null, selectedAudio,
                Collections.emptyList(), audioStreams);

        return PlaybackResolver.buildMediaSource(dataSource, playStream,
                info, cacheKey, tag);
    }

    @NonNull
    private static List<AudioStream> getFilteredAudioStreams(
            @Nullable List<AudioStream> streams, final int serviceId) {
        if (streams == null) return Collections.emptyList();
        final boolean isYoutube = serviceId == ServiceList.YouTube.getServiceId();
        final List<AudioStream> result = new ArrayList<>();
        for (final AudioStream as : streams) {
            if (as.getDeliveryMethod() == DeliveryMethod.TORRENT) continue;
            if (as.getDeliveryMethod() == DeliveryMethod.HLS
                    && as.getFormat() == MediaFormat.OPUS) continue;
            if (isYoutube && !isSupportedItag(as.getItagItem())) continue;
            if (resolveUrl(as) == null) continue;
            result.add(as);
        }
        return result;
    }

    @NonNull
    private static List<VideoStream> getFilteredVideoStreams(
            @Nullable List<VideoStream> videoStreams,
            @Nullable List<VideoStream> videoOnlyStreams,
            final int serviceId) {
        final boolean isYoutube = serviceId == ServiceList.YouTube.getServiceId();
        final List<VideoStream> result = new ArrayList<>();
        final List<List<VideoStream>> sources = new ArrayList<>();
        if (videoStreams != null) sources.add(videoStreams);
        if (videoOnlyStreams != null) sources.add(videoOnlyStreams);
        for (final List<VideoStream> list : sources) {
            for (final VideoStream vs : list) {
                if (vs.getDeliveryMethod() == DeliveryMethod.TORRENT) continue;
                if (isYoutube && !isSupportedItag(vs.getItagItem())) continue;
                if (resolveUrl(vs) == null) continue;
                result.add(vs);
            }
        }
        return result;
    }

    private static boolean isSupportedItag(@Nullable final ItagItem itag) {
        return itag == null || SUPPORTED_ITAG_IDS.contains(itag.id);
    }

    @Nullable
    private static String resolveUrl(@NonNull final Stream stream) {
        if (stream.isUrl()) return stream.getContent();
        return stream.getUrl();
    }
}
