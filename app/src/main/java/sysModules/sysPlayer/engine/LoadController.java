package sysModules.sysPlayer.engine;

import com.google.android.exoplayer2.DefaultLoadControl;

/**
 * Custom load controller that manages the player's buffering and preloading behavior.
 * This class extends ExoPlayer's {@link DefaultLoadControl} and adds the ability to
 * dynamically enable or disable preloading for the currently playing track.
 *
 * <p><strong>Core functionality:</strong>
 * <ul>
 * <li>Controls whether the player should continue loading data beyond the current
 *     playback position via {@link #shouldContinueLoading(long, long, float)}.</li>
 * <li>Provides methods to manually disable preloading
 *     ({@link #disablePreloadingOfCurrentTrack()}) and re-enable it
 *     ({@link #enablePreloading()}).</li>
 * <li>Automatically re-enables preloading on player prepared, stopped, and
 *     released events to ensure consistent behavior across playback sessions.</li>
 * </ul>
 *
 * <p><strong>Use cases for disabling preloading:</strong>
 * <ul>
 * <li>Saving network bandwidth when the player is paused and off-screen.</li>
 * <li>Preventing unnecessary buffering during UI transitions.</li>
 * <li>Optimizing resource usage in constrained environments.</li>
 * </ul>
 *
 * <p>The suppression of all lint warnings is intentional for this internal
 * player component where standard warning rules may not apply.
 *
 * @see DefaultLoadControl
 * @see #disablePreloadingOfCurrentTrack()
 * @see #enablePreloading()
 * @see #shouldContinueLoading(long, long, float)
 */
@SuppressWarnings("ALL")
public final class LoadController extends DefaultLoadControl {
	
	private volatile boolean preloadingEnabled = true;
	
	/**
	 * Disables preloading for the currently playing track. When preloading is
	 * disabled, the player will stop loading additional data beyond the current
	 * playback position, effectively freezing the buffer at its current state.
	 *
	 * <p>This is useful in scenarios such as:
	 * <ul>
	 * <li>The player is paused and scrolled off-screen (to save bandwidth).</li>
	 * <li>User explicitly requests to stop background loading.</li>
	 * <li>Transitioning to a different media source where preloading is unnecessary.</li>
	 * </ul>
	 *
	 * <p>To re-enable preloading, call {@link #enablePreloading()}. Note that
	 * preloading is automatically re-enabled on player prepared, stopped, or
	 * released events.
	 *
	 * @see #enablePreloading()
	 * @see #shouldContinueLoading(long, long, float)
	 */
	public void disablePreloadingOfCurrentTrack() {
		preloadingEnabled = false;
	}
	
	/**
	 * Enables preloading for the current and subsequent tracks. When preloading
	 * is enabled, the player continues to load data beyond the current playback
	 * position, ensuring smooth playback and faster seek operations.
	 *
	 * <p>Preloading is enabled by default during normal player operation. This
	 * method is typically called after {@link #disablePreloadingOfCurrentTrack()}
	 * to restore the default buffering behavior, or when the player comes back
	 * into view and should resume background loading.
	 *
	 * @see #disablePreloadingOfCurrentTrack()
	 * @see #shouldContinueLoading(long, long, float)
	 */
	public void enablePreloading() {
		preloadingEnabled = true;
	}
	
	/**
	 * Called when the player is prepared and ready to start playback. This method
	 * enables preloading by setting {@code preloadingEnabled} to {@code true} and
	 * delegates to the superclass implementation. Preloading allows the player to
	 * continue loading data beyond the current playback position.
	 *
	 * @see Listener#onPrepared()
	 */
	@Override
	public void onPrepared() {
		preloadingEnabled = true;
		super.onPrepared();
	}
	
	/**
	 * Called when the player is stopped (e.g., due to {@link #stop()} being called).
	 * This method re-enables preloading by setting {@code preloadingEnabled} to
	 * {@code true} and delegates to the superclass implementation. Re-enabling
	 * preloading ensures future playback sessions can load content efficiently.
	 *
	 * @see Listener#onStopped()
	 */
	@Override
	public void onStopped() {
		preloadingEnabled = true;
		super.onStopped();
	}
	
	/**
	 * Called when the player is released and all resources are freed. This method
	 * re-enables preloading before delegating to the superclass. Setting
	 * {@code preloadingEnabled} to {@code true} ensures that if the player is
	 * recreated, preloading is active by default.
	 *
	 * @see Listener#onReleased()
	 */
	@Override
	public void onReleased() {
		preloadingEnabled = true;
		super.onReleased();
	}
	
	/**
	 * Determines whether the player should continue loading data. This method
	 * returns {@code false} if preloading is disabled, effectively preventing any
	 * further loading operations. When preloading is enabled, it delegates the
	 * decision to the superclass implementation.
	 *
	 * <p>This is useful for controlling buffering behavior under specific conditions,
	 * such as when the player is in a paused state behind the UI or when conserving
	 * network bandwidth is desired.
	 *
	 * @param playbackPositionUs The current playback position in microseconds.
	 * @param bufferedDurationUs The duration of currently buffered data in microseconds.
	 * @param playbackSpeed      The current playback speed (e.g., 1.0 for normal).
	 * @return {@code true} if loading should continue, {@code false} otherwise.
	 */
	@Override
	public boolean shouldContinueLoading(long playbackPositionUs,
	                                     long bufferedDurationUs,
	                                     float playbackSpeed) {
		if (!preloadingEnabled) {
			return false;
		}
		return super.shouldContinueLoading(
			playbackPositionUs, bufferedDurationUs, playbackSpeed);
	}
}
