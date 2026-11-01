package sysModules.sysPlayer.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.mediaitem.MediaItemTag;

/**
 * Resolves {@link StreamInfo} objects into playable ExoPlayer {@link MediaSource}
 * instances. This class handles the complex logic of selecting appropriate video
 * and audio streams based on user preferences, device capabilities, and stream
 * availability. It supports live streams, DASH, HLS, and progressive download
 * formats with subtitle integration.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Selects optimal video resolution based on user preference or auto-selection.</li>
 * <li>Selects optimal audio track based on language and format preferences.</li>
 * <li>Filters unsupported or unplayable streams (torrent, unsupported itags).</li>
 * <li>Deduplicates streams by resolution/track ID, preferring higher bitrates.</li>
 * <li>Merges separate video and audio sources when necessary.</li>
 * <li>Adds subtitle sources to the final media source.</li>
 * </ul>
 *
 * <p>The class uses static ranking lists for video/audio formats and track types
 * to make consistent selection decisions. It also supports data saving mode,
 * descriptive audio preference, and original audio preference.
 *
 * @see #resolve(StreamInfo)
 * @see Config
 */
@SuppressWarnings("all") public final class VideoPlaybackResolver {
	private static final LoggerUtils logger = LoggerUtils.from(VideoPlaybackResolver.class);
	
	/**
	 * List of YouTube itag IDs that are supported by the ExoPlayer implementation.
	 * These itags represent specific video/audio format combinations that are known
	 * to be compatible across various devices. Unsupported itags are filtered out
	 * during stream selection to prevent playback failures.
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/YouTube#Quality_and_formats">
	 *     YouTube itag reference</a>
	 */
	private static final List<Integer> SUPPORTED_ITAG_IDS = List.of(
		17, 36, 18, 34, 35, 59, 78, 22, 37, 38, 43, 44, 45, 46, 171,
		172, 139, 140, 141, 249, 250, 251, 160, 133, 134, 135, 212,
		136, 298, 137, 299, 266, 278, 242, 243, 244, 245, 246, 247,
		248, 271, 272, 302, 303, 308, 313, 315
	);
	
	/**
	 * Ranking of video container formats from least preferred to most preferred.
	 * This ordering influences stream selection when multiple formats of the same
	 * resolution are available. MP4 is ranked highest due to wider hardware
	 * acceleration support and better compatibility.
	 */
	private static final List<MediaFormat> VIDEO_FORMAT_QUALITY_RANKING =
		List.of(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4);
	
	/**
	 * Ranking of audio formats for quality preference (higher bitrates prioritized).
	 * Used when {@link Config#limitDataUsage()} is false.
	 */
	private static final List<MediaFormat> AUDIO_FORMAT_QUALITY_RANKING =
		List.of(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A);
	
	/**
	 * Ranking of audio formats for efficiency preference (lower bitrates prioritized).
	 * Used when {@link Config#limitDataUsage()} is true to save bandwidth.
	 */
	private static final List<MediaFormat> AUDIO_FORMAT_EFFICIENCY_RANKING =
		List.of(MediaFormat.MP3, MediaFormat.M4A, MediaFormat.WEBMA);
	
	/**
	 * Default ranking of audio track types from most to least preferred.
	 * Original audio is prioritized, followed by dubbed, descriptive, then secondary.
	 */
	private static final List<AudioTrackType> AUDIO_TRACK_TYPE_RANKING =
		List.of(AudioTrackType.ORIGINAL, AudioTrackType.DUBBED,
			AudioTrackType.DESCRIPTIVE, AudioTrackType.SECONDARY);
	
	/**
	 * Alternative ranking of audio track types prioritizing descriptive audio.
	 * Used when {@link Config#preferDescriptiveAudio()} is true.
	 */
	private static final List<AudioTrackType> AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE =
		List.of(AudioTrackType.DESCRIPTIVE, AudioTrackType.DUBBED,
			AudioTrackType.ORIGINAL, AudioTrackType.SECONDARY);
	
	/**
	 * Set of resolutions considered "high resolution" (1440p and 2160p/4K).
	 * These may be filtered out when {@link Config#showHigherResolutions()} is false
	 * to save bandwidth or battery on devices with lower screen resolutions.
	 */
	private static final Set<String> HIGH_RESOLUTION_LIST =
		Set.of("1440p", "2160p");
	
	private static final String ENGLISH_LANGUAGE = "eng";
	
	private final PlayerDataSource dataSource;
	private final Config config;
	
	@Nullable private volatile String playbackQuality;
	@Nullable private volatile String audioTrack;
	@Nullable private volatile SourceType streamSourceType;
	
	/**
	 * Resolves {@link StreamInfo} objects into playable ExoPlayer {@link MediaSource}
	 * instances. This class handles the complex logic of selecting appropriate video
	 * and audio streams based on user preferences, device capabilities, and stream
	 * availability. It supports live streams, DASH, HLS, and progressive download
	 * formats with subtitle integration.
	 *
	 * <p><strong>Core responsibilities:</strong>
	 * <ul>
	 * <li>Selects optimal video resolution based on user preference or auto-selection.</li>
	 * <li>Selects optimal audio track based on language and format preferences.</li>
	 * <li>Filters unsupported or unplayable streams (torrent, unsupported itags).</li>
	 * <li>Deduplicates streams by resolution/track ID, preferring higher bitrates.</li>
	 * <li>Merges separate video and audio sources when necessary.</li>
	 * <li>Adds subtitle sources to the final media source.</li>
	 * </ul>
	 *
	 * @param dataSource Data source provider with YouTube-specific factory variants.
	 * @param config     Configuration object containing user preferences.
	 * @see #resolve(StreamInfo)
	 * @see Config
	 */
	public VideoPlaybackResolver(@NonNull PlayerDataSource dataSource,
	                             @NonNull Config config) {
		this.dataSource = dataSource;
		this.config = config;
	}
	
