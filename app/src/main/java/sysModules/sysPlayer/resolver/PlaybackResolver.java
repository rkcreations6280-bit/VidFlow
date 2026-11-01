package sysModules.sysPlayer.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import sysModules.sysPlayer.datasource.YoutubeDashLiveManifestParser;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import coreUtils.library.process.LoggerUtils;

public final class PlaybackResolver {
    private static final LoggerUtils logger = LoggerUtils.from(PlaybackResolver.class);

    /**
     * Target offset for live streams. NewPipe uses 10 seconds to stay close to the
     * real-time edge while allowing enough buffer to avoid rebuffering.
     */
    private static final long LIVE_STREAM_EDGE_GAP_MILLIS = 10_000L;

    /**
     * Coefficient for HLS live playlist stuck detection. NewPipe uses 15 (vs default 5)
     * to give low-latency live streams more tolerance before declaring stuck.
     */
    private static final double HLS_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15;

    private PlaybackResolver() {}

    // ─── Live stream ─────────────────────────────────────────────────────

    @Nullable
    public static MediaSource maybeBuildLiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final StreamInfo info) {
        if (!isLiveStream(info.getStreamType())) {
            return null;
        }

        try {
            if (!info.getDashMpdUrl().isEmpty()) {
                // Use cacheless factory for live streams (no disk caching)
                final DataSource.Factory liveFactory = dataSource.getCachelessDataSourceFactory();
                final DashMediaSource.Factory dashFactory = new DashMediaSource.Factory(liveFactory);

                // Only apply YouTube live DASH parser for YouTube streams
                if (info.getService() == ServiceList.YouTube) {
                    dashFactory.setManifestParser(new YoutubeDashLiveManifestParser());
                }

                return dashFactory.createMediaSource(new MediaItem.Builder()
                        .setUri(Uri.parse(info.getDashMpdUrl()))
                        .setLiveConfiguration(
                                new MediaItem.LiveConfiguration.Builder()
                                        .setTargetOffsetMs(LIVE_STREAM_EDGE_GAP_MILLIS)
                                        .build())
                        .build());
            }
            if (!info.getHlsUrl().isEmpty()) {
                final DataSource.Factory liveFactory = dataSource.getCachelessDataSourceFactory();
                return new HlsMediaSource.Factory(liveFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(new MediaItem.Builder()
                                .setUri(Uri.parse(info.getHlsUrl()))
                                .setLiveConfiguration(
                                        new MediaItem.LiveConfiguration.Builder()
                                                .setTargetOffsetMs(LIVE_STREAM_EDGE_GAP_MILLIS)
                                                .build())
                                .build());
            }
        } catch (final Exception e) {
            logger.error("Error building live media source: " + e.getMessage());
        }
        return null;
    }

    private static boolean isLiveStream(@NonNull final StreamType type) {
        return type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;
    }

    // ─── Cache key ───────────────────────────────────────────────────────

    @NonNull
    public static String cacheKeyOf(@NonNull final StreamInfo info,
                                     @NonNull final Stream stream) {
        final StringBuilder key = new StringBuilder();
        key.append(info.getServiceId()).append(" ");
        key.append(info.getId()).append(" ");
        key.append(stream.getId()).append(" ");

        final MediaFormat format = stream.getFormat();
        if (format != null) {
            key.append(format.getName()).append(" ");
        }

        if (stream instanceof VideoStream) {
            final VideoStream vs = (VideoStream) stream;
            final String resolution = vs.getResolution();
            if (!"unknown".equals(resolution)) {
                key.append(resolution);
            }
            key.append(" ").append(vs.isVideoOnly());
        } else if (stream instanceof AudioStream) {
            final AudioStream as = (AudioStream) stream;
            if (as.getAverageBitrate() != -1) {
                key.append(as.getAverageBitrate());
            }
            if (as.getAudioTrackId() != null) {
                key.append(" ").append(as.getAudioTrackId());
            }
            if (as.getAudioLocale() != null) {
                key.append(" ").append(as.getAudioLocale().getISO3Language());
            }
        }

        // Fallback: hash the content URL to avoid cache collisions when
        // resolution, bitrate, and format are all unknown
        final String content = resolveContent(stream);
        if (content != null && key.toString().endsWith(" ")) {
            key.append(sha1Hex(content));
        }

        return key.toString();
    }

    @NonNull
    private static String sha1Hex(@NonNull final String input) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-1");
            final byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(40);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ─── MediaSource building ────────────────────────────────────────────

    @Nullable
    public static MediaSource buildMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final Stream stream,
            @NonNull final StreamInfo streamInfo,
            @NonNull final String cacheKey,
            @Nullable final Object tag) {
        if (streamInfo.getService() == ServiceList.YouTube) {
            final MediaSource youtubeSource = buildYoutubeMediaSource(
                    dataSource, stream, streamInfo, cacheKey, tag);
            if (youtubeSource != null) {
                return youtubeSource;
            }
        }

        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        final String content = resolveContent(stream);
        if (content == null) return null;

        final MediaItem.Builder itemBuilder = new MediaItem.Builder()
                .setUri(Uri.parse(content))
                .setCustomCacheKey(cacheKey);
        if (tag != null) {
            itemBuilder.setTag(tag);
        }
        final MediaItem mediaItem = itemBuilder.build();

        switch (deliveryMethod) {
            case DASH:
                return buildDashSource(dataSource.getCacheDataSourceFactory(),
                        stream, cacheKey, tag);
            case HLS:
                return new HlsMediaSource.Factory(dataSource.getCacheDataSourceFactory())
                        .createMediaSource(mediaItem);
            case SS:
                return new SsMediaSource.Factory(dataSource.getCacheDataSourceFactory())
                        .createMediaSource(mediaItem);
            case PROGRESSIVE_HTTP:
            default:
                return new ProgressiveMediaSource.Factory(dataSource.getCacheDataSourceFactory())
                        .createMediaSource(mediaItem);
        }
    }

    @Nullable
    private static String resolveContent(@NonNull final Stream stream) {
        if (stream.isUrl()) {
            return stream.getContent();
        }
        return stream.getUrl();
    }

    // ─── YouTube-specific ────────────────────────────────────────────────

    @Nullable
    private static MediaSource buildYoutubeMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final Stream stream,
            @NonNull final StreamInfo streamInfo,
            @NonNull final String cacheKey,
            @Nullable final Object tag) {
        if (streamInfo.getStreamType() == StreamType.POST_LIVE_STREAM) {
            try {
                final ItagItem itag = stream.getItagItem();
                if (itag == null) return null;
                final String manifestString = YoutubePostLiveStreamDvrDashManifestCreator
                        .fromPostLiveStreamDvrStreamingUrl(stream.getContent(),
                                itag, itag.getTargetDurationSec(),
                                streamInfo.getDuration());
                // YouTube DASH: range=true, rn=true
                return buildDashFromManifest(
                        dataSource.getYouTubeDashDataSourceFactory(),
                        manifestString, stream, cacheKey, tag);
            } catch (final CreationException e) {
                logger.error("YouTube post-live DVR manifest generation failed: "
                        + e.getMessage());
                return null;
            }
        }

        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();

        if (deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) {
            final boolean isVideoOnly = stream instanceof VideoStream
                    && ((VideoStream) stream).isVideoOnly();
            final boolean isAudio = stream instanceof AudioStream;

            if (isVideoOnly || isAudio) {
                try {
                    final ItagItem itag = stream.getItagItem();
                    if (itag == null) return null;

                    final String manifestString = YoutubeProgressiveDashManifestCreator
                            .fromProgressiveStreamingUrl(
                                    stream.getContent(), itag, streamInfo.getDuration());
                    // YouTube DASH: range=true, rn=true
                    return buildDashFromManifest(
                            dataSource.getYouTubeDashDataSourceFactory(),
                            manifestString, stream, cacheKey, tag);
                } catch (final CreationException e) {
                    logger.warning("YouTube DASH manifest generation failed, "
                            + "falling back to progressive: " + e.getMessage());
                    // fall through to progressive fallback
                }
            }
            // YouTube legacy progressive: range=false, rn=true
            return buildProgressiveSource(
                    dataSource.getYouTubeProgressiveDataSourceFactory(),
                    stream, cacheKey, tag);
        }

        if (deliveryMethod == DeliveryMethod.DASH) {
            try {
                final ItagItem itag = stream.getItagItem();
                if (itag == null) return null;

                final String manifestString = YoutubeOtfDashManifestCreator
                        .fromOtfStreamingUrl(
                                stream.getContent(), itag, streamInfo.getDuration());
                // YouTube DASH: range=true, rn=true
                return buildDashFromManifest(
                        dataSource.getYouTubeDashDataSourceFactory(),
                        manifestString, stream, cacheKey, tag);
            } catch (final CreationException e) {
                logger.error("YouTube OTF DASH manifest generation failed: "
                        + e.getMessage());
                return null;
            }
        }

        if (deliveryMethod == DeliveryMethod.HLS) {
            final String content = resolveContent(stream);
            if (content == null) return null;
            final MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(content))
                    .setCustomCacheKey(cacheKey);
            if (tag != null) builder.setTag(tag);
            // YouTube HLS: range=false, rn=false
            return new HlsMediaSource.Factory(dataSource.getYouTubeHlsDataSourceFactory())
                    .createMediaSource(builder.build());
        }

        return null;
    }

    @Nullable
    private static MediaSource buildDashFromManifest(
            @NonNull final DataSource.Factory dataSourceFactory,
            @NonNull final String manifestString,
            @NonNull final Stream stream,
            @NonNull final String cacheKey,
            @Nullable final Object tag) {
        try {
            final DashManifest dashManifest = new DashManifestParser().parse(
                    Uri.parse(stream.getContent()),
                    new ByteArrayInputStream(
                            manifestString.getBytes(StandardCharsets.UTF_8)));
            final MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(stream.getContent()))
                    .setCustomCacheKey(cacheKey);
            if (tag != null) builder.setTag(tag);
            return new DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(dashManifest, builder.build());
        } catch (final IOException e) {
            logger.error("Failed to parse generated DASH manifest: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static MediaSource buildDashSource(
            @NonNull final DataSource.Factory dataSourceFactory,
            @NonNull final Stream stream,
            @NonNull final String cacheKey,
            @Nullable final Object tag) {
        if (stream.isUrl()) {
            final String content = stream.getContent();
            if (content == null) return null;
            final MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(content))
                    .setCustomCacheKey(cacheKey);
            if (tag != null) builder.setTag(tag);
            return new DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(builder.build());
        }

        // Non-URI DASH content: parse manifest string
        try {
            final String manifestUrl = stream.getManifestUrl();
            final String contentStr = stream.getContent();
            if (manifestUrl == null || contentStr == null) return null;
            final DashManifest dashManifest = new DashManifestParser().parse(
                    Uri.parse(manifestUrl),
                    new ByteArrayInputStream(
                            contentStr.getBytes(StandardCharsets.UTF_8)));
            final MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(manifestUrl))
                    .setCustomCacheKey(cacheKey);
            if (tag != null) builder.setTag(tag);
            return new DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(dashManifest, builder.build());
        } catch (final IOException e) {
            logger.error("Failed to parse DASH manifest: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static MediaSource buildProgressiveSource(
            @NonNull final DataSource.Factory dataSourceFactory,
            @NonNull final Stream stream,
            @NonNull final String cacheKey,
            @Nullable final Object tag) {
        final String content = resolveContent(stream);
        if (content == null) return null;
        final MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.parse(content))
                .setCustomCacheKey(cacheKey);
        if (tag != null) builder.setTag(tag);
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(builder.build());
    }

    // ─── Exception ───────────────────────────────────────────────────────

    public static final class ResolverException extends Exception {
        public ResolverException(@NonNull final String message) {
            super(message);
        }

        public ResolverException(@NonNull final String message,
                                  @NonNull final Throwable cause) {
            super(message, cause);
        }
    }
}
