package sysModules.sysPlayer.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.engine.AudioFocusHelper;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.helper.LockManager;
import sysModules.sysPlayer.helper.SerializedCache;
import sysModules.sysPlayer.helper.HistoryRecorder;
import sysModules.sysPlayer.model.PlayerType;
import sysModules.sysPlayer.model.RepeatMode;
import sysModules.sysPlayer.queue.PlayQueueItem;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import sysModules.sysPlayer.notification.NotificationConstants;
import sysModules.sysPlayer.notification.PlaybackNotification;
import sysModules.sysPlayer.queue.PendingPlaybackQueue;
import sysModules.sysPlayer.queue.PlayQueue;
import sysModules.sysPlayer.playback.MediaSourceManager;
import sysModules.sysPlayer.playback.PlaybackListener;
import sysModules.sysPlayer.queue.QueueSyncManager;
import sysModules.sysPlayer.session.MediaSessionManager;
import sysModules.sysPlayer.session.PlayQueueNavigator;

/**
 * Foreground service that manages background audio/video playback for the
 * application. This service integrates with ExoPlayer, handles audio focus,
 * provides media session controls for system UI (lock screen, Android Auto,
 * Wear OS), displays a media notification, and manages wake locks to keep
 * the CPU active during background playback.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Hosts and manages the {@link MediaEngine} for playback control.</li>
 * <li>Manages audio focus via {@link AudioFocusHelper}.</li>
 * <li>Provides system media controls via {@link MediaSessionManager}.</li>
 * <li>Displays a media notification with playback controls.</li>
 * <li>Acquires and releases a wake lock for background playback.</li>
 * <li>Handles media actions (play, pause, next, previous, stop, close).</li>
 * <li>Registers itself with {@link ServiceBridge} for global access.</li>
 * </ul>
 *
 * <p>The service runs in the foreground with sticky start mode to survive
 * temporary system kills. It does not support binding; use {@link ServiceBridge}
 * to access its components.
 *
 * @see Service
 * @see MediaEngine
 * @see MediaSessionManager
 * @see PlaybackNotification
 * @see ServiceBridge
 */
public class PlaybackService extends Service implements PlaybackListener {
	private static final LoggerUtils logger = LoggerUtils.from(PlaybackService.class);
	
	private MediaEngine engine;
	private MediaSourceManager sourceManager;
	private AudioFocusHelper audioFocus;
	private MediaSessionManager sessionManager;
	private PlayQueueNavigator queueNavigator;
	private PlaybackNotification notification;
	private PlayerType playerType;
	private boolean serviceStarted;
	private LockManager lockManager;
	private ExoPlayer extPlayer;
	private final Handler extHandler = new Handler(Looper.getMainLooper());
	