	/**
	 * Configuration interface for playback resolution preferences. Implementations
	 * provide user-specific settings that influence video and audio stream selection.
	 */
	public interface Config {
		@Nullable MediaFormat getDefaultVideoFormat();
		@Nullable MediaFormat getDefaultAudioFormat();
		boolean showHigherResolutions();
		@Nullable String getPreferredAudioLanguage();
		boolean limitDataUsage();
		boolean preferVideoOnly();
		boolean preferOriginalAudio();
		boolean preferDescriptiveAudio();
	}
	
	/**
	 * Defines the type of media source that has been resolved for playback.
	 * This enumeration helps the player understand the structure of the current
	 * media source, which may affect how playback controls and UI elements behave.
	 *
	 * <p><strong>Source types:</strong>
	 * <ul>
	 * <li>{@link #LIVE_STREAM} – A live HLS/DASH stream without seek capabilities.</li>
	 * <li>{@link #VIDEO_WITH_SEPARATED_AUDIO} – Video-only stream paired with a
	 *     separate audio stream (e.g., DASH with separate adaptation sets).</li>
	 * <li>{@link #VIDEO_WITH_AUDIO_OR_AUDIO_ONLY} – A single merged source containing
	 *     both video and audio, or an audio-only source.</li>
	 * </ul>
	 *
	 * @see PlaybackResolver#getStreamSourceType()
	 * @see PlaybackResolver#resolve(StreamInfo)
	 */
	public enum SourceType {
		LIVE_STREAM,
		VIDEO_WITH_SEPARATED_AUDIO,
		VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
	}
	
	/**
	 * Sets the preferred playback quality string for video resolution selection.
	 * This value overrides the default resolution selection logic. Valid values
	 * include strings like "1080p", "720p", "480p", or format-specific strings
	 * such as "1080p (H264)". Setting to {@code null} reverts to automatic
	 * selection based on the highest available resolution or configuration defaults.
	 *
	 * @param quality The desired playback quality string, or {@code null} for auto-selection.
	 * @see #getPlaybackQuality()
	 * @see PlaybackResolver#getOverrideResolutionIndex(List, String, MediaFormat)
	 */
	public void setPlaybackQuality(@Nullable String quality) {
		this.playbackQuality = quality;
	}
	
	/**
	 * Returns the currently selected playback quality string. This value represents
	 * the user's preferred video resolution (e.g., "1080p", "720p", "480p").
	 * If {@code null}, the default resolution selection logic will be used.
	 *
	 * @return The playback quality string, or {@code null} if not set.
	 * @see #setPlaybackQuality(String)
	 * @see PlaybackResolver#getOverrideResolutionIndex(List, String, MediaFormat)
	 */
	@Nullable
	public String getPlaybackQuality() {
		return playbackQuality;
	}
	
	/**
	 * Sets the preferred audio track identifier. This value is used to select a
	 * specific audio stream when multiple options are available (e.g., different
	 * languages or commentary tracks). The identifier format should match the
	 * audio track ID from the stream info.
	 *
	 * @param audioTrack The audio track identifier, or {@code null} to use the
	 *                   default selection logic based on language preferences.
	 * @see #getAudioTrack()
	 * @see PlaybackResolver#resolve(StreamInfo)
	 */
	public void setAudioTrack(@Nullable String audioTrack) {
		this.audioTrack = audioTrack;
	}
	
	/**
	 * Returns the currently selected audio track identifier. This value determines
	 * which audio stream is chosen during media source resolution. If {@code null},
	 * the default audio selection logic (based on preferred language and other
	 * preferences) will be used.
	 *
	 * @return The audio track identifier, or {@code null} if not set.
	 * @see #setAudioTrack(String)
	 */
	@Nullable
	public String getAudioTrack() {
		return audioTrack;
	}
	
	/**
	 * Returns the type of media source that was resolved during the last call to
	 * {@link #resolve(StreamInfo)}. This value indicates whether the source is a
	 * live stream, a video with merged audio, a video with separate audio stream,
	 * or an audio-only source. This is useful for debugging and UI state tracking.
	 *
	 * @return The {@link SourceType} of the last resolved media source,
	 *         or {@code null} if no source has been resolved yet.
	 * @see #resolve(StreamInfo)
	 * @see SourceType
	 */
	@Nullable
	public SourceType getStreamSourceType() {
		return streamSourceType;
	}
	
