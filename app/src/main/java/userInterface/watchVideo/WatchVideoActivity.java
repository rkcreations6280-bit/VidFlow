package userInterface.watchVideo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.ScaleGestureDetector;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.TimeBar;
import com.nextgen.R;
import com.nextgen.databinding.ActivityPlayerV2Binding;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import coreUtils.base.BaseActivity;
import coreUtils.library.views.AppDialog;
import coreUtils.library.views.AppToast;

/**
 * The video / audio player screen. Open it with {@link PlayRequest#start}:
 * downloaded files, files picked on the phone, pasted links and streams captured in the browser
 * all come through here.
 */
public class WatchVideoActivity extends BaseActivity<ActivityPlayerV2Binding> {

	private static final long HIDE_AFTER_MS = 3500;
	private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

	private final Handler main = new Handler(Looper.getMainLooper());
	private WatchVideoViewModel vm;
	private PlayRequest request;
	private boolean controlsShown = true;
	private boolean locked = false;
	private boolean scrubbing = false;
	private int orientationMode = 0; // 0 auto, 1 landscape, 2 portrait
	private int resizeIndex = 0;

	// gestures
	private GestureDetector detector;
	private int gestureMode = 0; // 0 none, 1 seek, 2 brightness, 3 volume
	private long gestureStartPosition;
	private long gestureTarget;
	private float gestureStartValue;
	private boolean boosted = false;
	private float speedBeforeBoost = 1f;
	private AudioManager audio;

	// zoom / pan, sleep timer, misc
	private ScaleGestureDetector scaleDetector;
	private float zoom = 1f;
	private float panX = 0f;
	private float panY = 0f;
	private boolean scalingNow = false;
	private boolean showRemaining = false;
	private boolean sliderHeld = false;
	private int sleepMinutes = 0;
	private int lastVolumeStep = -1;
	private float brightness = 0.5f;
	private static final float MAX_ZOOM = 4f;

	@Override
	protected boolean shouldLockOrientation() {
		return false;
	}

	@Override
	protected ActivityPlayerV2Binding inflateBinding(LayoutInflater inflater) {
		return ActivityPlayerV2Binding.inflate(inflater);
	}

	@Override
	protected void onLoadedLayout() {
		final boolean resume = PlayRequest.isResume(getIntent());
		request = PlayRequest.from(getIntent());
		if (request == null && !resume) request = PlayRequest.fromView(this, getIntent());
		if (request == null && !resume) {
			coreUtils.library.views.AppToast.show(this, "Nothing to play");
			finish();
			return;
		}
		audio = (AudioManager) getSystemService(AUDIO_SERVICE);
		goImmersive();
		applyInsets();
		if (!isNotificationPermissionAllowed()) requestForNotificationPermission();

		vm = new ViewModelProvider(this).get(WatchVideoViewModel.class);
		if (resume && !vm.adopt(this)) {
			// Nothing is playing in the background any more.
			finish();
			return;
		}
		if (!resume) vm.start(this, request);
		request = vm.request == null ? request : vm.request;
		if (request == null) {
			finish();
			return;
		}

		binding.tvTitle.setText(request.title.isEmpty() ? "" : request.title);
		wireControls();
		wireSliders();
		wireGestures();
		observe();
		showControls(true);
	}

	// ── Window ───────────────────────────────────────────────────────

	private void goImmersive() {
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		// The video may run under the notch, all the way to the edge of the screen.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams lp = getWindow().getAttributes();
			lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
				? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
				: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(lp);
		}
		WindowInsetsControllerCompat controller =
			new WindowInsetsControllerCompat(getWindow(), binding.getRoot());
		controller.hide(WindowInsetsCompat.Type.systemBars());
		controller.setSystemBarsBehavior(
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
	}

