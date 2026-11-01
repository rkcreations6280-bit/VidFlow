package sysModules.downloadSys;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Locale;

import coreUtils.library.networks.HttpClientProvider;
import dataRepo.appConfigs.AppConfigsRepo;
import sysModules.newPipeLib.libs.DefaultYTDownloaderImpl;
import sysModules.newPipeLib.parsers.YtSteamExtractor;

/**
 * NewPipe Extractor side of the downloader. It talks to YouTube directly over HTTPS (no Python,
 * no process start-up), so reading a link takes about a second. yt-dlp stays as the fallback and
 * as the engine for every other website.
 */
public final class NewPipeEngine {

	private NewPipeEngine() {}

	public static boolean isYouTube(String url) {
		try {
			String host = Uri.parse(url).getHost();
			if (host == null) return false;
			host = host.toLowerCase(Locale.US);
			return host.equals("youtu.be") || host.endsWith(".youtu.be")
				|| host.equals("youtube.com") || host.endsWith(".youtube.com")
				|| host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com");
		} catch (Throwable error) {
			return false;
		}
	}

	/** Safe to call from any thread, any number of times. */
	public static synchronized void ensureInit() {
		try {
			if (NewPipe.getDownloader() != null) return;
		} catch (Throwable ignored) {
		}
		String region = "US";
		try {
			String saved = AppConfigsRepo.getConfig().selectedRegionCode;
			if (saved != null && !saved.isEmpty()) region = saved;
		} catch (Throwable ignored) {
		}
		NewPipe.init(new DefaultYTDownloaderImpl(HttpClientProvider.getOkHttpClient(8, 15)),
			new Localization("en", region));
	}

	/** Loads the stream list. One quick retry for a transient network error. */
	public static StreamInfo load(String url) throws Exception {
		ensureInit();
		Exception last = null;
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
				if (hasStreams(info)) return info;
				last = new Exception("No downloadable streams found.");
			} catch (Exception error) {
				last = error;
			}
			try {
				Thread.sleep(400);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		throw last == null ? new Exception("Could not read this link.") : last;
	}

	/** Any site NewPipe knows (SoundCloud, Bandcamp, PeerTube, ...). */
	public static StreamInfo loadAny(String url) throws Exception {
		ensureInit();
		return StreamInfo.getInfo(url);
	}

	private static boolean hasStreams(StreamInfo info) {
		return (info.getVideoOnlyStreams() != null && !info.getVideoOnlyStreams().isEmpty())
			|| (info.getVideoStreams() != null && !info.getVideoStreams().isEmpty())
			|| (info.getAudioStreams() != null && !info.getAudioStreams().isEmpty());
	}

	/** Same shape yt-dlp's {@code -J} gives, so the popup does not care which engine answered. */
	public static JSONObject fetchInfo(String url) throws Exception {
		StreamInfo info = load(url);
		JSONObject out = new JSONObject();
		out.put("title", info.getName());
		out.put("uploader", info.getUploaderName());
		out.put("duration", info.getDuration());
		String thumb = YtSteamExtractor.getBestImageQuality(info.getThumbnails());
		out.put("thumbnail", thumb == null ? "" : thumb);
		out.put("engine", "newpipe");

		JSONArray formats = new JSONArray();
		long duration = Math.max(0, info.getDuration());
		addVideo(formats, info.getVideoOnlyStreams(), false, duration);
		addVideo(formats, info.getVideoStreams(), true, duration);
		List<AudioStream> audio = info.getAudioStreams();
		if (audio != null) {
			for (AudioStream stream : audio) {
				JSONObject f = new JSONObject();
				f.put("vcodec", "none");
				f.put("acodec", codecOf(stream.getFormat(), true));
				f.put("height", 0);
				long size = sizeOf(stream);
				if (size <= 0 && stream.getAverageBitrate() > 0) {
					size = stream.getAverageBitrate() * 1000L / 8 * duration;
				}
				f.put("filesize", size);
				formats.put(f);
			}
		}
		out.put("formats", formats);
		return out;
	}

	private static void addVideo(JSONArray formats, List<VideoStream> streams, boolean muxed,
	                             long duration) throws Exception {
		if (streams == null) return;
		for (VideoStream stream : streams) {
			if (stream.getHeight() <= 0) continue;
			JSONObject f = new JSONObject();
			f.put("vcodec", codecOf(stream.getFormat(), false));
			f.put("acodec", muxed ? "mp4a" : "none");
			f.put("height", stream.getHeight());
			long size = sizeOf(stream);
			if (size <= 0 && stream.getBitrate() > 0) size = stream.getBitrate() / 8L * duration;
			f.put("filesize", size);
			formats.put(f);
		}
	}

	private static String codecOf(MediaFormat format, boolean audio) {
		if (format == MediaFormat.WEBM) return "vp9";
		if (format == MediaFormat.WEBMA || format == MediaFormat.OPUS) return "opus";
		return audio ? "mp4a" : "avc1";
	}

	static long sizeOf(Stream stream) {
		try {
			var itag = stream.getItagItem();
			if (itag != null) return Math.max(0, itag.getContentLength());
		} catch (Throwable ignored) {
		}
		return 0;
	}

	static boolean isHttp(Stream stream) {
		try {
			return stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP;
		} catch (Throwable error) {
			return false;
		}
	}

	/**
	 * Best video-only MP4 (H.264) up to {@code height}, because Android's MediaMuxer can join
	 * it with AAC audio without ffmpeg. Returns null when there is none.
	 */
	static VideoStream pickVideoOnly(StreamInfo info, int height) {
		return pickVideo(info.getVideoOnlyStreams(), height, true);
	}

	/** Best ready-made video+audio MP4 up to {@code height} (usually 360p / 720p). */
	static VideoStream pickMuxed(StreamInfo info, int height) {
		return pickVideo(info.getVideoStreams(), height, false);
	}

	private static VideoStream pickVideo(List<VideoStream> streams, int height, boolean onlyMp4) {
		if (streams == null) return null;
		VideoStream best = null;
		VideoStream smallest = null;
		for (VideoStream stream : streams) {
			if (!isHttp(stream)) continue;
			if (stream.getFormat() != MediaFormat.MPEG_4) continue;
			if (smallest == null || stream.getHeight() < smallest.getHeight()) smallest = stream;
			if (height > 0 && stream.getHeight() > height) continue;
			if (best == null || stream.getHeight() > best.getHeight()
				|| (stream.getHeight() == best.getHeight() && stream.getBitrate() > best.getBitrate())) {
				best = stream;
			}
		}
		return best != null ? best : smallest;
	}

	/** Best AAC (m4a) audio, or any direct audio when there is no m4a. */
	static AudioStream pickAudio(StreamInfo info, boolean preferM4a) {
		List<AudioStream> streams = info.getAudioStreams();
		if (streams == null) return null;
		AudioStream best = null;
		AudioStream anyBest = null;
		for (AudioStream stream : streams) {
			if (!isHttp(stream)) continue;
			if (anyBest == null || stream.getAverageBitrate() > anyBest.getAverageBitrate()) anyBest = stream;
			if (stream.getFormat() == MediaFormat.M4A
				&& (best == null || stream.getAverageBitrate() > best.getAverageBitrate())) {
				best = stream;
			}
		}
		return best != null ? best : anyBest;
	}
}
