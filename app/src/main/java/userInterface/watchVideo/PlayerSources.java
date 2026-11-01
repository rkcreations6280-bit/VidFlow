package userInterface.watchVideo;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sysModules.crashedHandler.CrashLogger;
import sysModules.downloadSys.LinkInfoFetcher;
import sysModules.downloadSys.NewPipeEngine;
import sysModules.downloadSys.YtDlpRunner;
import sysModules.mediaEngine.MediaProbe;
import sysModules.sysPlayer.mediaitem.MediaItemTag;
import sysModules.sysPlayer.resolver.PlayerDataSource;
import sysModules.sysPlayer.resolver.VideoPlaybackResolver;

/**
 * Turns a {@link PlayRequest} into something ExoPlayer can play.
 * <ul>
 *   <li>YouTube: NewPipe (fast, chunked, multi audio, quality list), then yt-dlp if that fails.</li>
 *   <li>Other websites: yt-dlp (HLS / DASH master playlists are kept whole, so quality, audio
 *       languages and subtitles stay selectable).</li>
 *   <li>Streams captured in the browser: played directly with the page's referer and cookies.</li>
 *   <li>Local files: played as they are.</li>
 * </ul>
 */
final class PlayerSources {

	/** What the screen needs to know about the loaded media. */
	static final class Result {
		MediaSource source;
		String title = "";
		String thumbnail = "";
		String engine = "";
		boolean audioOnly = false;
		boolean live = false;
		/** Which engine step produced this (see {@link PlayerSources#link}), and how many exist. */
		int step = 0;
		int steps = 1;
		// YouTube extras (null for other sources)
		StreamInfo info;
		VideoPlaybackResolver resolver;
		MediaItemTag tag;
	}

	/** Mutable so the audio-language menu can change it and resolve again. */
	static final class YtConfig implements VideoPlaybackResolver.Config {
		volatile String audioLanguage = null;

		@Nullable @Override public MediaFormat getDefaultVideoFormat() { return null; }
		@Nullable @Override public MediaFormat getDefaultAudioFormat() { return null; }
		@Override public boolean showHigherResolutions() { return true; }
		@Nullable @Override public String getPreferredAudioLanguage() { return audioLanguage; }
		@Override public boolean limitDataUsage() { return false; }
		@Override public boolean preferVideoOnly() { return false; }
		@Override public boolean preferOriginalAudio() { return false; }
		@Override public boolean preferDescriptiveAudio() { return false; }
	}

	private static PlayerDataSource sharedData;

	/** One cache per process: two SimpleCache objects on the same folder would crash. */
	static synchronized PlayerDataSource data(Context context) {
		if (sharedData == null) sharedData = new PlayerDataSource(context.getApplicationContext());
		return sharedData;
	}

	private final Context app;
	final YtConfig ytConfig = new YtConfig();

	PlayerSources(Context context) {
		this.app = context.getApplicationContext();
	}

	/** Slow work (network): call from a background thread. */
	Result resolve(PlayRequest request, int startStep) throws Exception {
		switch (request.kind) {
			case FILE:
				return file(request);
			case STREAM:
				return stream(request);
			default:
				return link(request, startStep);
		}
	}

	// ── Local file ───────────────────────────────────────────────────

