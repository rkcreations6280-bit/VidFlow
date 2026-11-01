package sysModules.sysPlayer.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;

import sysModules.sysPlayer.notification.NotificationConstants;

/**
 * Lets {@link PlaybackService} host the ExoPlayer of the VidFlow player screen. The screen keeps
 * building its own sources; the service adds the notification with its buttons, the media session,
 * the wake lock and background playback. When the screen goes away while playing, the service
 * takes over the player ("service owned") and releases it when the user closes the notification.
 */
public final class ExternalPlayback {
	private static ExoPlayer player;
	private static String title = "";
	private static String uploader = "";
	private static String art = "";
	private static String key = "";
	private static Runnable closeCallback;
	private static boolean serviceOwned;
	private static Object owner;
	private static boolean launchResumeOffered = true;

	private ExternalPlayback() {}

	public static synchronized void attach(@NonNull Context context, @NonNull ExoPlayer p,
	                                       @Nullable String title, @Nullable String uploader,
	                                       @Nullable String art, @Nullable String key,
	                                       @Nullable Runnable onClose) {
		if (player != null && player != p && serviceOwned) {
			try {
				player.release();
			} catch (Throwable ignored) {
			}
		}
		player = p;
		serviceOwned = false;
		closeCallback = onClose;
		ExternalPlayback.key = key == null ? "" : key;
		setMeta(title, uploader, art);
		send(context);
	}

	public static synchronized void update(@NonNull Context context, @Nullable String title,
	                                       @Nullable String uploader, @Nullable String art) {
		if (player == null) return;
		setMeta(title, uploader, art);
		send(context);
	}

	private static void setMeta(String t, String u, String a) {
		title = t == null ? "" : t;
		uploader = u == null ? "" : u;
		art = a == null ? "" : a;
	}

	private static void send(Context context) {
		try {
			Intent intent = new Intent(context, PlaybackService.class);
			intent.setAction(NotificationConstants.ACTION_HOST_EXTERNAL);
			ContextCompat.startForegroundService(context, intent);
		} catch (Throwable ignored) {
		}
	}

	/** True while this player is audibly running, so it should outlive its screen. */
	public static synchronized boolean keepAlive(@Nullable ExoPlayer p) {
		if (p == null || p != player) return false;
		int state = p.getPlaybackState();
		return p.getPlayWhenReady() && state != Player.STATE_IDLE && state != Player.STATE_ENDED;
	}

	/** The screen is gone but keeps playing: the service releases the player on close. */
	public static synchronized void handOver(@Nullable ExoPlayer p, @Nullable Object screenState) {
		if (p != null && p == player) {
			serviceOwned = true;
			closeCallback = null;
			owner = screenState;
			launchResumeOffered = false;
		}
	}

	/** State the player screen left behind (request, loaded media) so a reopened screen can continue. */
	@Nullable public static synchronized Object owner() { return owner; }

	/** The screen took the player back from the service. */
	public static synchronized void reclaim(@Nullable Runnable onClose) {
		serviceOwned = false;
		owner = null;
		closeCallback = onClose;
	}

	/** True when a player is playing on in the background and no screen shows it. */
	public static synchronized boolean hasBackgroundSession() {
		return player != null && serviceOwned;
	}

	/** Once per background session: should a fresh app launch jump to the player? */
	public static synchronized boolean takeLaunchResume() {
		if (launchResumeOffered || !hasBackgroundSession()) return false;
		launchResumeOffered = true;
		return true;
	}

	/** The screen is done with this player: stop hosting. The caller releases the player. */
	public static synchronized void detach(@NonNull Context context, @Nullable ExoPlayer p) {
		if (p == null || p != player) return;
		clear();
		try {
			context.stopService(new Intent(context, PlaybackService.class));
		} catch (Throwable ignored) {
		}
	}

	static synchronized void clear() {
		owner = null;
		player = null;
		closeCallback = null;
		serviceOwned = false;
	}

	@Nullable public static synchronized ExoPlayer player() { return player; }
	static synchronized String title() { return title; }
	static synchronized String uploader() { return uploader; }
	static synchronized String art() { return art; }
	static synchronized String key() { return key; }
	static synchronized boolean isServiceOwned() { return serviceOwned; }
	@Nullable static synchronized Runnable closeCallback() { return closeCallback; }
	static synchronized boolean active() { return player != null; }
}