	/** Buttons and panels keep away from the notch; the video itself ignores it. */
	private void applyInsets() {
		final float d = getResources().getDisplayMetrics().density;
		final int extra = (int) (4 * d);
		final int topBase = (int) (6 * d);
		final int bottomBase = (int) (10 * d);
		ViewCompat.setOnApplyWindowInsetsListener(binding.playerRoot, (v, insets) -> {
			Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout()
				| WindowInsetsCompat.Type.systemBars());
			// Left / right keep clear of the notch; the panels themselves touch the top and bottom edge.
			binding.safeArea.setPadding(cut.left + extra, 0, cut.right + extra, 0);
			binding.topBar.setPadding(binding.topBar.getPaddingLeft(), topBase + cut.top,
				binding.topBar.getPaddingRight(), binding.topBar.getPaddingBottom());
			binding.bottomBar.setPadding(binding.bottomBar.getPaddingLeft(), binding.bottomBar.getPaddingTop(),
				binding.bottomBar.getPaddingRight(), bottomBase + cut.bottom);
			android.widget.FrameLayout.LayoutParams zoomLp =
				(android.widget.FrameLayout.LayoutParams) binding.btnZoomReset.getLayoutParams();
			zoomLp.topMargin = (int) (78 * d) + cut.top;
			binding.btnZoomReset.setLayoutParams(zoomLp);
			android.widget.FrameLayout.LayoutParams flashLp =
				(android.widget.FrameLayout.LayoutParams) binding.tvGesture.getLayoutParams();
			flashLp.topMargin = (int) (92 * d) + cut.top;
			binding.tvGesture.setLayoutParams(flashLp);
			return insets;
		});
		ViewCompat.requestApplyInsets(binding.playerRoot);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && binding != null) goImmersive();
	}

	// ── Lifecycle ────────────────────────────────────────────────────

	@Override
	protected void onStart() {
		super.onStart();
		if (vm == null || vm.player == null) return;
		vm.player.setTrackSelectionParameters(vm.player.getTrackSelectionParameters().buildUpon()
			.setTrackTypeDisabled(com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO, false).build());
		binding.playerView.setPlayer(vm.player);
		vm.player.addListener(playerListener);
		syncPlayState();
		refreshTrackButtons();
		main.post(ticker);
	}

	@Override
	protected void onStop() {
		main.removeCallbacks(ticker);
		main.removeCallbacks(hideControls);
		main.removeCallbacks(hideBrightnessSlider);
		main.removeCallbacks(hideVolumeSlider);
		if (vm != null && vm.player != null) {
			vm.player.removeListener(playerListener);
			// Background play on (default): Home, screen off, Back and swipe-away all keep playing
			// through the playback service, like a music player. Off: leaving the screen pauses.
			if (!isChangingConfigurations() && !backgroundPlayOn()) vm.player.pause();
			if (!isChangingConfigurations()) {
				vm.player.setTrackSelectionParameters(vm.player.getTrackSelectionParameters().buildUpon()
					.setTrackTypeDisabled(com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO, true).build());
			}
			vm.savePosition();
			binding.playerView.setPlayer(null);
		}
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		super.onStop();
	}

	// ── Observers ────────────────────────────────────────────────────

	private void observe() {
		vm.status.observe(this, text -> binding.tvStatus.setText(text));
		vm.loading.observe(this, on -> {
			boolean loadingNow = Boolean.TRUE.equals(on);
			binding.loadingBox.setVisibility(loadingNow ? View.VISIBLE : View.GONE);
			binding.tvStatus.setVisibility(loadingNow ? View.VISIBLE : View.GONE);
			if (loadingNow) binding.errorBox.setVisibility(View.GONE);
		});
		vm.error.observe(this, message -> {
			if (message == null) {
				binding.errorBox.setVisibility(View.GONE);
				return;
			}
			binding.tvError.setText(message);
			binding.errorBox.setVisibility(View.VISIBLE);
			showControls(true);
		});
		vm.loaded.observe(this, result -> {
			if (result != null) onLoaded(result);
		});
		vm.closeRequested.observe(this, close -> {
			if (Boolean.TRUE.equals(close)) finish();
		});
		binding.tvError.setOnClickListener(v -> {
			try {
				ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				cm.setPrimaryClip(ClipData.newPlainText("details", String.valueOf(binding.tvError.getText())));
				AppToast.show(this, "Details copied");
			} catch (Throwable ignored) {
			}
		});
		binding.btnRetry.setOnClickListener(v -> vm.retry());
		binding.btnCloseError.setOnClickListener(v -> finish());
	}

	private void onLoaded(PlayerSources.Result result) {
		if (!result.title.isEmpty()) binding.tvTitle.setText(result.title);
		binding.tvEngine.setVisibility(View.VISIBLE);
		binding.tvEngine.setText(result.engine);
		binding.audioBox.setVisibility(result.audioOnly ? View.VISIBLE : View.GONE);
		if (result.audioOnly && !result.thumbnail.isEmpty()) {
			binding.imgArtIcon.setVisibility(View.GONE);
			try {
				Glide.with(this).load(result.thumbnail).into(binding.imgArt);
			} catch (Throwable ignored) {
			}
		}
		refreshTrackButtons();
	}

	// ── Player events ────────────────────────────────────────────────

	private final Player.Listener playerListener = new Player.Listener() {
		@Override
		public void onIsPlayingChanged(boolean isPlaying) {
			syncPlayState();
		}

		@Override
		public void onPlaybackStateChanged(int state) {
			syncPlayState();
			if (state == Player.STATE_ENDED) showControls(true);
		}

		@Override
		public void onTracksChanged(@NonNull Tracks tracks) {
			refreshTrackButtons();
		}

		@Override
		public void onPlaybackParametersChanged(@NonNull PlaybackParameters parameters) {
			binding.btnSpeed.setText(speedLabel(parameters.speed));
		}
	};

	private void syncPlayState() {
		if (vm == null || vm.player == null) return;
		Player player = vm.player;
		boolean playing = player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED
			&& player.getPlaybackState() != Player.STATE_IDLE;
		binding.btnPlayPause.setImageResource(playing ? R.drawable.pl_ic_pause : R.drawable.pl_ic_play);
		boolean buffering = player.getPlaybackState() == Player.STATE_BUFFERING
			&& Boolean.FALSE.equals(vm.loading.getValue());
		if (Boolean.FALSE.equals(vm.loading.getValue())) {
			binding.loadingBox.setVisibility(buffering ? View.VISIBLE : View.GONE);
			binding.tvStatus.setVisibility(View.GONE);
		}
		boolean video = vm.result == null || !vm.result.audioOnly;
		if (playing && video) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (playing) scheduleHide();
		else main.removeCallbacks(hideControls);
	}

	private final Runnable ticker = new Runnable() {
		@Override
		public void run() {
			if (vm != null && vm.player != null && binding != null) {
				Player player = vm.player;
				long duration = player.getDuration();
				boolean known = duration != C.TIME_UNSET && duration > 0;
				if (!scrubbing) {
					binding.timeBar.setDuration(known ? duration : C.TIME_UNSET);
					binding.timeBar.setPosition(player.getCurrentPosition());
					binding.tvPosition.setText(time(player.getCurrentPosition()));
				}
				binding.timeBar.setBufferedPosition(player.getBufferedPosition());
				if (!known) binding.tvDuration.setText("LIVE");
				else if (showRemaining) binding.tvDuration.setText("-" + time(Math.max(0, duration - player.getCurrentPosition())));
				else binding.tvDuration.setText(time(duration));
				syncVolumeSlider();
			}
			main.postDelayed(this, 500);
		}
	};

	private static String time(long ms) {
		if (ms < 0) ms = 0;
		long s = ms / 1000;
		return s >= 3600
			? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
			: String.format(Locale.US, "%d:%02d", s / 60, s % 60);
	}

	// ── Controls ─────────────────────────────────────────────────────

	private final Runnable hideControls = () -> {
		if (vm != null && vm.player != null && vm.player.isPlaying() && !scrubbing) showControls(false);
	};

	private void scheduleHide() {
		main.removeCallbacks(hideControls);
		main.postDelayed(hideControls, HIDE_AFTER_MS);
	}

	private void showControls(boolean show) {
		controlsShown = show && !locked;
		binding.controls.animate().cancel();
		if (controlsShown) {
			binding.controls.setVisibility(View.VISIBLE);
			binding.controls.animate().alpha(1f).setDuration(150).start();
			scheduleHide();
		} else {
			binding.controls.animate().alpha(0f).setDuration(200)
				.withEndAction(() -> {
					if (!controlsShown) binding.controls.setVisibility(View.GONE);
				}).start();
		}
	}

	// ── Volume / brightness sliders: separate group, shown only while in use ──

	private final Runnable hideBrightnessSlider = () -> fadeSlider(binding.sliderBrightness, false, -1);
	private final Runnable hideVolumeSlider = () -> fadeSlider(binding.sliderVolume, false, 1);

	private void fadeSlider(PlayerSlider slider, boolean show, int side) {
		float shift = 18 * getResources().getDisplayMetrics().density * side;
		slider.animate().cancel();
		if (show) {
			if (slider.getVisibility() != View.VISIBLE) {
				slider.setAlpha(0f);
				slider.setTranslationX(shift);
				slider.setScaleX(0.9f);
				slider.setScaleY(0.9f);
				slider.setVisibility(View.VISIBLE);
			}
			slider.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(160).start();
		} else {
			slider.animate().alpha(0f).translationX(shift).setDuration(200)
				.withEndAction(() -> slider.setVisibility(View.GONE)).start();
		}
	}

	private void revealBrightness() {
		fadeSlider(binding.sliderBrightness, true, -1);
		main.removeCallbacks(hideBrightnessSlider);
		if (!sliderHeld) main.postDelayed(hideBrightnessSlider, 1100);
	}

	private void revealVolume() {
		fadeSlider(binding.sliderVolume, true, 1);
		main.removeCallbacks(hideVolumeSlider);
		if (!sliderHeld) main.postDelayed(hideVolumeSlider, 1100);
	}

	private void wireControls() {
		binding.btnBack.setOnClickListener(v -> finish());
		binding.btnPlayPause.setOnClickListener(v -> {
			togglePlay();
			scheduleHide();
		});
		binding.btnRewind.setOnClickListener(v -> {
			seekBy(-10_000);
			scheduleHide();
		});
		binding.btnForward.setOnClickListener(v -> {
			seekBy(10_000);
			scheduleHide();
		});
		binding.btnLock.setOnClickListener(v -> setLocked(true));
		binding.btnUnlock.setOnClickListener(v -> setLocked(false));
		binding.btnSpeed.setOnClickListener(v -> chooseSpeed());
		binding.btnQuality.setOnClickListener(v -> chooseQuality());
		binding.btnAudio.setOnClickListener(v -> chooseAudio());
		binding.btnSubtitles.setOnClickListener(v -> chooseSubtitles());
		binding.btnAspect.setOnClickListener(v -> cycleResize());
		binding.btnRotate.setOnClickListener(v -> cycleOrientation());
		binding.btnMore.setOnClickListener(v -> showMore());
		binding.btnZoomReset.setOnClickListener(v -> resetZoom(true));
		binding.tvDuration.setOnClickListener(v -> {
			showRemaining = !showRemaining;
			scheduleHide();
		});

		binding.timeBar.addListener(new TimeBar.OnScrubListener() {
			@Override
			public void onScrubStart(@NonNull TimeBar timeBar, long position) {
				scrubbing = true;
				main.removeCallbacks(hideControls);
			}

			@Override
			public void onScrubMove(@NonNull TimeBar timeBar, long position) {
				binding.tvPosition.setText(time(position));
			}

			@Override
			public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
				scrubbing = false;
				if (!canceled && vm != null && vm.player != null) vm.player.seekTo(position);
				scheduleHide();
			}
		});
	}

	private void togglePlay() {
		if (vm == null || vm.player == null) return;
		Player player = vm.player;
		if (player.getPlaybackState() == Player.STATE_ENDED) {
			player.seekTo(0);
			player.setPlayWhenReady(true);
		} else {
			player.setPlayWhenReady(!player.getPlayWhenReady());
		}
	}

	private void seekBy(long delta) {
		if (vm == null || vm.player == null) return;
		Player player = vm.player;
		long target = player.getCurrentPosition() + delta;
		long duration = player.getDuration();
		if (duration != C.TIME_UNSET) target = Math.min(target, duration);
		player.seekTo(Math.max(0, target));
	}

	private void setLocked(boolean on) {
		locked = on;
		binding.btnUnlock.setVisibility(on ? View.VISIBLE : View.GONE);
		if (on) {
			showControls(false);
			main.postDelayed(() -> {
				if (locked) binding.btnUnlock.setVisibility(View.GONE);
			}, 2500);
		} else {
			showControls(true);
		}
	}

	private void cycleResize() {
		int[] modes = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL,
			AspectRatioFrameLayout.RESIZE_MODE_ZOOM};
		String[] names = {"Fit", "Stretch", "Fill screen"};
		resizeIndex = (resizeIndex + 1) % modes.length;
		binding.playerView.setResizeMode(modes[resizeIndex]);
		resetZoom(false);
		flash(names[resizeIndex]);
		scheduleHide();
	}

	private void cycleOrientation() {
		orientationMode = (orientationMode + 1) % 3;
		switch (orientationMode) {
			case 1:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				flash("Landscape");
				break;
			case 2:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				flash("Portrait");
				break;
			default:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				flash("Auto rotate");
		}
	}

	private final Runnable hideFlash = () -> binding.tvGesture.setVisibility(View.GONE);

	private void flash(String text) {
		binding.tvGesture.setText(text);
		binding.tvGesture.setVisibility(View.VISIBLE);
		main.removeCallbacks(hideFlash);
		main.postDelayed(hideFlash, 900);
	}

	// ── Touch gestures ───────────────────────────────────────────────

	private void wireGestures() {
		detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(@NonNull MotionEvent e) {
				return true;
			}

			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				if (locked) {
					binding.btnUnlock.setVisibility(View.VISIBLE);
					main.postDelayed(() -> {
						if (locked) binding.btnUnlock.setVisibility(View.GONE);
					}, 2500);
				} else {
					showControls(!controlsShown);
				}
				return true;
			}

			@Override
			public boolean onDoubleTap(@NonNull MotionEvent e) {
				if (locked) return true;
				float third = binding.gestureLayer.getWidth() / 3f;
				if (e.getX() < third) {
					seekBy(-10_000);
					pop(binding.tvSeekLeft);
				} else if (e.getX() > third * 2) {
					seekBy(10_000);
					pop(binding.tvSeekRight);
				} else {
					togglePlay();
				}
				return true;
			}

			@Override
			public void onLongPress(@NonNull MotionEvent e) {
				if (locked || vm == null || vm.player == null || !vm.player.isPlaying()) return;
				boosted = true;
				speedBeforeBoost = vm.player.getPlaybackParameters().speed;
				vm.player.setPlaybackSpeed(2f);
				flash("2x speed");
			}

			@Override
			public boolean onScroll(MotionEvent down, @NonNull MotionEvent move, float dx, float dy) {
				if (locked || down == null || vm == null || vm.player == null) return false;
				float totalX = move.getX() - down.getX();
				float totalY = move.getY() - down.getY();
				int width = Math.max(1, binding.gestureLayer.getWidth());
				int height = Math.max(1, binding.gestureLayer.getHeight());
				if (gestureMode == 0) {
					if (Math.abs(totalX) < 24 && Math.abs(totalY) < 24) return true;
					if (Math.abs(totalX) > Math.abs(totalY)) {
						long duration = vm.player.getDuration();
						if (duration == C.TIME_UNSET || duration <= 0) return true; // live: no seeking
						gestureMode = 1;
						gestureStartPosition = vm.player.getCurrentPosition();
					} else if (down.getX() < width / 2f) {
						gestureMode = 2;
						gestureStartValue = currentBrightness();
					} else {
						gestureMode = 3;
						gestureStartValue = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
							/ (float) audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
					}
				}
				if (gestureMode == 1) {
					long duration = vm.player.getDuration();
					gestureTarget = gestureStartPosition + (long) (totalX / width * 120_000f);
					gestureTarget = Math.max(0, Math.min(duration, gestureTarget));
					long diff = (gestureTarget - gestureStartPosition) / 1000;
					flash(time(gestureTarget) + "  (" + (diff >= 0 ? "+" : "") + diff + "s)");
				} else if (gestureMode == 2) {
					setBrightness(gestureStartValue - totalY / height * 1.2f);
					revealBrightness();
				} else if (gestureMode == 3) {
					setVolume(gestureStartValue - totalY / height * 1.2f);
					revealVolume();
				}
				return true;
			}
		});
		scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			private float lastX;
			private float lastY;

			@Override
			public boolean onScaleBegin(@NonNull ScaleGestureDetector d) {
				if (locked) return false;
				scalingNow = true;
				gestureMode = 0;
				lastX = d.getFocusX();
				lastY = d.getFocusY();
				MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
				detector.onTouchEvent(cancel);
				cancel.recycle();
				return true;
			}

			@Override
			public boolean onScale(@NonNull ScaleGestureDetector d) {
				zoom = Math.max(1f, Math.min(MAX_ZOOM, zoom * d.getScaleFactor()));
				panX += d.getFocusX() - lastX;
				panY += d.getFocusY() - lastY;
				lastX = d.getFocusX();
				lastY = d.getFocusY();
				applyZoom();
				flash("Zoom  " + Math.round(zoom * 100) + "%");
				return true;
			}
		});
		binding.gestureLayer.setOnTouchListener((v, event) -> {
			scaleDetector.onTouchEvent(event);
			if (!scalingNow && event.getPointerCount() == 1) detector.onTouchEvent(event);
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) scalingNow = false;
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				if (gestureMode == 1 && vm != null && vm.player != null && action == MotionEvent.ACTION_UP) {
					vm.player.seekTo(gestureTarget);
				}
				gestureMode = 0;
				if (boosted && vm != null && vm.player != null) {
					vm.player.setPlaybackSpeed(speedBeforeBoost);
					boosted = false;
				}
				main.removeCallbacks(hideFlash);
				main.postDelayed(hideFlash, 400);
			}
			return true;
		});
	}

	private void pop(View view) {
		view.animate().cancel();
		view.setAlpha(1f);
		view.setVisibility(View.VISIBLE);
		view.animate().alpha(0f).setStartDelay(450).setDuration(250)
			.withEndAction(() -> view.setVisibility(View.GONE)).start();
	}

	// ── Zoom, brightness, volume ─────────────────────────────────────

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private void applyZoom() {
		View v = binding.playerView;
		float maxX = (zoom - 1f) * v.getWidth() / 2f;
		float maxY = (zoom - 1f) * v.getHeight() / 2f;
		panX = clamp(panX, -maxX, maxX);
		panY = clamp(panY, -maxY, maxY);
		v.setScaleX(zoom);
		v.setScaleY(zoom);
		v.setTranslationX(panX);
		v.setTranslationY(panY);
		boolean zoomed = zoom > 1.02f;
		binding.btnZoomReset.setVisibility(zoomed ? View.VISIBLE : View.GONE);
		binding.btnZoomReset.setText(Math.round(zoom * 100) + "%");
	}

	private void resetZoom(boolean animate) {
		zoom = 1f;
		panX = 0f;
		panY = 0f;
		binding.btnZoomReset.setVisibility(View.GONE);
		if (animate) {
			binding.playerView.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
				.setDuration(180).start();
			flash("Zoom reset");
		} else {
			binding.playerView.setScaleX(1f);
			binding.playerView.setScaleY(1f);
			binding.playerView.setTranslationX(0f);
			binding.playerView.setTranslationY(0f);
		}
	}

	private float currentBrightness() {
		float set = getWindow().getAttributes().screenBrightness;
		if (set >= 0) return set;
		try {
			return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
		} catch (Throwable e) {
			return 0.5f;
		}
	}

	private void setBrightness(float value) {
		brightness = clamp(value, 0.02f, 1f);
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = brightness;
		getWindow().setAttributes(lp);
		binding.sliderBrightness.setValue(brightness);
	}

	private float currentVolume() {
		int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
		return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
	}

	private int volumeLevel = -1; // 0 off, 1 low, 2 high

	private void setVolume(float value) {
		float v = clamp(value, 0f, 1f);
		int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
		lastVolumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		binding.sliderVolume.setValue(v);
		updateVolumeIcon(v);
	}

	private void updateVolumeIcon(float v) {
		int level = v <= 0.001f ? 0 : v < 0.5f ? 1 : 2;
		if (level != volumeLevel) {
			boolean first = volumeLevel < 0;
			volumeLevel = level;
			int icon = level == 0 ? R.drawable.pl_ic_volume_off
				: level == 1 ? R.drawable.pl_ic_volume_low : R.drawable.pl_ic_volume;
			if (first) binding.sliderVolume.setIcon(icon); else binding.sliderVolume.swapIcon(icon);
		}
	}

	/** Keeps the volume slider in step with the phone's volume keys, and shows it when they are used. */
	private void syncVolumeSlider() {
		if (audio == null) return;
		int step = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		if (step != lastVolumeStep) {
			lastVolumeStep = step;
			float v = currentVolume();
			binding.sliderVolume.setValue(v);
			updateVolumeIcon(v);
			revealVolume();
		}
	}

	private void wireSliders() {
		binding.sliderBrightness.setIcon(R.drawable.pl_ic_brightness);
		binding.sliderBrightness.setRotateIcon(true);
		binding.sliderBrightness.setValue(currentBrightness());
		float volume = currentVolume();
		binding.sliderVolume.setValue(volume);
		updateVolumeIcon(volume);
		lastVolumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		binding.sliderBrightness.setListener(new PlayerSlider.Listener() {
			@Override
			public void onChange(float value) {
				setBrightness(value);
			}

			@Override
			public void onTouch(boolean down) {
				sliderHeld = down;
				if (down) main.removeCallbacks(hideBrightnessSlider); else revealBrightness();
			}
		});
		binding.sliderVolume.setListener(new PlayerSlider.Listener() {
			@Override
			public void onChange(float value) {
				setVolume(value);
			}

			@Override
			public void onTouch(boolean down) {
				sliderHeld = down;
				if (down) main.removeCallbacks(hideVolumeSlider); else revealVolume();
			}
		});
	}

	// ── More menu ────────────────────────────────────────────────────

	private final Runnable sleepRunnable = () -> {
		if (vm != null && vm.player != null) vm.player.pause();
		sleepMinutes = 0;
		flash("Sleep timer: stopped");
	};

	private void showMore() {
		if (vm == null || vm.player == null) return;
		final List<String> labels = new ArrayList<>();
		final List<Runnable> actions = new ArrayList<>();
		boolean repeat = vm.player.getRepeatMode() == Player.REPEAT_MODE_ONE;
		labels.add("Repeat: " + (repeat ? "On" : "Off"));
		actions.add(() -> {
			boolean on = vm.player.getRepeatMode() != Player.REPEAT_MODE_ONE;
			vm.player.setRepeatMode(on ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
			flash(on ? "Repeat on" : "Repeat off");
		});
		labels.add("Sleep timer: " + (sleepMinutes > 0 ? sleepMinutes + " min" : "Off"));
		actions.add(this::chooseSleep);
		labels.add("Background play: " + (backgroundPlayOn() ? "On" : "Off"));
		actions.add(() -> {
			boolean on = !backgroundPlayOn();
			setBackgroundPlay(on);
			flash(on ? "Keeps playing when you leave" : "Pauses when you leave");
		});
		labels.add("Listen in background");
		actions.add(() -> {
			setBackgroundPlay(true);
			vm.player.setPlayWhenReady(true);
			moveTaskToBack(true);
		});
		labels.add("Video info");
		actions.add(this::showInfo);
		final String link = shareableLink();
		if (link != null) {
			labels.add("Copy link");
			actions.add(() -> {
				try {
					ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
					cm.setPrimaryClip(ClipData.newPlainText("link", link));
					AppToast.show(this, "Link copied");
				} catch (Throwable ignored) {
				}
			});
		}
		main.removeCallbacks(hideControls);
		PlayerSheets.choose(this, "More", labels, -1, i -> {
			actions.get(i).run();
			scheduleHide();
		});
	}

	private String shareableLink() {
		if (request == null) return null;
		String page = request.pageUrl == null ? "" : request.pageUrl;
		if (page.startsWith("http")) return page;
		return request.url != null && request.url.startsWith("http") ? request.url : null;
	}

	private void chooseSleep() {
		final int[] minutes = {0, 10, 15, 30, 45, 60, -1};
		List<String> labels = new ArrayList<>();
		labels.add("Off");
		for (int i = 1; i < minutes.length - 1; i++) labels.add(minutes[i] + " minutes");
		labels.add("At the end of this video");
		int selected = 0;
		for (int i = 0; i < minutes.length; i++) if (minutes[i] == sleepMinutes) selected = i;
		PlayerSheets.choose(this, "Sleep timer", labels, selected, i -> {
			main.removeCallbacks(sleepRunnable);
			int m = minutes[i];
			if (m > 0) {
				sleepMinutes = m;
				main.postDelayed(sleepRunnable, m * 60_000L);
				flash("Sleep timer: " + m + " min");
			} else if (m == 0) {
				sleepMinutes = 0;
				flash("Sleep timer off");
			} else {
				sleepMinutes = 0;
				if (vm != null && vm.player != null) vm.player.setRepeatMode(Player.REPEAT_MODE_OFF);
				flash("Stops at the end");
			}
		});
	}

	private static String mbps(int bitrate) {
		if (bitrate == Format.NO_VALUE || bitrate <= 0) return null;
		return bitrate >= 1_000_000
			? String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000f)
			: (bitrate / 1000) + " kbps";
	}

	private static String codecName(Format f) {
		String mime = f.sampleMimeType == null ? "" : f.sampleMimeType;
		int slash = mime.indexOf('/');
		String name = slash >= 0 ? mime.substring(slash + 1) : mime;
		if (f.codecs != null && !f.codecs.isEmpty()) name = f.codecs.split(",")[0];
		return name.toUpperCase(Locale.US);
	}

	private void showInfo() {
		if (vm == null || vm.player == null) return;
		StringBuilder sb = new StringBuilder();
		if (vm.result != null && vm.result.engine != null && !vm.result.engine.isEmpty()) {
			sb.append("Source:  ").append(vm.result.engine).append('\n');
		}
		Format v = vm.player.getVideoFormat();
		if (v != null && v.width > 0) {
			sb.append("Video:  ").append(v.width).append(" × ").append(v.height);
			if (v.frameRate > 0) sb.append("  •  ").append(Math.round(v.frameRate)).append(" fps");
			sb.append("\n          ").append(codecName(v));
			String rate = mbps(v.bitrate);
			if (rate != null) sb.append("  •  ").append(rate);
			sb.append('\n');
		}
		Format a = vm.player.getAudioFormat();
		if (a != null) {
			sb.append("Audio:  ").append(codecName(a));
			if (a.channelCount > 0) sb.append("  •  ").append(a.channelCount).append(" ch");
			if (a.sampleRate > 0) sb.append("  •  ").append(a.sampleRate / 1000f).append(" kHz");
			String rate = mbps(a.bitrate);
			if (rate != null) sb.append("  •  ").append(rate);
			sb.append('\n');
		}
		long duration = vm.player.getDuration();
		if (duration != C.TIME_UNSET && duration > 0) sb.append("Length:  ").append(time(duration));
		if (sb.length() == 0) sb.append("No details yet.");
		new AppDialog.Builder(this).setTitle("Video info").setMessage(sb.toString().trim())
			.setPositiveButton("Close", null).show();
	}

	private boolean backgroundPlayOn() {
		try {
			return getSharedPreferences("vidflow_player", MODE_PRIVATE).getBoolean("bg_play", true);
		} catch (Throwable e) {
			return true;
		}
	}

	private void setBackgroundPlay(boolean on) {
		try {
			getSharedPreferences("vidflow_player", MODE_PRIVATE).edit().putBoolean("bg_play", on).apply();
		} catch (Throwable ignored) {
		}
	}

	@Override
	protected void onDestroy() {
		main.removeCallbacksAndMessages(null);
		super.onDestroy();
	}

	// ── Speed / audio / subtitles / quality menus ────────────────────

	private static String speedLabel(float speed) {
		return (speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed)) + "x";
	}

	private void chooseSpeed() {
		if (vm == null || vm.player == null) return;
		List<String> labels = new ArrayList<>();
		int selected = 2;
		float current = vm.player.getPlaybackParameters().speed;
		for (int i = 0; i < SPEEDS.length; i++) {
			labels.add(speedLabel(SPEEDS[i]));
			if (Math.abs(SPEEDS[i] - current) < 0.01f) selected = i;
		}
		PlayerSheets.choose(this, "Playback speed", labels, selected,
			i -> vm.player.setPlaybackSpeed(SPEEDS[i]));
	}

	private static final class Entry {
		final TrackGroup group;
		final int index;
		final String label;
		final boolean selected;

		Entry(TrackGroup group, int index, String label, boolean selected) {
			this.group = group;
			this.index = index;
			this.label = label;
			this.selected = selected;
		}
	}

	private List<Entry> entries(int type) {
		List<Entry> list = new ArrayList<>();
		if (vm == null || vm.player == null) return list;
		int n = 0;
		for (Tracks.Group group : vm.player.getCurrentTracks().getGroups()) {
			if (group.getType() != type) continue;
			for (int i = 0; i < group.length; i++) {
				if (!group.isTrackSupported(i)) continue;
				n++;
				list.add(new Entry(group.getMediaTrackGroup(), i, labelOf(group.getTrackFormat(i), type, n),
					group.isTrackSelected(i)));
			}
		}
		return list;
	}

	private static String labelOf(Format f, int type, int n) {
		if (type == C.TRACK_TYPE_VIDEO) {
			String text = f.height > 0 ? f.height + "p" : "Video " + n;
			if (f.frameRate > 30) text += Math.round(f.frameRate);
			return text;
		}
		String text = f.label;
		if (text == null || text.isEmpty()) {
			if (f.language != null && !f.language.isEmpty() && !"und".equals(f.language)) {
				text = new Locale(f.language).getDisplayLanguage();
			} else {
				text = (type == C.TRACK_TYPE_AUDIO ? "Audio " : "Subtitles ") + n;
			}
		}
		if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 2) text += "  (" + f.channelCount + " ch)";
		return text;
	}

	private boolean isYoutube() {
		return vm != null && vm.result != null && vm.result.resolver != null && vm.result.tag != null;
	}

	private void refreshTrackButtons() {
		if (vm == null || binding == null) return;
		boolean yt = isYoutube();
		int audioCount = yt ? PlayerSources.audioLanguages(vm.result).size() : entries(C.TRACK_TYPE_AUDIO).size();
		binding.btnAudio.setVisibility(audioCount > 1 ? View.VISIBLE : View.GONE);
		binding.btnSubtitles.setVisibility(entries(C.TRACK_TYPE_TEXT).isEmpty() ? View.GONE : View.VISIBLE);
		boolean showQuality;
		String label = "Auto";
		if (yt) {
			List<String> qualities = youtubeQualities();
			showQuality = qualities.size() > 1;
			VideoStream selected = vm.result.tag.getSelectedVideo();
			if (selected != null) label = selected.getResolution();
		} else {
			List<Entry> video = entries(C.TRACK_TYPE_VIDEO);
			showQuality = video.size() > 1;
			TrackSelectionParameters params = vm.player.getTrackSelectionParameters();
			boolean forced = false;
			for (Entry e : video) if (e.selected) label = e.label;
			for (TrackSelectionOverride o : params.overrides.values()) {
				if (o.getType() == C.TRACK_TYPE_VIDEO) forced = true;
			}
			if (!forced) label = "Auto";
		}
		binding.btnQuality.setVisibility(showQuality ? View.VISIBLE : View.GONE);
		binding.btnQuality.setText(label);
	}

	private List<String> youtubeQualities() {
		Set<String> seen = new LinkedHashSet<>();
		if (isYoutube()) {
			for (VideoStream v : vm.result.tag.getSortedVideoStreams()) seen.add(v.getResolution());
		}
		return new ArrayList<>(seen);
	}

	private void chooseQuality() {
		if (vm == null || vm.player == null) return;
		if (isYoutube()) {
			List<String> qualities = youtubeQualities();
			VideoStream current = vm.result.tag.getSelectedVideo();
			int selected = current == null ? 0 : Math.max(0, qualities.indexOf(current.getResolution()));
			PlayerSheets.choose(this, "Quality", qualities, selected, i -> {
				vm.result.resolver.setPlaybackQuality(qualities.get(i));
				flash("Quality " + qualities.get(i));
				vm.swapYoutubeSource();
			});
			return;
		}
		List<Entry> video = entries(C.TRACK_TYPE_VIDEO);
		if (video.size() < 2) return;
		List<Entry> sorted = new ArrayList<>(video);
		sorted.sort((a, b) -> Integer.compare(
			a.group.getFormat(a.index).height, b.group.getFormat(b.index).height) * -1);
		List<String> labels = new ArrayList<>();
		labels.add("Auto");
		int selected = 0;
		boolean forced = false;
		for (TrackSelectionOverride o : vm.player.getTrackSelectionParameters().overrides.values()) {
			if (o.getType() == C.TRACK_TYPE_VIDEO) forced = true;
		}
		for (int i = 0; i < sorted.size(); i++) {
			labels.add(sorted.get(i).label);
			if (forced && sorted.get(i).selected) selected = i + 1;
		}
		PlayerSheets.choose(this, "Quality", labels, selected, i -> {
			TrackSelectionParameters.Builder b = vm.player.getTrackSelectionParameters().buildUpon();
			if (i == 0) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
			else b.setOverrideForType(new TrackSelectionOverride(sorted.get(i - 1).group,
				sorted.get(i - 1).index));
			vm.player.setTrackSelectionParameters(b.build());
		});
	}

	private void chooseAudio() {
		if (vm == null || vm.player == null) return;
		if (isYoutube()) {
			List<String[]> languages = PlayerSources.audioLanguages(vm.result);
			List<String> labels = new ArrayList<>();
			int selected = 0;
			AudioStream current = vm.result.tag.getSelectedAudio();
			String currentCode = current != null && current.getAudioLocale() != null
				? current.getAudioLocale().getLanguage() : "";
			for (int i = 0; i < languages.size(); i++) {
				labels.add(languages.get(i)[0]);
				if (languages.get(i)[1].equals(currentCode)) selected = i;
			}
			PlayerSheets.choose(this, "Audio language", labels, selected, i -> {
				vm.sources.ytConfig.audioLanguage = languages.get(i)[1];
				flash(languages.get(i)[0]);
				vm.swapYoutubeSource();
			});
			return;
		}
		List<Entry> audioTracks = entries(C.TRACK_TYPE_AUDIO);
		List<String> labels = new ArrayList<>();
		int selected = 0;
		for (int i = 0; i < audioTracks.size(); i++) {
			labels.add(audioTracks.get(i).label);
			if (audioTracks.get(i).selected) selected = i;
		}
		PlayerSheets.choose(this, "Audio", labels, selected, i -> {
			Entry e = audioTracks.get(i);
			vm.player.setTrackSelectionParameters(vm.player.getTrackSelectionParameters().buildUpon()
				.setOverrideForType(new TrackSelectionOverride(e.group, e.index)).build());
		});
	}

	private void chooseSubtitles() {
		if (vm == null || vm.player == null) return;
		List<Entry> tracks = entries(C.TRACK_TYPE_TEXT);
		if (tracks.isEmpty()) return;
		boolean disabled = vm.player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
		List<String> labels = new ArrayList<>();
		labels.add("Off");
		int selected = disabled ? 0 : 1;
		for (int i = 0; i < tracks.size(); i++) {
			labels.add(tracks.get(i).label);
			if (!disabled && tracks.get(i).selected) selected = i + 1;
		}
		PlayerSheets.choose(this, "Subtitles", labels, selected, i -> {
			TrackSelectionParameters.Builder b = vm.player.getTrackSelectionParameters().buildUpon();
			if (i == 0) {
				b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
			} else {
				b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
				b.setOverrideForType(new TrackSelectionOverride(tracks.get(i - 1).group,
					tracks.get(i - 1).index));
			}
			vm.player.setTrackSelectionParameters(b.build());
		});
	}
}