	/**
	 * Called when the service is first created. This method initializes all core
	 * playback components including the media engine, queue manager, audio focus
	 * helper, media session manager, and playback notification. It also configures
	 * the wake lock for background playback and registers the service with the
	 * {@link ServiceBridge} singleton.
	 *
	 * <p><strong>Initialization performed:</strong>
	 * <ul>
	 * <li>Creates and initializes the {@link MediaEngine}.</li>
	 * <li>Creates the {@link QueueSyncManager} for queue synchronization.</li>
	 * <li>Creates the {@link AudioFocusHelper} for audio focus management.</li>
	 * <li>Creates the {@link MediaSessionManager} for system media controls.</li>
	 * <li>Creates the {@link PlaybackNotification} for media notifications.</li>
	 * <li>Registers the notification as a callback to the engine.</li>
	 * <li>Acquires a partial wake lock to keep CPU active during playback.</li>
	 * <li>Initializes the ServiceBridge with all components.</li>
	 * </ul>
	 *
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		engine = new MediaEngine(this);
		audioFocus = new AudioFocusHelper(this, engine);
		sessionManager = new MediaSessionManager(this, engine);
		notification = new PlaybackNotification(this);
		playerType = PlayerType.MAIN;
		lockManager = new LockManager(this);
		
		engine.addCallback(notification);
		
		logger.debug("PlaybackService created");
	}
	
	/**
	 * Called each time the service is started via {@link android.content.Context#startService(Intent)}.
	 * This method extracts and processes the action from the intent, then handles
	 * the corresponding playback command via {@link #handleAction(String, Intent)}.
	 * It also updates the player type if provided in the intent extras.
	 *
	 * <p>The service returns {@link #START_STICKY} to ensure it is restarted
	 * automatically if killed by the system (e.g., due to low memory), though
	 * the most recent intent will be re-delivered.
	 *
	 * @param intent  The Intent supplied to {@link #startService(Intent)}, containing
	 *                the action and optional extras.
	 * @param flags   Additional data about this start request.
	 * @param startId A unique integer representing this specific start request.
	 * @return {@link #START_STICKY} to restart service if killed.
	 */
	@Override
	public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null) {
				handleAction(action, intent);
			}
			
			if (intent.hasExtra(NotificationConstants.EXTRA_PLAYER_TYPE)) {
				try {
					Object extra = intent.getSerializableExtra(
						NotificationConstants.EXTRA_PLAYER_TYPE);
					if (extra instanceof PlayerType) {
						playerType = (PlayerType) extra;
					}
				} catch (ClassCastException e) {
					logger.error("Invalid player type extra", e);
				}
			}
		}
		return START_STICKY;
	}
	
	/**
	 * Handles media control actions received from the notification buttons,
	 * lock screen controls, or other system UI components. This method routes
	 * each action to the appropriate engine method and manages wake lock and
	 * foreground state accordingly.
	 *
	 * <p><strong>Action mappings:</strong>
	 * <ul>
	 * <li>PLAY → Starts playback, ensures foreground, acquires wake lock.</li>
	 * <li>PAUSE → Pauses playback, releases wake lock.</li>
	 * <li>PLAY_PAUSE → Toggles state; manages wake lock based on resulting state.</li>
	 * <li>NEXT → Skips to next track in the queue.</li>
	 * <li>PREVIOUS → Returns to previous track.</li>
	 * <li>STOP → Stops playback and cleans up resources.</li>
	 * <li>CLOSE → Fully closes the player and stops the service.</li>
	 * </ul>
	 *
	 * <p>Wake lock is acquired only when playback is active to preserve battery.
	 * The service is moved to foreground when playback starts to ensure
	 * background operation stability.
	 *
	 * @param action The action identifier from {@link NotificationConstants}.
	 * @param intent The intent containing the action (extras are currently unused).
	 */
	private void handleAction(@NonNull String action, @NonNull Intent intent) {
		if (NotificationConstants.ACTION_HOST_EXTERNAL.equals(action)) {
			hostExternal();
			return;
		}
		if (ExternalPlayback.active() && handleExternalAction(action)) return;
		switch (action) {
			case NotificationConstants.ACTION_PLAY:
				engine.play();
				ensureForeground();
				acquireWakeLock();
				break;
			case NotificationConstants.ACTION_PAUSE:
				engine.pause();
				releaseWakeLock();
				break;
			case NotificationConstants.ACTION_PLAY_PAUSE:
				engine.playPause();
				ensureForeground();
				if (engine.getExoPlayer().getPlayWhenReady()) {
					acquireWakeLock();
				} else {
					releaseWakeLock();
				}
				break;
			case NotificationConstants.ACTION_NEXT:
				engine.playNext();
				break;
			case NotificationConstants.ACTION_PREVIOUS:
				engine.playPrevious();
				break;
			case NotificationConstants.ACTION_STOP:
				stopPlayback();
				break;
		case NotificationConstants.ACTION_CLOSE:
			closePlayer();
			break;
		case NotificationConstants.ACTION_CYCLE_REPEAT:
			engine.cycleRepeatMode();
			break;
			case NotificationConstants.ACTION_LOAD_AND_PLAY:
				logger.debug("ACTION_LOAD_AND_PLAY received");
				ensureForeground();
				boolean audioOnly = intent.getBooleanExtra(
					NotificationConstants.EXTRA_AUDIO_ONLY, false);
				engine.setAudioOnly(audioOnly);
				PlayQueue queue = null;
				if (intent.hasExtra(NotificationConstants.EXTRA_PLAY_QUEUE)) {
					try {
						Object extra = intent.getSerializableExtra(
							NotificationConstants.EXTRA_PLAY_QUEUE);
						logger.debug("EXTRA_PLAY_QUEUE type=" +
							(extra != null ? extra.getClass().getName() : "null"));
						if (extra instanceof PlayQueue) {
							queue = (PlayQueue) extra;
						}
					} catch (ClassCastException e) {
						logger.error("Invalid play queue extra", e);
					}
				}
				if (queue == null) {
					queue = PendingPlaybackQueue.consume();
					logger.debug("Consumed from PendingPlaybackQueue: " +
						(queue != null ? queue.size() + " items" : "null"));
				}
				if (queue != null) {
					loadAndPlay(queue);
				} else {
					logger.error("ACTION_LOAD_AND_PLAY has no queue");
				}
				break;
		}
	}
	
	/**
	 * Loads the specified play queue and begins playback. This method performs
	 * complete setup of the media session, queue navigator, notification,
	 * audio focus, and wake lock for background playback.
	 *
	 * <p><strong>Initialization steps:</strong>
	 * <ol>
	 * <li>Connects and configures the media session with the ExoPlayer.</li>
	 * <li>Sets a close callback to shut down the service when the session closes.</li>
	 * <li>Creates and attaches a new {@link PlayQueueNavigator} to the session.</li>
	 * <li>Configures the notification with the session token.</li>
	 * <li>Ensures the service is in foreground mode.</li>
	 * <li>Starts the notification service.</li>
	 * <li>Requests audio focus for playback.</li>
	 * <li>Binds the queue manager to the provided queue.</li>
	 * <li>Acquires a wake lock to keep the CPU active during playback.</li>
	 * </ol>
	 *
	 * <p>If a queue navigator already exists, it is disposed and replaced with
	 * a new one bound to the current session and queue.
	 *
	 * @param queue The {@link PlayQueue} to load and play. Must not be null.
	 * @see #ensureForeground()
	 * @see #closePlayer()
	 */
	public void loadAndPlay(@NonNull PlayQueue queue) {
		logger.debug("loadAndPlay() called, queue.size=" + queue.size()
				+ " queue.index=" + queue.getIndex()
				+ " item=" + (queue.getItem() != null ? queue.getItem().getTitle() : "null"));
		sessionManager.connect();
		sessionManager.setPlayer(engine.getExoPlayer());
		sessionManager.setCloseCallback(this::closePlayer);

		// Wire the queue to the engine so playNext()/playPrevious() work
		engine.setPlayQueue(queue);

		if (queueNavigator != null) {
			queueNavigator.dispose();
			queueNavigator = null;
		}
		if (sessionManager.getMediaSession() != null) {
			queueNavigator = new PlayQueueNavigator(
				sessionManager.getMediaSession(), queue, engine);
			sessionManager.setQueueNavigator(queueNavigator);
		}
		
		notification.setSessionToken(sessionManager.getSessionToken());
		ensureForeground();
		notification.start();
		
		audioFocus.requestFocus();

		// Dispose old source manager if it exists
		if (sourceManager != null) {
			sourceManager.dispose();
		}
		sourceManager = new MediaSourceManager(this, queue);
		sourceManager.init();

		// Set the player source early so ConcatenatingMediaSource operations
		// (addMediaSource/removeMediaSource) are processed correctly.
		// PlaceholderMediaSource reports an empty timeline, so the player
		// remains in a waiting state until the real source is resolved.
		engine.setMediaSourceAndPrepare(sourceManager.getParentMediaSource());

		ServiceBridge.getInstance().init(this, engine, sourceManager, playerType);
		acquireWakeLock();
	}
	
	/**
	 * Ensures the service is running in the foreground. If the service has not
	 * yet been started in foreground mode, this method calls
	 * {@link #startForeground(int, Notification)} with a placeholder notification
	 * to elevate the service's priority, reducing the chance of being killed by
	 * the system during background playback.
	 *
	 * <p>The placeholder notification is displayed until the actual media
	 * notification is built and shown.
	 *
	 * @see #loadAndPlay(PlayQueue)
	 * @see #stopPlayback()
	 */

	// ── Hosting the VidFlow player screen's ExoPlayer ───────────────

	private final Player.Listener extListener = new Player.Listener() {
		@Override
		public void onIsPlayingChanged(boolean isPlaying) {
			notification.onIsPlayingChanged(isPlaying);
			if (isPlaying) acquireWakeLock(); else releaseWakeLock();
			saveExternalProgress();
		}

		@Override
		public void onPlaybackStateChanged(int state) {
			if (state == Player.STATE_ENDED && extPlayer != null) {
				PlayQueueItem item = externalItem();
				if (item != null) HistoryRecorder.markCompleted(PlaybackService.this, item);
				notification.onIsPlayingChanged(false);
				releaseWakeLock();
			}
		}

		@Override
		public void onRepeatModeChanged(int repeatMode) {
			notification.onRepeatModeChanged(toRepeat(repeatMode));
		}
	};

	private final Runnable extProgressTick = new Runnable() {
		@Override
		public void run() {
			if (extPlayer == null) return;
			if (android.os.Build.VERSION.SDK_INT < 33 && extPlayer.isPlaying()) {
				notification.setProgress(extPlayer.getCurrentPosition(), extPlayer.getDuration());
			}
			extHandler.postDelayed(this, 2000);
		}
	};

	private final Runnable extTick = new Runnable() {
		@Override
		public void run() {
			if (extPlayer == null) return;
			saveExternalProgress();
			extHandler.postDelayed(this, 5000);
		}
	};

	private static RepeatMode toRepeat(int mode) {
		return mode == Player.REPEAT_MODE_ONE ? RepeatMode.ONE
			: mode == Player.REPEAT_MODE_ALL ? RepeatMode.ALL : RepeatMode.NONE;
	}

	@Nullable
	private PlayQueueItem externalItem() {
		String key = ExternalPlayback.key();
		if (key.isEmpty()) return null;
		long duration = extPlayer != null && extPlayer.getDuration() > 0 ? extPlayer.getDuration() / 1000 : 0;
		return new PlayQueueItem(Uri.parse(key), ExternalPlayback.title(), ExternalPlayback.uploader(), duration);
	}

	private void saveExternalProgress() {
		try {
			PlayQueueItem item = externalItem();
			if (item == null || extPlayer == null) return;
			long duration = extPlayer.getDuration();
			if (duration < 60_000) return; // short clips and live streams are not resumed
			HistoryRecorder.recordProgress(this, item, extPlayer.getCurrentPosition(), duration);
		} catch (Throwable ignored) {
		}
	}

	private void hostExternal() {
		ExoPlayer p = ExternalPlayback.player();
		if (p == null) {
			stopSelf();
			return;
		}
		ensureForeground();
		if (extPlayer != p) {
			if (extPlayer != null) extPlayer.removeListener(extListener);
			extPlayer = p;
			p.addListener(extListener);
			sessionManager.connect();
			sessionManager.setPlayer(p);
			sessionManager.setCloseCallback(this::closeExternal);
			notification.setSessionToken(sessionManager.getSessionToken());
			notification.start();
			extHandler.removeCallbacks(extTick);
			extHandler.postDelayed(extTick, 5000);
			extHandler.removeCallbacks(extProgressTick);
			extHandler.postDelayed(extProgressTick, 2000);
		}
		sessionManager.setExternalMetadata(ExternalPlayback.title(), ExternalPlayback.uploader(),
			Math.max(0, p.getDuration()), ExternalPlayback.art());
		notification.setExternal(ExternalPlayback.title(), ExternalPlayback.uploader(), ExternalPlayback.art());
		notification.onRepeatModeChanged(toRepeat(p.getRepeatMode()));
		notification.onIsPlayingChanged(p.isPlaying());
		if (p.isPlaying()) acquireWakeLock();
	}

	/** Notification buttons while the AIO player is hosted. Returns true when handled. */
	private boolean handleExternalAction(@NonNull String action) {
		ExoPlayer p = ExternalPlayback.player();
		if (p == null) return false;
		switch (action) {
			case NotificationConstants.ACTION_PLAY:
				if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0);
				p.play();
				return true;
			case NotificationConstants.ACTION_PAUSE:
				p.pause();
				return true;
			case NotificationConstants.ACTION_PLAY_PAUSE:
				if (p.getPlayWhenReady() && p.getPlaybackState() != Player.STATE_ENDED) {
					p.pause();
				} else {
					if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0);
					p.play();
				}
				return true;
			case NotificationConstants.ACTION_NEXT:
				p.seekForward();
				return true;
			case NotificationConstants.ACTION_PREVIOUS:
				p.seekBack();
				return true;
			case NotificationConstants.ACTION_CYCLE_REPEAT:
				p.setRepeatMode(p.getRepeatMode() == Player.REPEAT_MODE_OFF
					? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
				return true;
			case NotificationConstants.ACTION_STOP:
			case NotificationConstants.ACTION_CLOSE:
				closeExternal();
				return true;
			default:
				return false;
		}
	}

	private void closeExternal() {
		ExoPlayer p = extPlayer != null ? extPlayer : ExternalPlayback.player();
		boolean owned = ExternalPlayback.isServiceOwned();
		Runnable callback = ExternalPlayback.closeCallback();
		extHandler.removeCallbacks(extTick);
		extHandler.removeCallbacks(extProgressTick);
		if (p != null) {
			saveExternalProgress();
			p.removeListener(extListener);
			try {
				p.pause();
			} catch (Throwable ignored) {
			}
		}
		extPlayer = null;
		releaseWakeLock();
		if (serviceStarted) {
			stopForeground(true);
			serviceStarted = false;
		}
		notification.stop();
		sessionManager.clearExternalMetadata();
		sessionManager.release();
		ExternalPlayback.clear();
		if (owned && p != null) {
			try {
				p.release();
			} catch (Throwable ignored) {
			}
		}
		if (callback != null) callback.run();
		stopSelf();
	}

	private void ensureForeground() {
		if (!serviceStarted) {
			startForeground(NotificationConstants.NOTIFICATION_ID,
				buildPlaceholderNotification());
			serviceStarted = true;
			logger.debug("Service moved to foreground");
		}
	}
	
	/**
	 * Builds and returns a placeholder notification used when media metadata is
	 * not yet available (e.g., during initial loading or when the player is
	 * preparing a track). The notification displays a generic "Loading" message
	 * and prevents the service from being killed by the system.
	 *
	 * <p>The notification uses a default media play icon and is marked as
	 * {@code setOngoing(true)} to indicate that playback is expected to start
	 * soon, preventing the notification from being dismissed by the user.
	 *
	 * @return A {@link Notification} instance suitable for foreground service
	 * display during loading states.
	 * @see NotificationConstants#CHANNEL_ID
	 */
	private Notification buildPlaceholderNotification() {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
			this, NotificationConstants.CHANNEL_ID)
			.setSmallIcon(com.nextgen.R.drawable.ic_play_arrow)
			.setContentTitle(getString(com.nextgen.R.string.player_loading))
			.setOngoing(true);
		return builder.build();
	}
	
	/**
	 * Acquires a partial wake lock to keep the CPU running during background
	 * playback. The wake lock is acquired with a timeout of 30 minutes to
	 * prevent accidental indefinite lock holding. The lock is automatically
	 * released when playback stops or the service is destroyed.
	 *
	 * <p>This method checks that the wake lock is non-null and not already held
	 * before attempting to acquire it to avoid unnecessary operations.
	 *
	 * @see android.os.PowerManager.WakeLock#acquire(long)
	 * @see #releaseWakeLock()
	 */
	private void acquireWakeLock() {
		if (lockManager != null && !lockManager.isHeld()) {
			lockManager.acquireWifiAndCpu();
		}
	}
	
	/**
	 * Releases the wake lock if it is currently held. The wake lock is used to
	 * keep the CPU awake during background playback. This method ensures the
	 * lock is properly released to prevent unnecessary battery drain.
	 *
	 * @see android.os.PowerManager.WakeLock#release()
	 */
	private void releaseWakeLock() {
		if (lockManager != null && lockManager.isHeld()) {
			lockManager.releaseWifiAndCpu();
		}
	}
	
	/**
	 * Stops all playback activity and cleans up associated resources. This method:
	 * <ul>
	 * <li>Disposes of the audio focus helper.</li>
	 * <li>Stops the media engine.</li>
	 * <li>Releases the wake lock.</li>
	 * <li>Removes the service from foreground mode if active.</li>
	 * <li>Stops and cleans up the notification.</li>
	 * <li>Releases the media session manager.</li>
	 * <li>Disposes of the queue navigator.</li>
	 * </ul>
	 *
	 * <p>This method is called when playback is intentionally stopped, either
	 * by user action or when the queue becomes empty.
	 */
	private void stopPlayback() {
		audioFocus.dispose();
		engine.stop();
		releaseWakeLock();
		if (serviceStarted) {
			stopForeground(true);
			serviceStarted = false;
		}
		notification.stop();
		sessionManager.release();
		if (queueNavigator != null) {
			queueNavigator.dispose();
			queueNavigator = null;
		}
		logger.debug("Playback stopped");
	}
	
	/**
	 * Completely closes the player and stops the service. This method first calls
	 * {@link #stopPlayback()} to halt all playback and release resources, then
	 * unbinds the queue manager, and finally calls {@link #stopSelf()} to terminate
	 * the service.
	 *
	 * <p>This is the final cleanup method called when the service should be
	 * fully shut down, typically when there is no more media to play or when
	 * the application is being closed.
	 *
	 * @see #stopPlayback()
	 * @see #stopSelf()
	 */
	private void closePlayer() {
		stopPlayback();
		// Persist the queue before shutting down so it can be restored later
		final PlayQueue queue = getQueue();
		if (queue != null) {
			SerializedCache.writePlayQueue(this, queue);
		}
		if (sourceManager != null) {
			sourceManager.dispose();
			sourceManager = null;
		}
		engine.setPlayQueue(null);
		stopSelf();
	}
	
	/**
	 * Called when the playback service is being destroyed. This method performs
	 * comprehensive cleanup of all resources to prevent memory leaks and ensure
	 * proper shutdown of playback components.
	 *
	 * <p><strong>Cleanup performed (in order):</strong>
	 * <ul>
	 * <li>Clears the ServiceBridge singleton references.</li>
	 * <li>Removes the notification callback from the engine.</li>
	 * <li>Stops the notification service.</li>
	 * <li>Releases the media session manager.</li>
	 * <li>Disposes of the queue navigator and clears its reference.</li>
	 * <li>Releases the audio focus helper resources.</li>
	 * <li>Unbinds the queue manager.</li>
	 * <li>Releases the media engine.</li>
	 * <li>Releases the wake lock (if held).</li>
	 * <li>Stops the service from foreground mode if it was started.</li>
	 * </ul>
	 *
	 * @see android.app.Service#onDestroy()
	 * @see #releaseWakeLock()
	 */
	@Override
	public void onDestroy() {
		logger.debug("PlaybackService destroyed");
		extHandler.removeCallbacks(extTick);
		extHandler.removeCallbacks(extProgressTick);
		if (extPlayer != null) {
			extPlayer.removeListener(extListener);
			extPlayer = null;
		}
		if (ExternalPlayback.active() && ExternalPlayback.isServiceOwned()) {
			try {
				ExternalPlayback.player().release();
			} catch (Throwable ignored) {
			}
			ExternalPlayback.clear();
		}
		ServiceBridge.getInstance().clear();
		engine.removeCallback(notification);
		notification.stop();
		sessionManager.release();
		if (queueNavigator != null) {
			queueNavigator.dispose();
			queueNavigator = null;
		}
		audioFocus.dispose();
		if (sourceManager != null) {
			sourceManager.dispose();
			sourceManager = null;
		}
		engine.release();
		releaseWakeLock();
		if (serviceStarted) {
			stopForeground(true);
		}
		super.onDestroy();
	}
	
	/**
	 * Returns the communication channel to the service. This implementation returns
	 * {@code null}, indicating that the service does not support binding for direct
	 * client communication. Clients interact with the service through the singleton
	 * {@link ServiceBridge} instead of via Binder.
	 *
	 * <p>This service is designed as a started service that runs independently
	 * and coordinates through the application-wide ServiceBridge pattern.
	 *
	 * @param intent The Intent that was used to bind to this service.
	 * @return {@code null} always, as this service does not support binding.
	 * @see android.app.Service#onBind(Intent)
	 */
	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	/**
	 * Returns the underlying media engine instance responsible for playback control,
	 * track loading, and state management. The engine provides methods for play,
	 * pause, seek, volume control, and queue management.
	 *
	 * @return The {@link MediaEngine} instance (never null after initialization).
	 * @see MediaEngine
	 */
	public MediaEngine getEngine() {
		return engine;
	}
	
	/**
	 * Returns the current player type (e.g., ExoPlayer or Media3 Player). This
	 * indicates which underlying player implementation is being used for playback.
	 *
	 * @return The current {@link PlayerType} (never null after initialization).
	 * @see #setPlayerType(PlayerType)
	 * @see PlayerType
	 */
	public PlayerType getPlayerType() {
		return playerType;
	}
	
	/**
	 * Sets the player type to be used for playback. This method should be called
	 * during initialization to specify whether to use ExoPlayer or Media3 Player
	 * as the underlying implementation.
	 *
	 * @param playerType The {@link PlayerType} to set. Must not be null.
	 * @see #getPlayerType()
	 * @see PlayerType
	 */
	public void setPlayerType(@NonNull PlayerType playerType) {
		this.playerType = playerType;
	}
	
	/**
	 * Mutes the audio playback and abandons audio focus. When muted, the engine's
	 * volume is set to zero, and the application releases its audio focus, allowing
	 * other apps to play audio without ducking or interruption.
	 *
	 * <p>Audio focus is abandoned because muted playback does not require priority
	 * over other audio sources, improving the user experience with concurrent apps.
	 *
	 * @see #unmute()
	 * @see #toggleMute()
	 * @see AudioFocusHelper#abandonFocus()
	 */
	public void mute() {
		engine.setMuted(true);
		audioFocus.abandonFocus();
	}
	
	/**
	 * Unmutes the audio playback and requests audio focus. This method restores
	 * the audio volume to its previous level and ensures the application has
	 * the necessary audio focus for uninterrupted playback.
	 *
	 * <p>The audio focus request is necessary because unmuting often indicates
	 * that the user is actively listening, and the app should have priority
	 * over other audio sources that may be playing.
	 *
	 * @see #mute()
	 * @see #toggleMute()
	 * @see AudioFocusHelper#requestFocus()
	 */
	public void unmute() {
		engine.setMuted(false);
		audioFocus.requestFocus();
	}
	
	/**
	 * Toggles the current mute state. If the engine is currently muted, it will
	 * be unmuted; otherwise, it will be muted. This method provides a convenient
	 * way to implement mute/unmute toggle buttons in UI components.
	 *
	 * <p>The behavior is equivalent to:
	 * <pre>
	 * if (isMuted()) unmute(); else mute();
	 * </pre>
	 *
	 * @see #mute()
	 * @see #unmute()
	 * @see #isMuted()
	 */
	public void toggleMute() {
		if (engine.isMuted()) {
			unmute();
		} else {
			mute();
		}
	}
	
	/**
	 * Returns whether the audio playback is currently muted. This method can be
	 * used to reflect the current mute state in UI components (e.g., showing a
	 * muted or unmuted icon on a volume button).
	 *
	 * @return {@code true} if the engine is muted, {@code false} otherwise.
	 * @see MediaEngine#isMuted()
	 */
	public boolean isMuted() {
		return engine.isMuted();
	}

	// ─── PlaybackListener ──────────────────────────────────────────────────

	@Override
	public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
		if (engine.getExoPlayer() == null || !engine.isPlaying()) {
			return false;
		}
		final long position = engine.getCurrentPosition();
		final long duration = engine.getDuration();
		return duration - position < timeToEndMillis;
	}

	@Override
	public void onPlaybackBlock() {
		logger.debug("onPlaybackBlock() called");
		// Save current position before stopping so we can resume later
		if (engine.getCurrentItem() != null && engine.getExoPlayer() != null) {
			long position = engine.getCurrentPosition();
			final PlayQueue queue = getQueue();
			if (queue != null) {
				int idx = queue.indexOf(engine.getCurrentItem());
				if (idx >= 0 && position > 0) {
					queue.setRecovery(idx, position);
				}
			}
		}
		engine.setCurrentItem(null);
		engine.stop();
	}

	@Override
	public void onPlaybackUnblock(@NonNull final com.google.android.exoplayer2.source.MediaSource mediaSource) {
        logger.debug("onPlaybackUnblock() called");
        engine.setMediaSourceAndPrepare(mediaSource);
        engine.play();
	}

	@Override
	public void onPlaybackSynchronize(@NonNull final sysModules.sysPlayer.queue.PlayQueueItem item,
									 final boolean wasBlocked) {
		logger.debug("onPlaybackSynchronize(wasBlocked=" + wasBlocked + ") item=" + item.getTitle());
		if (engine.getCurrentItem() == item) return;

		engine.setCurrentItem(item);

		final PlayQueue queue = sourceManager != null
				? getQueue() : null;
		if (queue == null) return;

		final int queueIndex = queue.indexOf(item);
		final int playerIndex = engine.getExoPlayer().getCurrentMediaItemIndex();
		final long recovery = item.getRecoveryPosition();

		if (wasBlocked || playerIndex != queueIndex || !engine.isPlaying()) {
			if (recovery != sysModules.sysPlayer.queue.PlayQueueItem.RECOVERY_UNSET) {
				engine.getExoPlayer().seekTo(queueIndex, recovery);
				queue.setRecovery(queueIndex,
						sysModules.sysPlayer.queue.PlayQueueItem.RECOVERY_UNSET);
			} else {
				engine.getExoPlayer().seekToDefaultPosition(queueIndex);
			}
		}
	}

	@Nullable
	@Override
	public com.google.android.exoplayer2.source.MediaSource sourceOf(
			@NonNull final sysModules.sysPlayer.queue.PlayQueueItem item,
			@NonNull final org.schabi.newpipe.extractor.stream.StreamInfo info) {
		return engine.resolveSource(info);
	}

	@Override
	public void onPlaybackShutdown() {
		logger.debug("onPlaybackShutdown() called");
		closePlayer();
	}

	@Override
	public void onPlayQueueEdited() {
		// Queue was edited; notification will update on next metadata/progress callback
	}

	@Nullable
	private PlayQueue getQueue() {
		return sourceManager != null ? sourceManager.getPlayQueue() : null;
	}
}
