package userInterface.watchVideo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import sysModules.crashedHandler.CrashLogger;
import sysModules.sysPlayer.helper.HistoryRecorder;
import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.service.ExternalPlayback;

/**
 * Owns the ExoPlayer, so rotating the phone does not restart the video.
 * It also does the engine fallback: if the first way of getting a stream fails, the next one
 * is tried before the user ever sees an error.
 */
public class WatchVideoViewModel extends ViewModel {

	final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
	final MutableLiveData<String> status = new MutableLiveData<>("Getting video…");
	final MutableLiveData<String> error = new MutableLiveData<>();
	final MutableLiveData<PlayerSources.Result> loaded = new MutableLiveData<>();
	/** Set when the notification's close button was pressed: the screen should finish. */
	final MutableLiveData<Boolean> closeRequested = new MutableLiveData<>(false);

	ExoPlayer player;
	/** The player this screen left to the service; a reopened screen takes it back. */
	private ExoPlayer handedOver;
	PlayerSources sources;
	PlayRequest request;
	PlayerSources.Result result;

	private Context app;
	private boolean started = false;
	private boolean linkFallbackTried = false;
	private int generation = 0;
	private final ExecutorService io = Executors.newSingleThreadExecutor();
	private final Handler main = new Handler(Looper.getMainLooper());

	/** Call from the main thread. Does nothing if this request is already running (rotation). */
	void start(Context context, PlayRequest req) {
		if (started) return;
		started = true;
		app = context.getApplicationContext();
		request = req;
		sources = new PlayerSources(app);
		createPlayer();
		load(request, 0);
	}