	/**
	 * Resolves a {@link StreamInfo} object into a playable {@link MediaSource}
	 * for ExoPlayer. This method selects the best video and audio streams based
	 * on user preferences, configuration settings, and stream availability.
	 *
	 * <p><strong>Resolution steps:</strong>
	 * <ol>
	 * <li>Attempts to build a live stream source (if applicable).</li>
	 * <li>Filters and sorts video streams using {@link #getSortedStreamVideosList}.</li>
	 * <li>Selects audio stream using {@link #getAudioFormatIndex}.</li>
	 * <li>Selects video stream using default or override resolution logic.</li>
	 * <li>Builds separate media sources for video and audio if needed.</li>
	 * <li>Adds subtitle sources via {@link #addSubtitleSources}.</li>
	 * <li>Merges multiple sources into a {@link MergingMediaSource} if necessary.</li>
	 * </ol>
	 *
	 * <p>The method handles various source types:
	 * <ul>
	 * <li>LIVE_STREAM – For HLS/DASH live content.</li>
	 * <li>VIDEO_WITH_SEPARATED_AUDIO – Video-only stream + separate audio.</li>
	 * <li>VIDEO_WITH_AUDIO_OR_AUDIO_ONLY – Combined or audio-only sources.</li>
	 * </ul>
	 *
	 * @param info The {@link StreamInfo} containing available video/audio/subtitle streams.
	 * @return A playable {@link MediaSource}, or {@code null} if no playable streams found.
	 * @see #getSortedStreamVideosList
	 * @see #getAudioFormatIndex
	 * @see #addSubtitleSources
	 * @see MergingMediaSource
	 */
	@Nullable
	public MediaSource resolve(@NonNull StreamInfo info) {
		final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(
			dataSource, info);
		if (liveSource != null) {
			streamSourceType = SourceType.LIVE_STREAM;
			return liveSource;
		}
		
		final List<MediaSource> sources = new ArrayList<>();
		
		final List<VideoStream> allVideos = getSortedStreamVideosList(
			info.getVideoStreams(), info.getVideoOnlyStreams(),
			config.preferVideoOnly(), config.getDefaultVideoFormat(),
			config.showHigherResolutions(), info.getServiceId());
		
		final List<AudioStream> allAudio = getFilteredAudioStreams(
			info.getAudioStreams(), info.getServiceId());
		
		int audioIndex = getAudioFormatIndex(allAudio,
			config.getPreferredAudioLanguage());
		
		if (allVideos.isEmpty() && allAudio.isEmpty()) {
			logger.warning("No playable streams for " + info.getName());
			return null;
		}
		
		final int videoIndex;
		if (allVideos.isEmpty()) {
			videoIndex = -1;
		} else if (playbackQuality == null) {
			videoIndex = getDefaultResolutionIndex(allVideos, config.getDefaultVideoFormat());
		} else {
			videoIndex = getOverrideResolutionIndex(allVideos, playbackQuality,
				config.getDefaultVideoFormat());
		}
		
		final VideoStream selectedVideo = (videoIndex >= 0 && videoIndex < allVideos.size())
			? allVideos.get(videoIndex) : null;
		
		final AudioStream selectedAudio = (audioIndex >= 0 && audioIndex < allAudio.size())
			? allAudio.get(audioIndex) : null;
		
		final MediaItemTag tag = new MediaItemTag(
			info.getServiceId(), videoIndex, audioIndex,
			selectedVideo, selectedAudio,
			allVideos, allAudio);
		
		if (selectedVideo != null) {
			final String cacheKey = PlaybackResolver.cacheKeyOf(info, selectedVideo);
			final MediaSource videoSource = PlaybackResolver.buildMediaSource(
				dataSource, selectedVideo, info, cacheKey, tag);
			if (videoSource != null) {
				sources.add(videoSource);
			}
		}
		
		final boolean needsSeparateAudio = selectedVideo != null
			&& (selectedVideo.isVideoOnly() || audioTrack != null);
		if (selectedAudio != null && (selectedVideo == null || needsSeparateAudio)) {
			final String cacheKey = PlaybackResolver.cacheKeyOf(info, selectedAudio);
			final MediaSource audioSource = PlaybackResolver.buildMediaSource(
				dataSource, selectedAudio, info, cacheKey, tag);
			if (audioSource != null) {
				sources.add(audioSource);
			}
		}
		
		if (selectedVideo != null && selectedAudio != null && needsSeparateAudio) {
			streamSourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO;
		} else if (selectedVideo != null) {
			streamSourceType = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY;
		} else if (selectedAudio != null) {
			streamSourceType = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY;
		}
		
		addSubtitleSources(info, sources);
		
		if (sources.isEmpty()) return null;
		if (sources.size() == 1) return sources.get(0);
		return new MergingMediaSource(true, sources.toArray(new MediaSource[0]));
	}
	
	
	/**
	 * Creates a sorted and deduplicated list of video streams based on user
	 * preferences, available streams, and format ranking. This method filters,
	 * merges, deduplicates, and sorts video streams for optimal playback selection.
	 *
	 * <p><strong>Processing steps:</strong>
	 * <ol>
	 * <li>Filters playable streams from both video and video-only sources.</li>
	 * <li>Orders the two sources based on {@code preferVideoOnlyStreams}.</li>
	 * <li>Filters out high resolutions if {@code showHigherResolutions} is false.</li>
	 * <li>Deduplicates streams by resolution, preferring the default format when present.</li>
	 * <li>Sorts by resolution (descending) and then by format quality ranking.</li>
	 * </ol>
	 *
	 * <p><strong>High resolution filtering:</strong>
	 * When {@code showHigherResolutions} is false, resolutions in
	 * {@link #HIGH_RESOLUTION_LIST} (e.g., "2160p", "1440p") are excluded.
	 *
	 * @param videoStreams          Regular video streams (with audio).
	 * @param videoOnlyStreams      Video-only streams (no audio).
	 * @param preferVideoOnlyStreams If true, video-only streams are prioritized.
	 * @param defaultFormat         Preferred media format for deduplication.
	 * @param showHigherResolutions If true, include 4K/1440p; otherwise filter them out.
	 * @param serviceId             Service ID for service-specific filtering.
	 * @return A sorted, deduplicated list of video streams ready for playback.
	 */
	@NonNull
	private List<VideoStream> getSortedStreamVideosList(
		@Nullable List<VideoStream> videoStreams,
		@Nullable List<VideoStream> videoOnlyStreams,
		final boolean preferVideoOnlyStreams,
		@Nullable final MediaFormat defaultFormat,
		final boolean showHigherResolutions,
		final int serviceId) {
		
		final List<VideoStream> videoFiltered = filterPlayable(videoStreams, serviceId);
		final List<VideoStream> videoOnlyFiltered = filterPlayable(videoOnlyStreams, serviceId);
		
		final List<List<VideoStream>> ordered = preferVideoOnlyStreams
			? Arrays.asList(videoFiltered, videoOnlyFiltered)
			: Arrays.asList(videoOnlyFiltered, videoFiltered);
		
		final List<VideoStream> allInitialStreams = ordered.stream()
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.filter(s -> showHigherResolutions
				|| !HIGH_RESOLUTION_LIST.contains(
				s.getResolution().replaceAll("p\\d+$", "p")))
			.collect(Collectors.toList());
		
		final HashMap<String, VideoStream> deduped = new HashMap<>();
		for (final VideoStream vs : allInitialStreams) {
			deduped.put(vs.getResolution(), vs);
		}
		for (final VideoStream vs : allInitialStreams) {
			if (vs.getFormat() == defaultFormat) {
				deduped.put(vs.getResolution(), vs);
			}
		}
		
		final List<VideoStream> result = new ArrayList<>(deduped.values());
		result.sort((a, b) -> {
			int cmp = compareVideoStreamResolution(b.getResolution(), a.getResolution());
			if (cmp != 0) return cmp;
			return Integer.compare(
				VIDEO_FORMAT_QUALITY_RANKING.indexOf(a.getFormat()),
				VIDEO_FORMAT_QUALITY_RANKING.indexOf(b.getFormat()));
		});
		return result;
	}
	
