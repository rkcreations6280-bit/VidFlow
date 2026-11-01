package sysModules.downloadSys;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import com.nextgen.R;

import dataRepo.appConfigs.AppConfigsRepo;

/** Plays the short download sound effects (respects the "Completion sound" setting). */
public final class DownloadSounds {

	private DownloadSounds() {}

	public static void added(Context context) {
		play(context, R.raw.sound_download_added);
	}

	public static void finished(Context context) {
		play(context, R.raw.sound_download_finished);
	}

	private static void play(Context context, int rawRes) {
		try {
			if (!AppConfigsRepo.getConfig().isDownloadCompletionSoundEnabled) return;
		} catch (Throwable ignored) {
		}
		try {
			Context app = context.getApplicationContext();
			MediaPlayer player = MediaPlayer.create(app, rawRes, new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_NOTIFICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build(), 0);
			if (player == null) return;
			player.setOnCompletionListener(MediaPlayer::release);
			player.setOnErrorListener((mp, what, extra) -> {
				mp.release();
				return true;
			});
			player.start();
		} catch (Throwable ignored) {
		}
	}
}
