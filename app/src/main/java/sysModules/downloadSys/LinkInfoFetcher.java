package sysModules.downloadSys;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sysModules.crashedHandler.CrashLogger;

/**
 * Reads a link's title / thumbnail / formats as fast as possible.
 * <ul>
 *   <li>YouTube: NewPipe first (about 1 second), yt-dlp only if that fails.</li>
 *   <li>Everything else: yt-dlp.</li>
 *   <li>The answer is cached for 20 minutes, so reopening the same link is instant.</li>
 * </ul>
 */
public final class LinkInfoFetcher {

	private static final long TTL_MS = 20L * 60 * 1000;
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	private static volatile boolean warming = false;

	private static final class Cached {
		final JSONObject info;
		final long time = System.currentTimeMillis();

		Cached(JSONObject info) {
			this.info = info;
		}
	}

	private LinkInfoFetcher() {}

	/** Starts both engines in the background so the first real request does not pay start-up. */
	public static void prewarm(Context context) {
		if (warming) return;
		warming = true;
		final Context app = context.getApplicationContext();
		Thread worker = new Thread(() -> {
			try {
				NewPipeEngine.ensureInit();
			} catch (Throwable ignored) {
			}
			try {
				YtDlpRunner.ensureInit(app);
			} catch (Throwable ignored) {
			}
		}, "engine-warmup");
		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();
	}

	public static JSONObject fetch(Context context, String url) throws Exception {
		Cached cached = CACHE.get(url);
		if (cached != null && System.currentTimeMillis() - cached.time < TTL_MS) return cached.info;

		JSONObject info = null;
		Throwable newPipeError = null;
		Exception ytDlpError = null;

		if (NewPipeEngine.isYouTube(url)) {
			try {
				info = NewPipeEngine.fetchInfo(url);
			} catch (Throwable error) {
				newPipeError = error;
				CrashLogger.breadcrumb("newpipe info failed, trying yt-dlp: " + url, error);
			}
		}
		if (info == null) {
			try {
				info = YtDlpRunner.fetchInfo(context, url);
				info.put("engine", "ytdlp");
			} catch (Exception error) {
				ytDlpError = error;
			}
		}
		if (info == null) {
			if (ytDlpError != null) throw ytDlpError;
			throw new Exception(newPipeError == null ? "Could not read this link."
				: String.valueOf(newPipeError.getMessage()));
		}
		if (CACHE.size() > 30) CACHE.clear();
		CACHE.put(url, new Cached(info));
		return info;
	}
}