	/**
	 * Filters a list of video streams to include only those that are playable
	 * by the player. This method excludes streams with incompatible delivery
	 * methods (e.g., torrent), unsupported itags (for YouTube), and streams
	 * with unresolvable URLs.
	 *
	 * <p><strong>Filtering rules:</strong>
	 * <ul>
	 * <li>Excludes streams with torrent delivery method.</li>
	 * <li>For YouTube streams, excludes unsupported itag IDs.</li>
	 * <li>Excludes streams with unresolvable URLs (null from {@link #resolveUrl(Stream)}).</li>
	 * </ul>
	 *
	 * @param streams   The complete list of available video streams, or null.
	 * @param serviceId The service ID (e.g., YouTube) used to apply service-specific rules.
	 * @return A filtered list of playable video streams, or null if the input was null.
	 * @see #isSupportedItag(ItagItem)
	 * @see #resolveUrl(Stream)
	 */
	@Nullable
	private static List<VideoStream> filterPlayable(
		@Nullable List<VideoStream> streams, final int serviceId) {
		if (streams == null) return null;
		final boolean isYoutube = serviceId == ServiceList.YouTube.getServiceId();
		final List<VideoStream> result = new ArrayList<>();
		for (final VideoStream vs : streams) {
			if (vs.getDeliveryMethod() == DeliveryMethod.TORRENT) continue;
			if (isYoutube && !isSupportedItag(vs.getItagItem())) continue;
			if (resolveUrl(vs) == null) continue;
			result.add(vs);
		}
		return result;
	}
	
	/**
	 * Compares two video resolution strings numerically, extracting the numeric
	 * height value from each (e.g., "1080p" → 1080). The method handles resolution
	 * strings that may include refresh rate suffixes (e.g., "1080p60") by replacing
	 * "0p" patterns with "1" as a fallback, then strips all non-digit characters
	 * before parsing.
	 *
	 * <p><strong>Examples:</strong>
	 * <ul>
	 * <li>"1080p" vs "720p" → 1080 - 720 = 360 (positive)</li>
	 * <li>"720p30" vs "1080p" → 720 - 1080 = -360 (negative)</li>
	 * <li>On parse error, returns 1 (treating r1 as greater than r2).</li>
	 * </ul>
	 *
	 * @param r1 The first resolution string (e.g., "1080p").
	 * @param r2 The second resolution string (e.g., "720p").
	 * @return Negative if r1 < r2, positive if r1 > r2, or 1 on error.
	 */
	private static int compareVideoStreamResolution(@NonNull final String r1,
	                                                @NonNull final String r2) {
		try {
			final int res1 = Integer.parseInt(r1.replaceAll("0p\\d+$", "1")
				.replaceAll("[^\\d.]", ""));
			final int res2 = Integer.parseInt(r2.replaceAll("0p\\d+$", "1")
				.replaceAll("[^\\d.]", ""));
			return res1 - res2;
		} catch (final NumberFormatException e) {
			return 1;
		}
	}
	