	private void createPlayer() {
		DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
			.setBufferDurationsMs(20_000, 60_000, 1_000, 2_500)
			.setPrioritizeTimeOverSizeThresholds(true)
			.build();
		DefaultRenderersFactory renderers = new DefaultRenderersFactory(app)
			.setEnableDecoderFallback(true);
		player = new ExoPlayer.Builder(app, renderers)
			.setLoadControl(loadControl)
			.setSeekBackIncrementMs(10_000)
			.setSeekForwardIncrementMs(10_000)
			.setHandleAudioBecomingNoisy(true)
			.setAudioAttributes(AudioAttributes.DEFAULT, true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.build();
		player.setSeekParameters(SeekParameters.CLOSEST_SYNC); // snappier seeking
		// Subtitles stay off until the user turns them on.
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
			.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
		player.addListener(vmListener);
	}

	private final Player.Listener vmListener = new Player.Listener() {
			@Override
			public void onPlaybackStateChanged(int state) {
				// A captured web stream that turned out to be audio only: try the page link instead.
				if (state != Player.STATE_READY || request == null || result == null) return;
				if (request.kind != PlayRequest.Kind.STREAM || request.audio || linkFallbackTried) return;
				com.google.android.exoplayer2.Tracks t = null;
				try {
					t = player.getCurrentTracks();
				} catch (Throwable ignored) {
				}
				if (t != null && !t.isEmpty() && !t.containsType(C.TRACK_TYPE_VIDEO)) tryNextEngine();
			}

			@Override
			public void onPlayerError(@NonNull PlaybackException e) {
				CrashLogger.breadcrumb("player error: " + e.getErrorCodeName(), e);
				lastPlayError = e.getErrorCodeName();
				if (tryNextEngine()) return;
				showError("This video could not be played.\n" + (result == null ? "" : result.engine + ": ")
					+ e.getErrorCodeName() + "\n\n(Tap to copy details)");
			}
	};

	/**
	 * Reopened from the notification / launcher while the service keeps playing: take over the same
	 * player, the same request and the same loaded media. Returns false when there is nothing to take.
	 */
	boolean adopt(Context context) {
		if (started) return true;
		Object o = ExternalPlayback.owner();
		if (!(o instanceof WatchVideoViewModel)) return false;
		WatchVideoViewModel old = (WatchVideoViewModel) o;
		if (old.handedOver == null || old.handedOver != ExternalPlayback.player()) return false;
		started = true;
		app = context.getApplicationContext();
		player = old.handedOver;
		player.addListener(vmListener);
		sources = old.sources;
		request = old.request;
		result = old.result;
		linkFallbackTried = old.linkFallbackTried;
		old.handedOver = null;
		ExternalPlayback.reclaim(() -> main.post(() -> closeRequested.setValue(true)));
		loading.setValue(false);
		if (result != null) loaded.setValue(result);
		return true;
	}

	/** Loads (or reloads) in the background. */
	void load(PlayRequest req, int startStep) {
		final int gen = ++generation;
		loading.setValue(true);
		error.setValue(null);
		status.setValue(req.kind == PlayRequest.Kind.LINK ? "Getting video…" : "Opening…");
		io.execute(() -> {
			try {
				PlayerSources.Result r = sources.resolve(req, startStep);
				main.post(() -> {
					if (gen != generation || player == null) return;
					apply(req, r);
				});
			} catch (Throwable e) {
				CrashLogger.breadcrumb("player resolve failed: " + req.url, e);
				main.post(() -> {
					if (gen != generation || player == null) return;
					if (tryStreamFallback()) return;
					String message = e.getMessage() == null ? e.toString() : e.getMessage();
					if (message.length() > 700) message = message.substring(0, 700) + "…";
					showError("Could not play this link.\n\n" + message + "\n\n(Tap to copy details)");
				});
			}
		});
	}

	private void apply(PlayRequest req, PlayerSources.Result r) {
		result = r;
		if (r.title == null || r.title.isEmpty()) r.title = req.title;
		long resume = r.live ? 0 : savedPosition(req.url);
		attachToService(req, r);
		player.setMediaSource(r.source);
		if (resume > 3000) player.seekTo(resume);
		player.prepare();
		player.setPlayWhenReady(true);
		loading.setValue(false);
		loaded.setValue(r);
	}

	/** Gives the player to the playback service: notification, buttons, lock screen, background play. */
	private void attachToService(PlayRequest req, PlayerSources.Result r) {
		try {
			String title = r.title == null || r.title.isEmpty() ? req.title : r.title;
			String key = req.url == null ? "" : req.url;
			ExternalPlayback.attach(app, player, title, r.engine == null ? "" : r.engine,
				r.thumbnail == null ? "" : r.thumbnail, key,
				() -> main.post(() -> closeRequested.setValue(true)));
		} catch (Throwable e) {
			CrashLogger.breadcrumb("service attach failed", e);
		}
	}

	/**
	 * Second chance: YouTube goes from NewPipe to yt-dlp, a captured web stream goes to the page
	 * link (yt-dlp). Returns false when nothing is left to try.
	 */
	private boolean tryNextEngine() {
		if (request == null) return false;
		if (request.kind == PlayRequest.Kind.LINK && result != null && result.step + 1 < result.steps) {
			status.setValue("Switching engine…");
			final int next = result.step + 1;
			lastPlayError = result.engine + " → " + lastPlayError;
			load(request, next);
			return true;
		}
		return tryStreamFallback();
	}

	/** A captured web stream that fails goes to the page link, where the engine ladder takes over. */
	private boolean tryStreamFallback() {
		if (request == null) return false;
		if (request.kind == PlayRequest.Kind.STREAM && !linkFallbackTried
			&& request.pageUrl.startsWith("http")) {
			linkFallbackTried = true;
			status.setValue("Trying the page link…");
			request = PlayRequest.link(request.pageUrl);
			request.title = request.title.isEmpty() ? request.url : request.title;
			load(request, 0);
			return true;
		}
		return false;
	}

	private String lastPlayError = "";

	private void showError(String message) {
		loading.setValue(false);
		error.setValue(message);
	}

	/** Retry button: start the whole resolve again. */
	void retry() {
		linkFallbackTried = false;
		if (player != null) player.stop();
		load(request, 0);
	}

	/** Re-resolve a YouTube video after quality / audio language changed, keeping the position. */
	void swapYoutubeSource() {
		if (result == null || result.resolver == null || player == null) return;
		final PlayerSources.Result current = result;
		final long position = player.getCurrentPosition();
		final boolean play = player.getPlayWhenReady();
		io.execute(() -> {
			try {
				MediaSource next = sources.reresolve(current);
				main.post(() -> {
					if (player == null || next == null) return;
					current.tag = sysModules.sysPlayer.mediaitem.MediaItemTag.from(next.getMediaItem());
					player.setMediaSource(next, position);
					player.prepare();
					player.setPlayWhenReady(play);
				});
			} catch (Throwable e) {
				CrashLogger.breadcrumb("quality switch failed", e);
			}
		});
	}

	// ── Resume position (local only, never uploaded) ─────────────────

	private PlayQueueItem item(String url) {
		return new PlayQueueItem(android.net.Uri.parse(url == null ? "" : url),
			request == null || request.title == null ? "" : request.title, null, 0);
	}

	private long savedPosition(String url) {
		try {
			long pos = HistoryRecorder.getRecoveryPosition(app, item(url));
			return pos == PlayQueueItem.RECOVERY_UNSET ? 0 : pos;
		} catch (Throwable e) {
			return 0;
		}
	}

	void savePosition() {
		try {
			if (player == null || request == null || result == null || result.live) return;
			long duration = player.getDuration();
			long position = player.getCurrentPosition();
			if (duration == C.TIME_UNSET || duration < 60_000) return;
			HistoryRecorder.recordProgress(app, item(request.url), position, duration);
		} catch (Throwable ignored) {
		}
	}

	@Override
	protected void onCleared() {
		savePosition();
		generation++;
		if (player != null) {
			if (ExternalPlayback.keepAlive(player)) {
				player.removeListener(vmListener);
				handedOver = player;
				ExternalPlayback.handOver(player, this); // keeps playing in the background
			} else {
				ExternalPlayback.detach(app, player);
				player.release();
			}
			player = null;
		}
		io.shutdownNow();
		super.onCleared();
	}
}
