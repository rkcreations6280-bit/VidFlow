package sysModules.ytdlpWrapper;

import android.content.Context;
import android.content.SharedPreferences;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

import coreUtils.base.BaseApplication;
import coreUtils.library.networks.NetworkUtils;
import coreUtils.library.process.LoggerUtils;

/**
 * Starts yt-dlp when the app starts. No server of ours is contacted: the only network call is
 * the optional yt-dlp self-update, which downloads the official release from GitHub (a plain
 * GET with no device id or any user data) and runs at most once every 12 hours.
 */
public class YtDlpLibraryManager {

	private static final LoggerUtils logger = LoggerUtils.from(YtDlpLibraryManager.class);
	private static final String PREFS = "ytdlp_engine";
	private static final String KEY_LAST_UPDATE = "last_update_check";
	private static final long UPDATE_EVERY_MS = 12L * 60 * 60 * 1000;
	private static volatile boolean started = false;

	private YtDlpLibraryManager() {}

	public static void initialize(BaseApplication appContext) {
		if (started) return;
		started = true;
		Thread worker = new Thread(() -> {
			try {
				YoutubeDL.getInstance().init(appContext);
				try {
					FFmpeg.getInstance().init(appContext);
				} catch (Throwable ignored) {
				}
				updateIfDue(appContext);
			} catch (Throwable error) {
				logger.error("yt-dlp initialization failed", error);
			}
		}, "ytdlp-init");
		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();
	}

	private static void updateIfDue(Context context) {
		try {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			long now = System.currentTimeMillis();
			if (now - prefs.getLong(KEY_LAST_UPDATE, 0) < UPDATE_EVERY_MS) return;
			if (!NetworkUtils.isInternetConnected()) return;
			// Stamp first so a crash or timeout does not retry on every start.
			prefs.edit().putLong(KEY_LAST_UPDATE, now).apply();
			YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE);
		} catch (Throwable error) {
			logger.error("yt-dlp update skipped", error);
		}
	}
}