	/**
	 * Parses a resolution string (e.g., "1080p", "720p", "4k") and extracts the
	 * numeric height value as an integer. The method handles standard resolution
	 * formats and strips any non-digit characters before parsing. For "4k",
	 * it would extract "4" (which may need special handling in callers).
	 *
	 * <p><strong>Examples:</strong>
	 * <ul>
	 * <li>"1080p" → 1080</li>
	 * <li>"720p60" → 720</li>
	 * <li>"480p (H264)" → 480</li>
	 * <li>"4k" → 4 (caller should map to 2160 if needed)</li>
	 * </ul>
	 *
	 * @param resolution The resolution string to parse (e.g., "1080p").
	 * @return The numeric resolution value, or 0 if parsing fails.
	 */
	private static int parseResolutionValue(@NonNull final String resolution) {
		try {
			final int pIndex = resolution.indexOf('p');
			if (pIndex > 0) {
				return Integer.parseInt(resolution.substring(0, pIndex)
					.replaceAll("[^\\d]", ""));
			}
			return Integer.parseInt(resolution.replaceAll("[^\\d]", ""));
		} catch (final NumberFormatException e) {
			return 0;
		}
	}
	
	/**
	 * Strips refresh rate information from a resolution string. For example,
	 * "1080p60" becomes "1080p", while "1080p" remains unchanged. This method
	 * is useful for comparing resolutions that differ only by frame rate.
	 *
	 * @param resolution The resolution string that may contain refresh rate (e.g., "720p30").
	 * @return The resolution string with refresh rate suffix removed.
	 */
	@NonNull
	private static String stripRefreshRate(@NonNull final String resolution) {
		return resolution.replaceAll("p\\d+$", "p");
	}
	
	/**
	 * Determines the default resolution index for video playback when no user
	 * preference is specified. This implementation returns the first index (0)
	 * when the list is non-empty, which typically corresponds to the highest
	 * available resolution in a descending sorted list.
	 *
	 * @param sortedVideos  List of video streams sorted by resolution (descending).
	 * @param defaultFormat Optional preferred media format (currently unused).
	 * @return The index of the default video stream, or 0 if the list is empty.
	 */
	private static int getDefaultResolutionIndex(
		@NonNull final List<VideoStream> sortedVideos,
		@Nullable final MediaFormat defaultFormat) {
		if (sortedVideos.isEmpty()) return 0;
		return 0;
	}
	
	/**
	 * Determines the index of the best matching video stream for a requested
	 * playback quality. This method attempts to find an exact resolution match,
	 * then a format+resolution match, then a resolution-only match, and finally
	 * falls back to the closest lower resolution available.
	 *
	 * <p><strong>Matching priority (highest to lowest):</strong>
	 * <ol>
	 * <li>Exact string match with full quality descriptor (e.g., "1080p (H264)").</li>
	 * <li>Format + resolution match after stripping refresh rates.</li>
	 * <li>Resolution value match (numeric comparison, e.g., 720, 1080).</li>
	 * <li>Resolution string match after stripping refresh rates.</li>
	 * <li>Closest lower resolution (highest resolution ≤ requested).</li>
	 * </ol>
	 *
	 * <p>If no videos are available, returns 0 (first index). The method also
	 * handles edge cases where the quality string may include refresh rate
	 * information (e.g., "1080p60") which is stripped for comparison.
	 *
	 * @param sortedVideos    List of video streams sorted by resolution (descending).
	 * @param playbackQuality The requested playback quality string (e.g., "1080p (H264)").
	 * @param defaultFormat   Optional preferred media format for matching.
	 * @return The index of the best matching video stream within {@code sortedVideos}.
	 * @see #parseResolutionString(String)
	 * @see #parseFormatFromQuality(String)
	 * @see #parseResolutionValue(String)
	 * @see #stripRefreshRate(String)
	 */
	private static int getOverrideResolutionIndex(
		@NonNull final List<VideoStream> sortedVideos,
		@NonNull final String playbackQuality,
		@Nullable final MediaFormat defaultFormat) {
		if (sortedVideos.isEmpty()) return 0;
		
		final String qualityResolution = parseResolutionString(playbackQuality);
		final MediaFormat qualityFormat = parseFormatFromQuality(playbackQuality);
		
		for (int i = 0; i < sortedVideos.size(); i++) {
			final VideoStream vs = sortedVideos.get(i);
			if (vs.getResolution().equals(playbackQuality)) {
				return i;
			}
		}
		
		if (qualityFormat != null) {
			final String strippedQuality = stripRefreshRate(qualityResolution);
			for (int i = 0; i < sortedVideos.size(); i++) {
				final VideoStream vs = sortedVideos.get(i);
				if (vs.getFormat() == qualityFormat
					&& stripRefreshRate(vs.getResolution()).equals(strippedQuality)) {
					return i;
				}
			}
		}
		
		final int qualityResValue = parseResolutionValue(qualityResolution);
		for (int i = 0; i < sortedVideos.size(); i++) {
			final VideoStream vs = sortedVideos.get(i);
			if (parseResolutionValue(vs.getResolution()) == qualityResValue) {
				return i;
			}
		}
		
		final String strippedQualityRes = stripRefreshRate(qualityResolution);
		for (int i = 0; i < sortedVideos.size(); i++) {
			final VideoStream vs = sortedVideos.get(i);
			if (stripRefreshRate(vs.getResolution()).equals(strippedQualityRes)) {
				return i;
			}
		}
		
		int closestIndex = 0;
		int closestDiff = Integer.MAX_VALUE;
		for (int i = 0; i < sortedVideos.size(); i++) {
			final VideoStream vs = sortedVideos.get(i);
			final int vsRes = parseResolutionValue(vs.getResolution());
			if (vsRes <= qualityResValue) {
				final int diff = qualityResValue - vsRes;
				if (diff < closestDiff) {
					closestDiff = diff;
					closestIndex = i;
				}
			}
		}
		return closestIndex;
	}
	
