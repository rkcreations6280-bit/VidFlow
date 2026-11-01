package sysModules.sysPlayer.engine;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import coreUtils.library.process.LoggerUtils;

/**
 * Helper class for managing audio focus requests and responding to focus changes
 * during media playback. This class encapsulates the logic for requesting and
 * abandoning audio focus, handling focus gain/loss events, and coordinating
 * with a {@link MediaEngine} to pause, play, or adjust volume accordingly.
 *
 * <p><strong>Focus change handling:</strong>
 * <ul>
 * <li>GAIN -> Restores full volume, resumes playback if paused due to loss.</li>
 * <li>LOSS / LOSS_TRANSIENT -> Pauses playback, tracks state for potential resumption.</li>
 * <li>LOSS_TRANSIENT_CAN_DUCK -> Lowers volume to 20% (ducking).</li>
 * </ul>
 *
 * <p>This class uses the modern {@link AudioFocusRequest} API (introduced in
 * Android O / API 26) for better control over delayed focus gains and audio
 * attributes. It is intended to be used as a companion to a media playback
 * engine in video player modules.
 *
 * @see AudioManager
 * @see AudioFocusRequest
 * @see MediaEngine
 */
public final class AudioFocusHelper {
	private static final LoggerUtils logger = LoggerUtils.from(AudioFocusHelper.class);

	private static final float DUCK_VOLUME = 0.2f;
	private static final int DUCK_DURATION_MS = 1500;

	private final MediaEngine engine;
	private final AudioManager audioManager;
	private final AudioManager.OnAudioFocusChangeListener focusChangeListener;
	private AudioFocusRequest focusRequest;

	private volatile boolean hasFocus;
	private volatile boolean wasPlayingBeforeLoss;
	private volatile boolean isDucked;
	private volatile ValueAnimator duckAnimator;

	/**
	 * Constructs an AudioFocusHelper instance associated with the given media engine.
	 * The helper automatically creates an audio focus change listener and retains
	 * a reference to the system's {@link AudioManager} service.
	 *
	 * @param context The application or activity context used to obtain the
	 *                {@link AudioManager} system service. The application context
	 *                is used to avoid memory leaks.
	 * @param engine  The {@link MediaEngine} instance that will be controlled
	 *                (play, pause, set volume) in response to focus changes.
	 */
	public AudioFocusHelper(@NonNull Context context, @NonNull MediaEngine engine) {
		this.engine = engine;
		this.audioManager = ContextCompat.getSystemService(
			context.getApplicationContext(), AudioManager.class);
		this.focusChangeListener = createListener();
	}

	/**
	 * Creates and returns the audio focus change listener that reacts to focus
	 * events from the system.
	 */
	private AudioManager.OnAudioFocusChangeListener createListener() {
		return change -> {
			switch (change) {
				case AudioManager.AUDIOFOCUS_GAIN:
					hasFocus = true;
					if (isDucked) {
						animateVolume(DUCK_VOLUME, 1.0f);
						isDucked = false;
					}
					if (wasPlayingBeforeLoss && !engine.isPlaying()) {
						engine.play();
					}
					wasPlayingBeforeLoss = false;
					break;
				case AudioManager.AUDIOFOCUS_LOSS:
				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
					hasFocus = false;
					wasPlayingBeforeLoss = engine.isPlaying();
					if (wasPlayingBeforeLoss) {
						engine.pause();
					}
					if (isDucked) {
						engine.setVolume(1.0f);
						isDucked = false;
					}
					break;
				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
					if (!isDucked) {
						engine.setVolume(DUCK_VOLUME);
						isDucked = true;
					}
					break;
			}
		};
	}

	/**
	 * Requests permanent audio focus for media playback.
	 *
	 * @return {@code true} if audio focus was successfully granted,
	 * {@code false} otherwise.
	 */
	public boolean requestFocus() {
		if (audioManager == null) return false;
		try {
			focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
				.setOnAudioFocusChangeListener(focusChangeListener)
				.setAcceptsDelayedFocusGain(true)
				.setAudioAttributes(buildAudioAttributes())
				.build();

			int result = audioManager.requestAudioFocus(focusRequest);
			hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
			return hasFocus;
		} catch (Exception error) {
			logger.error("Failed to request audio focus: " + error.getMessage());
			return false;
		}
	}

	/**
	 * Smoothly animates the playback volume from one level to another.
	 * Cancels any in-progress duck animation first.
	 *
	 * @param from Volume level to animate from (0.0 - 1.0).
	 * @param to   Volume level to animate to (0.0 - 1.0).
	 */
	private void animateVolume(final float from, final float to) {
		if (duckAnimator != null && duckAnimator.isRunning()) {
			duckAnimator.cancel();
		}
		duckAnimator = ValueAnimator.ofFloat(from, to);
		duckAnimator.setDuration(DUCK_DURATION_MS);
		duckAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationStart(final Animator animation) {
				engine.setVolume(from);
			}

			@Override
			public void onAnimationEnd(final Animator animation) {
				engine.setVolume(to);
			}

			@Override
			public void onAnimationCancel(final Animator animation) {
				engine.setVolume(to);
			}
		});
		duckAnimator.addUpdateListener(
			animation -> engine.setVolume((float) animation.getAnimatedValue()));
		duckAnimator.start();
	}

	private static AudioAttributes buildAudioAttributes() {
		return new AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_MEDIA)
			.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
			.build();
	}

	/**
	 * Releases the previously requested audio focus. Safe to call even if
	 * focus was never requested.
	 */
	public void abandonFocus() {
		if (audioManager == null) return;
		if (focusRequest == null) return;
		try {
			audioManager.abandonAudioFocusRequest(focusRequest);
			hasFocus = false;
			wasPlayingBeforeLoss = false;

			if (isDucked) {
				engine.setVolume(1.0f);
				isDucked = false;
			}
		} catch (Exception error) {
			logger.error("Failed to abandon audio focus: " + error.getMessage());
		}
	}

	/**
	 * Releases all resources held by this helper.
	 */
	public void dispose() {
		abandonFocus();
		if (duckAnimator != null) {
			duckAnimator.cancel();
			duckAnimator = null;
		}
	}

	public boolean hasFocus() { return hasFocus; }
	public boolean isDucked() { return isDucked; }
}