	private Result file(PlayRequest request) {
		Result result = new Result();
		result.title = request.title;
		result.engine = "Local file";
		result.audioOnly = request.audio;
		MediaItem item = new MediaItem.Builder().setUri(Uri.parse(request.url)).build();
		result.source = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app))
			.createMediaSource(item);
		return result;
	}

	// ── Stream captured from a web page ──────────────────────────────

	private Result stream(PlayRequest request) {
		Map<String, String> headers = new HashMap<>();
		if (!request.referer.isEmpty()) {
			headers.put("Referer", request.referer);
			try {
				Uri ref = Uri.parse(request.referer);
				if (ref.getScheme() != null && ref.getHost() != null) {
					headers.put("Origin", ref.getScheme() + "://" + ref.getHost());
				}
			} catch (Throwable ignored) {
			}
		}
		if (!request.cookie.isEmpty()) headers.put("Cookie", request.cookie);

		Result result = new Result();
		result.title = request.title;
		result.engine = "Direct stream";
		result.audioOnly = request.audio;
		result.source = buildUrl(request.url, headers, request.userAgent, request.subs, null);
		return result;
	}

	private static String mimeFor(String url) {
		String lower = url.toLowerCase(Locale.US);
		int q = lower.indexOf('?');
		String path = q > 0 ? lower.substring(0, q) : lower;
		if (path.endsWith(".m3u8") || lower.contains("/m3u8") || lower.contains("format=m3u8")) {
			return MimeTypes.APPLICATION_M3U8;
		}
		if (path.endsWith(".mpd") || lower.contains("format=mpd")) return MimeTypes.APPLICATION_MPD;
		return null;
	}

	/** One URL (progressive, HLS or DASH) with request headers and optional side-loaded subtitles. */
	private MediaSource buildUrl(String url, Map<String, String> headers, String userAgent,
	                             List<PlayRequest.Sub> subs, @Nullable String forcedMime) {
		DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
			.setAllowCrossProtocolRedirects(true)
			.setConnectTimeoutMs(15000)
			.setReadTimeoutMs(20000);
		if (userAgent != null && !userAgent.isEmpty()) http.setUserAgent(userAgent);
		if (headers != null && !headers.isEmpty()) http.setDefaultRequestProperties(headers);

		MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
		String mime = forcedMime != null ? forcedMime : mimeFor(url);
		if (mime != null) builder.setMimeType(mime);
		List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
		if (subs != null) {
			for (PlayRequest.Sub sub : subs) {
				configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
					.setMimeType(subtitleMime(sub.url))
					.setLanguage(sub.lang.isEmpty() ? null : sub.lang)
					.setLabel(sub.label.isEmpty() ? (sub.lang.isEmpty() ? "Subtitles" : sub.lang) : sub.label)
					.build());
			}
		}
		if (!configs.isEmpty()) builder.setSubtitleConfigurations(configs);
		return new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app, http))
			.createMediaSource(builder.build());
	}

	private static String subtitleMime(String url) {
		String lower = url.toLowerCase(Locale.US);
		int q = lower.indexOf('?');
		String path = q > 0 ? lower.substring(0, q) : lower;
		if (path.endsWith(".srt")) return MimeTypes.APPLICATION_SUBRIP;
		if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
		if (path.endsWith(".ass") || path.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
		return MimeTypes.TEXT_VTT;
	}

	// ── Page link ────────────────────────────────────────────────────

	/**
	 * The engine ladder for a pasted link. Every step either gives ExoPlayer a media source or
	 * fails and the next one is tried. A later playback error continues from the next step.
	 * <pre>
	 *   YouTube : NewPipe, yt-dlp, yt-dlp (other YouTube clients)
	 *   others  : direct media link, yt-dlp, NewPipe (other services), page source scan
	 * </pre>
	 */
	private static final String[] LADDER_YT = {"NewPipe", "yt-dlp", "yt-dlp (alt client)"};
	private static final String[] LADDER_WEB = {"Direct link", "yt-dlp", "NewPipe", "Page scan"};

	private Result link(PlayRequest request, int startStep) throws Exception {
		final String url = request.url;
		final boolean yt = NewPipeEngine.isYouTube(url);
		final String[] ladder = yt ? LADDER_YT : LADDER_WEB;
		StringBuilder trace = new StringBuilder();
		for (int i = Math.max(0, startStep); i < ladder.length; i++) {
			try {
				Result r = runStep(url, ladder[i], trace);
				if (r != null) {
					r.step = i;
					r.steps = ladder.length;
					if (r.engine == null || r.engine.isEmpty()) r.engine = ladder[i];
					return r;
				}
				trace.append(ladder[i]).append(": nothing found\n");
			} catch (Throwable error) {
				String m = error.getMessage() == null ? error.toString() : error.getMessage();
				int at = m.lastIndexOf("ERROR:");
				if (at >= 0) m = m.substring(at);
				if (m.length() > 200) m = m.substring(0, 200) + "…";
				trace.append(ladder[i]).append(": ").append(m.trim()).append('\n');
				CrashLogger.breadcrumb("player: " + ladder[i] + " failed: " + url, error);
			}
		}
		throw new Exception(trace.length() == 0 ? "No playable stream was found for this link." : trace.toString().trim());
	}

	private Result runStep(String url, String step, StringBuilder trace) throws Exception {
		switch (step) {
			case "NewPipe":
				if (NewPipeEngine.isYouTube(url)) return youtube(url);
				return newPipeAny(url);
			case "yt-dlp":
				return fromYtDlp(YtDlpRunner.fetchInfo(app, url));
			case "yt-dlp (alt client)":
				return fromYtDlp(YtDlpRunner.fetchInfo(app, url,
					new String[]{"--extractor-args", "youtube:player_client=tv,android_vr,web_safari"}));
			case "Direct link": {
				String type = MediaProbe.probe(url, null);
				if (type == null) return null;
				Result r = new Result();
				Map<String, String> headers = new HashMap<>();
				r.source = buildUrl(url, headers, MediaProbe.UA, null, null);
				r.audioOnly = type.startsWith("audio") || MediaProbe.looksLikeAudio(url) || type.equals("audio");
				r.title = lastSegment(url);
				r.engine = "Direct link";
				return r;
			}
			case "Page scan": {
				MediaProbe.Found f = MediaProbe.scanPage(url, trace);
				if (f == null) return null;
				Map<String, String> headers = new HashMap<>();
				headers.put("Referer", f.pageUrl);
				Result r = new Result();
				r.source = buildUrl(f.url, headers, MediaProbe.UA, null, null);
				r.audioOnly = MediaProbe.looksLikeAudio(f.url);
				r.title = f.title.isEmpty() ? lastSegment(url) : f.title;
				r.engine = "Page scan";
				return r;
			}
			default:
				return null;
		}
	}

	private static String lastSegment(String url) {
		try {
			String seg = Uri.parse(url).getLastPathSegment();
			if (seg == null || seg.isEmpty()) return url;
			int dot = seg.lastIndexOf('.');
			return dot > 0 ? seg.substring(0, dot) : seg;
		} catch (Throwable e) {
			return url;
		}
	}

	/** NewPipe for non-YouTube services: HLS / DASH first, then the best progressive file. */
	private Result newPipeAny(String url) throws Exception {
		StreamInfo info = NewPipeEngine.loadAny(url);
		MediaSource source = null;
		boolean audioOnly = false;
		String hls = info.getHlsUrl();
		String dash = info.getDashMpdUrl();
		if (hls != null && hls.startsWith("http")) {
			source = buildUrl(hls, null, null, null, MimeTypes.APPLICATION_M3U8);
		} else if (dash != null && dash.startsWith("http")) {
			source = buildUrl(dash, null, null, null, MimeTypes.APPLICATION_MPD);
		}
		if (source == null) {
			org.schabi.newpipe.extractor.stream.VideoStream bestV = null;
			for (org.schabi.newpipe.extractor.stream.VideoStream v : info.getVideoStreams()) {
				if (!v.isUrl() || v.getContent() == null) continue;
				if (bestV == null || v.getHeight() > bestV.getHeight()) bestV = v;
			}
			if (bestV != null) source = buildUrl(bestV.getContent(), null, null, null, null);
		}
		if (source == null) {
			AudioStream bestA = null;
			for (AudioStream a : info.getAudioStreams()) {
				if (!a.isUrl() || a.getContent() == null) continue;
				if (bestA == null || a.getAverageBitrate() > bestA.getAverageBitrate()) bestA = a;
			}
			if (bestA != null) {
				source = buildUrl(bestA.getContent(), null, null, null, null);
				audioOnly = true;
			}
		}
		if (source == null) return null;
		Result result = new Result();
		result.source = source;
		result.title = info.getName();
		String thumb = sysModules.newPipeLib.parsers.YtSteamExtractor.getBestImageQuality(info.getThumbnails());
		result.thumbnail = thumb == null ? "" : thumb;
		result.engine = "NewPipe";
		result.audioOnly = audioOnly;
		return result;
	}

	private Result youtube(String url) throws Exception {
		StreamInfo info = NewPipeEngine.load(url);
		VideoPlaybackResolver resolver = new VideoPlaybackResolver(data(app), ytConfig);
		MediaSource source = resolver.resolve(info);
		if (source == null) return null;
		Result result = new Result();
		result.source = source;
		result.info = info;
		result.resolver = resolver;
		result.tag = MediaItemTag.from(source.getMediaItem());
		result.title = info.getName();
		String thumb = sysModules.newPipeLib.parsers.YtSteamExtractor.getBestImageQuality(info.getThumbnails());
		result.thumbnail = thumb == null ? "" : thumb;
		result.engine = "NewPipe";
		result.audioOnly = info.getVideoStreams().isEmpty() && info.getVideoOnlyStreams().isEmpty();
		result.live = resolver.getStreamSourceType() == VideoPlaybackResolver.SourceType.LIVE_STREAM;
		return result;
	}

	/** Re-resolves the same YouTube video after the quality / audio language changed. */
	MediaSource reresolve(Result result) {
		return result.resolver.resolve(result.info);
	}

	/** Audio languages a YouTube video offers: label to language code. Empty if only one. */
	static List<String[]> audioLanguages(Result result) {
		List<String[]> list = new ArrayList<>();
		if (result.tag == null) return list;
		java.util.Set<String> seen = new java.util.LinkedHashSet<>();
		for (AudioStream stream : result.tag.getSortedAudioStreams()) {
			Locale locale = stream.getAudioLocale();
			String code = locale == null ? "" : locale.getLanguage();
			String id = stream.getAudioTrackId() == null ? code : stream.getAudioTrackId();
			if (id == null) id = "";
			if (!seen.add(code + "|" + id)) continue;
			String name = stream.getAudioTrackName();
			if (name == null || name.isEmpty()) {
				name = locale == null ? "Track " + (list.size() + 1) : locale.getDisplayLanguage();
			}
			list.add(new String[]{name, code});
		}
		return list.size() > 1 ? list : new ArrayList<>();
	}

	// ── yt-dlp result → player source ────────────────────────────────

	private Result fromYtDlp(JSONObject json) throws Exception {
		JSONArray formats = json.optJSONArray("formats");
		if (formats == null) formats = new JSONArray();

		JSONObject master = null;       // HLS / DASH manifest: adaptive, multi audio, subtitles
		JSONObject bestCombined = null; // progressive video + audio in one file
		JSONObject bestVideo = null;
		JSONObject bestAudio = null;
		for (int i = 0; i < formats.length(); i++) {
			JSONObject f = formats.optJSONObject(i);
			if (f == null) continue;
			String url = f.optString("url", "");
			if (!url.startsWith("http")) continue;
			String protocol = f.optString("protocol", "");
			String manifest = f.optString("manifest_url", "");
			boolean hasVideo = !isNone(f.optString("vcodec", "none"));
			boolean hasAudio = !isNone(f.optString("acodec", "none"));
			int height = f.optInt("height", 0);
			if (protocol.contains("m3u8") || protocol.contains("dash")) {
				if (!manifest.isEmpty() && master == null) master = f;
				continue;
			}
			if (!(protocol.equals("https") || protocol.equals("http") || protocol.isEmpty())) continue;
			if (hasVideo && hasAudio) {
				if (bestCombined == null || height > bestCombined.optInt("height", 0)
					|| (height == bestCombined.optInt("height", 0)
					&& f.optDouble("tbr", 0) > bestCombined.optDouble("tbr", 0))) bestCombined = f;
			} else if (hasVideo) {
				if (bestVideo == null || height > bestVideo.optInt("height", 0)
					|| (height == bestVideo.optInt("height", 0)
					&& f.optDouble("tbr", 0) > bestVideo.optDouble("tbr", 0))) bestVideo = f;
			} else if (hasAudio) {
				if (bestAudio == null || f.optDouble("abr", f.optDouble("tbr", 0))
					> bestAudio.optDouble("abr", bestAudio.optDouble("tbr", 0))) bestAudio = f;
			}
		}

		List<PlayRequest.Sub> subs = subtitlesOf(json);
		MediaSource source = null;
		boolean audioOnly = false;

		if (master != null) {
			String manifest = master.optString("manifest_url");
			String mime = master.optString("protocol", "").contains("dash")
				? MimeTypes.APPLICATION_MPD : MimeTypes.APPLICATION_M3U8;
			source = buildUrl(manifest, headersOf(master, json), null, subs, mime);
		}
		// A progressive file that is clearly better than the best the manifest offers is rare;
		// the manifest keeps quality switching, so it wins when present.
		if (source == null && bestCombined != null && (bestVideo == null
			|| bestCombined.optInt("height", 0) >= bestVideo.optInt("height", 0))) {
			source = buildUrl(bestCombined.optString("url"), headersOf(bestCombined, json), null, subs, null);
		}
		if (source == null && bestVideo != null && bestAudio != null) {
			MediaSource video = buildUrl(bestVideo.optString("url"), headersOf(bestVideo, json), null, subs, null);
			MediaSource audio = buildUrl(bestAudio.optString("url"), headersOf(bestAudio, json), null, null, null);
			source = new MergingMediaSource(video, audio);
		}
		if (source == null && bestCombined != null) {
			source = buildUrl(bestCombined.optString("url"), headersOf(bestCombined, json), null, subs, null);
		}
		if (source == null && bestAudio != null) {
			source = buildUrl(bestAudio.optString("url"), headersOf(bestAudio, json), null, null, null);
			audioOnly = true;
		}
		if (source == null && bestVideo != null) {
			source = buildUrl(bestVideo.optString("url"), headersOf(bestVideo, json), null, subs, null);
		}
		String top = json.optString("url", "");
		if (source == null && top.startsWith("http")) {
			source = buildUrl(top, headersOf(json, json), null, subs, null);
		}
		if (source == null) throw new Exception("No playable stream was found for this link.");

		Result result = new Result();
		result.source = source;
		result.title = json.optString("title", "");
		String thumb = json.optString("thumbnail", "");
		result.thumbnail = "null".equals(thumb) ? "" : thumb;
		result.engine = "yt-dlp";
		result.audioOnly = audioOnly;
		result.live = json.optBoolean("is_live", false);
		return result;
	}

	private static boolean isNone(String codec) {
		return codec == null || codec.isEmpty() || "none".equals(codec) || "null".equals(codec);
	}

	private static Map<String, String> headersOf(JSONObject format, JSONObject root) {
		Map<String, String> headers = new HashMap<>();
		JSONObject h = format.optJSONObject("http_headers");
		if (h == null) h = root.optJSONObject("http_headers");
		if (h != null) {
			Iterator<String> keys = h.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				String value = h.optString(key, "");
				if (value.isEmpty()) continue;
				if (key.equalsIgnoreCase("Accept-Encoding")) continue; // let the player manage it
				headers.put(key, value);
			}
		}
		String cookies = format.optString("cookies", "");
		if (!cookies.isEmpty() && !"null".equals(cookies)) headers.put("Cookie", cookies);
		return headers;
	}

	private static List<PlayRequest.Sub> subtitlesOf(JSONObject json) {
		List<PlayRequest.Sub> list = new ArrayList<>();
		JSONObject subs = json.optJSONObject("subtitles");
		if (subs == null) return list;
		Iterator<String> langs = subs.keys();
		while (langs.hasNext() && list.size() < 30) {
			String lang = langs.next();
			if (lang.equals("live_chat")) continue;
			JSONArray entries = subs.optJSONArray(lang);
			if (entries == null) continue;
			String chosen = null;
			String label = "";
			for (int i = 0; i < entries.length(); i++) {
				JSONObject e = entries.optJSONObject(i);
				if (e == null) continue;
				String url = e.optString("url", "");
				if (!url.startsWith("http")) continue;
				String ext = e.optString("ext", "");
				if (chosen == null || ext.equals("vtt")) {
					chosen = url;
					label = e.optString("name", "");
				}
				if (ext.equals("vtt")) break;
			}
			if (chosen != null) list.add(new PlayRequest.Sub(chosen, label.isEmpty() ? lang : label, lang));
		}
		return list;
	}
}