	/**
	 * Extracts and returns the pure resolution string from a video quality
	 * descriptor by removing any parenthesized suffix. For example, the input
	 * "1080p (H264)" will return "1080p". The method uses a regular expression
	 * to strip trailing content enclosed in parentheses with optional leading spaces.
	 *
	 * @param quality The raw quality string (e.g., "720p (VP9)" or "1080p").
	 * @return The cleaned resolution string with parentheses and their contents removed.
	 * @see #parseFormatFromQuality(String)
	 */
	@NonNull
	private static String parseResolutionString(@NonNull final String quality) {
		return quality.replaceAll("\\s*\\(.*\\)$", "").trim();
	}
	
	/**
	 * Parses and extracts the media format from a quality string containing format
	 * information in parentheses. For example, the input "1080p (H264)" will return
	 * {@link MediaFormat#H264}. The method searches for a format name within
	 * parentheses and matches it against known {@link MediaFormat} values.
	 *
	 * <p><strong>Format detection:</strong>
	 * <ul>
	 * <li>Looks for parentheses in the quality string (e.g., "(H264)").</li>
	 * <li>If no parentheses are found, returns null.</li>
	 * <li>Extracts the substring between the parentheses and trims whitespace.</li>
	 * <li>Iterates through all {@link MediaFormat} values to find a case-insensitive
	 *     name match.</li>
	 * </ul>
	 *
	 * @param quality The raw quality string containing format information in parentheses.
	 * @return The detected {@link MediaFormat}, or {@code null} if no format is found
	 * or the format cannot be matched to a known value.
	 * @see #parseResolutionString(String)
	 * @see MediaFormat#getName()
	 */
	@Nullable
	private static MediaFormat parseFormatFromQuality(@NonNull final String quality) {
		final int idx = quality.indexOf('(');
		if (idx < 0) return null;
		final String formatPart = quality.substring(idx + 1, quality.indexOf(')', idx)).trim();
		for (final MediaFormat fmt : MediaFormat.values()) {
			if (fmt.getName() != null && fmt.getName().equalsIgnoreCase(formatPart)) {
				return fmt;
			}
		}
		return null;
	}
	
	/**
	 * Filters and deduplicates a list of audio streams based on delivery method,
	 * format compatibility, itag support (for YouTube), and URL validity. This
	 * method ensures only playable streams are considered for audio track selection.
	 *
	 * <p><strong>Filtering rules:</strong>
	 * <ul>
	 * <li>Excludes streams with torrent delivery method.</li>
	 * <li>Excludes OPUS streams delivered via HLS (incompatible with ExoPlayer).</li>
	 * <li>For YouTube streams, excludes unsupported itag IDs.</li>
	 * <li>Excludes streams with unresolvable URLs.</li>
	 * </ul>
	 *
	 * <p><strong>Deduplication:</strong>
	 * <ul>
	 * <li>Groups streams by {@link AudioStream#getAudioTrackId()}.</li>
	 * <li>For duplicate track IDs, keeps the stream with the highest bitrate.</li>
	 * <li>Removes the empty-string track ID if multiple streams remain, as this
	 *     indicates an untagged default track that should not be prioritized.</li>
	 * </ul>
	 *
	 * @param streams   The complete list of available audio streams, or null.
	 * @param serviceId The service ID (e.g., YouTube) used to apply service-specific rules.
	 * @return A filtered and deduplicated list of playable audio streams.
	 * @see #isSupportedItag(ItagItem)
	 * @see #resolveUrl(Stream)
	 */
	@NonNull
	private List<AudioStream> getFilteredAudioStreams(
		@Nullable List<AudioStream> streams, final int serviceId) {
		if (streams == null) return Collections.emptyList();
		final boolean isYoutube = serviceId == ServiceList.YouTube.getServiceId();
		final List<AudioStream> filtered = new ArrayList<>();
		for (final AudioStream as : streams) {
			if (as.getDeliveryMethod() == DeliveryMethod.TORRENT) continue;
			if (as.getDeliveryMethod() == DeliveryMethod.HLS
				&& as.getFormat() == MediaFormat.OPUS) continue;
			if (isYoutube && !isSupportedItag(as.getItagItem())) continue;
			if (resolveUrl(as) == null) continue;
			filtered.add(as);
		}
		
		final HashMap<String, AudioStream> deduped = new HashMap<>();
		for (final AudioStream as : filtered) {
			final String trackId = Objects.toString(as.getAudioTrackId(), "");
			final AudioStream existing = deduped.get(trackId);
			if (existing == null || as.getAverageBitrate() > existing.getAverageBitrate()) {
				deduped.put(trackId, as);
			}
		}
		
		if (deduped.size() > 1) {
			deduped.remove("");
		}
		
		return new ArrayList<>(deduped.values());
	}
	
	/**
	 * Determines the index of the best audio stream for playback from a list of
	 * available streams, based on user preferences and configuration settings.
	 * This method filters streams by service ID, then applies a comparator
	 * ({@link #getAudioTrackComparator}) to select the highest-ranked option.
	 *
	 * <p><strong>Selection process:</strong>
	 * <ol>
	 * <li>Filters audio streams to those matching the specified service ID.</li>
	 * <li>If no streams remain after filtering, returns -1.</li>
	 * <li>Creates a comparator using user preferences (language, original audio
	 *     preference, descriptive audio preference, data usage limits).</li>
	 * <li>Iterates through filtered streams to find the best-ranked option.</li>
	 * <li>Returns the index of the best stream within the filtered list.</li>
	 * </ol>
	 *
	 * <p>The returned index corresponds to a position in the original unfiltered
	 * list only if the caller maps it appropriately. This method is typically
	 * used to select a default audio track when multiple formats are available.
	 *
	 * @param audioStreams      The complete list of available audio streams, or null.
	 * @param preferredLanguage User's preferred language code (ISO3 or 2-letter).
	 * @param serviceId         The service ID (e.g., YouTube, SoundCloud) to filter by.
	 * @return The index of the best matching audio stream within the filtered list,
	 * or -1 if no suitable streams are available.
	 * @see #getFilteredAudioStreams(List, int)
	 * @see #getAudioTrackComparator(String, MediaFormat, boolean, boolean, boolean)
	 */
	private int getAudioFormatIndex(@NonNull List<AudioStream> filtered,
	                                @Nullable String preferredLanguage) {
		if (filtered.isEmpty()) return -1;
		
		final Comparator<AudioStream> comparator = getAudioTrackComparator(
			preferredLanguage, config.getDefaultAudioFormat(),
			config.preferOriginalAudio(), config.preferDescriptiveAudio(),
			config.limitDataUsage());
		int bestIndex = 0;
		AudioStream best = filtered.get(0);
		for (int i = 1; i < filtered.size(); i++) {
			final AudioStream current = filtered.get(i);
			if (comparator.compare(best, current) < 0) {
				best = current;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	/**
	 * Creates a comparator for sorting audio streams based on user preferences.
	 * The comparator evaluates streams by multiple criteria in priority order:
	 * original audio preference, language match, track type, default format,
	 * bitrate, and format efficiency/quality ranking.
	 *
	 * <p><strong>Sorting priority (highest to lowest):</strong>
	 * <ol>
	 * <li>Original vs dubbed audio (if {@code preferOriginalAudio} is true).</li>
	 * <li>Language score (exact match > English > other).</li>
	 * <li>Track type ranking (e.g., descriptive, commentary, standard).</li>
	 * <li>Default format preference (if specified).</li>
	 * <li>Bitrate (higher for quality mode, lower for data saving mode).</li>
	 * <li>Format ranking (efficiency for data saving, quality for standard).</li>
	 * </ol>
	 *
	 * @param preferredLanguage      User's preferred language code (ISO3 or 2-letter).
	 * @param defaultFormat          Preferred media format, or null if not specified.
	 * @param preferOriginalAudio    If true, original language tracks are prioritized.
	 * @param preferDescriptiveAudio If true, descriptive audio tracks are prioritized.
	 * @param limitDataUsage         If true, prefers efficient formats and lower bitrates.
	 * @return A comparator for sorting {@link AudioStream} objects.
	 */
	private static Comparator<AudioStream> getAudioTrackComparator(
		@Nullable final String preferredLanguage,
		@Nullable final MediaFormat defaultFormat,
		final boolean preferOriginalAudio,
		final boolean preferDescriptiveAudio,
		final boolean limitDataUsage) {
		
		final List<MediaFormat> formatRanking = limitDataUsage
			? AUDIO_FORMAT_EFFICIENCY_RANKING
			: AUDIO_FORMAT_QUALITY_RANKING;
		
		final List<AudioTrackType> trackTypeRanking = preferDescriptiveAudio
			? AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE
			: AUDIO_TRACK_TYPE_RANKING;
		
		return (a, b) -> {
			if (preferOriginalAudio) {
				final boolean aIsOriginal = a.getAudioTrackType() == AudioTrackType.ORIGINAL;
				final boolean bIsOriginal = b.getAudioTrackType() == AudioTrackType.ORIGINAL;
				if (aIsOriginal != bIsOriginal) {
					return aIsOriginal ? 1 : -1;
				}
			}
			
			final int aLangScore = languageScore(a, preferredLanguage);
			final int bLangScore = languageScore(b, preferredLanguage);
			if (aLangScore != bLangScore) {
				return Integer.compare(aLangScore, bLangScore);
			}
			
			final int aTypeIdx = trackTypeRanking.indexOf(a.getAudioTrackType());
			final int bTypeIdx = trackTypeRanking.indexOf(b.getAudioTrackType());
			if (aTypeIdx != bTypeIdx) {
				return Integer.compare(aTypeIdx, bTypeIdx);
			}
			
			if (defaultFormat != null) {
				final boolean aIsDefault = a.getFormat() == defaultFormat;
				final boolean bIsDefault = b.getFormat() == defaultFormat;
				if (aIsDefault != bIsDefault) {
					return aIsDefault ? 1 : -1;
				}
			}
			
			final int bitrateCompare = Integer.compare(
				a.getAverageBitrate(), b.getAverageBitrate());
			if (bitrateCompare != 0) {
				return limitDataUsage ? -bitrateCompare : bitrateCompare;
			}
			
			return Integer.compare(
				formatRanking.indexOf(a.getFormat()),
				formatRanking.indexOf(b.getFormat()));
		};
	}
	
	/**
	 * Calculates a preference score for an audio stream based on how well its
	 * language matches the user's preferred language. Higher scores indicate
	 * better matches, allowing the player to select the most appropriate audio
	 * track when multiple options are available.
	 *
	 * <p><strong>Scoring logic:</strong>
	 * <ul>
	 * <li>Exact match (ISO3 or 2-letter code) → 5 points.</li>
	 * <li>English audio when no preferred language set → 3 points.</li>
	 * <li>Other languages → 1 point.</li>
	 * <li>Streams with null locale → 0 points (excluded from consideration).</li>
	 * </ul>
	 *
	 * <p>ISO3 language codes (e.g., "eng", "spa") are preferred for matching,
	 * but 2-letter codes (e.g., "en", "es") are also supported for compatibility
	 * with simpler locale representations.
	 *
	 * @param stream            The {@link AudioStream} to evaluate.
	 * @param preferredLanguage The user's preferred language code (ISO3 or 2-letter),
	 *                          or {@code null} if no preference is set.
	 * @return A score from 0 to 5, where higher values indicate a better match.
	 * @see #ENGLISH_LANGUAGE
	 */
	private static int languageScore(@NonNull final AudioStream stream,
	                                 @Nullable final String preferredLanguage) {
		final Locale locale = stream.getAudioLocale();
		if (locale == null) return 0;
		
		if (preferredLanguage != null) {
			if (preferredLanguage.equals(locale.getISO3Language())) return 5;
			if (preferredLanguage.length() == 2
				&& preferredLanguage.equals(locale.getLanguage())) return 5;
		}
		
		if (ENGLISH_LANGUAGE.equals(locale.getISO3Language())) return 3;
		
		return 1;
	}
	
	/**
	 * Checks whether a given Itag item is supported by the player. An Itag is
	 * considered supported if it is null (indicating no specific format
	 * restrictions) or if its ID exists in the {@link #SUPPORTED_ITAG_IDS} set.
	 *
	 * <p>Supported itags typically include common video/audio formats such as
	 * H264, VP9, Opus, and AAC that are compatible with the ExoPlayer
	 * implementation.
	 *
	 * @param itag The {@link ItagItem} to check, or null.
	 * @return {@code true} if the itag is null or its ID is in the supported set,
	 * {@code false} otherwise.
	 * @see #SUPPORTED_ITAG_IDS
	 */
	private static boolean isSupportedItag(@Nullable final ItagItem itag) {
		return itag == null || SUPPORTED_ITAG_IDS.contains(itag.id);
	}
	
	/**
	 * Resolves the actual URL for a given stream. If the stream provides a direct
	 * URL via {@link Stream#isUrl()} and {@link Stream#getContent()}, that URL
	 * is returned. Otherwise, the method falls back to {@link Stream#getUrl()}.
	 *
	 * <p>This resolution is necessary because different streaming sources may
	 * store the playable URL in different fields depending on how they were
	 * extracted (e.g., direct links vs. progressive download URLs).
	 *
	 * @param stream The {@link Stream} containing URL information. Must not be null.
	 * @return The resolved URL string, or {@code null} if neither field contains
	 * a valid URL.
	 * @see Stream#isUrl()
	 * @see Stream#getContent()
	 * @see Stream#getUrl()
	 */
	@Nullable
	private static String resolveUrl(@NonNull final Stream stream) {
		if (stream.isUrl()) {
			return stream.getContent();
		}
		return stream.getUrl();
	}
	
	/**
	 * Adds subtitle sources to the provided list for the given stream information.
	 * This method iterates through available subtitle streams, filters out invalid
	 * sources (non-URL or torrent delivery methods), and builds
	 * {@link SingleSampleMediaSource} instances for each valid subtitle track.
	 *
	 * <p><strong>Subtitle filtering:</strong>
	 * <ul>
	 * <li>Skips subtitles that are not URL-based.</li>
	 * <li>Skips subtitles using torrent delivery method.</li>
	 * <li>Skips subtitles with null format (mime type unavailable).</li>
	 * </ul>
	 *
	 * <p><strong>Role flag assignment:</strong>
	 * <ul>
	 * <li>Auto-generated subtitles → {@link C#ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND}.</li>
	 * <li>Manual/caption subtitles → {@link C#ROLE_FLAG_CAPTION}.</li>
	 * </ul>
	 *
	 * @param info    The {@link StreamInfo} containing subtitle tracks.
	 * @param sources The mutable list to which subtitle {@link MediaSource} instances
	 *                will be added. Must not be null.
	 * @see SubtitlesStream
	 * @see SingleSampleMediaSource
	 * @see C.RoleFlags
	 */
	private void addSubtitleSources(@NonNull StreamInfo info,
	                                @NonNull List<MediaSource> sources) {
		final List<SubtitlesStream> subtitles = info.getSubtitles();
		if (subtitles == null || subtitles.isEmpty()) return;
		
		for (final SubtitlesStream subtitle : subtitles) {
			if (!subtitle.isUrl()
				|| subtitle.getDeliveryMethod() == DeliveryMethod.TORRENT) continue;
			final MediaFormat format = subtitle.getFormat();
			if (format == null) continue;
			
			@C.RoleFlags final int textRoleFlag = subtitle.isAutoGenerated()
				? C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
				: C.ROLE_FLAG_CAPTION;
			
			final MediaItem.SubtitleConfiguration config =
				new MediaItem.SubtitleConfiguration.Builder(
					Uri.parse(subtitle.getContent()))
					.setMimeType(format.getMimeType())
					.setRoleFlags(textRoleFlag)
					.setLanguage(subtitle.getLanguageTag())
					.build();
			
		final MediaSource textSource = new SingleSampleMediaSource
			.Factory(dataSource.getCacheDataSourceFactory())
			.createMediaSource(config, C.TIME_UNSET);
			sources.add(textSource);
		}
	}
}
