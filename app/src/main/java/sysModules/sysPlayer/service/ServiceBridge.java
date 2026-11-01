package sysModules.sysPlayer.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.engine.EngineCallbacks;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.model.PlayerType;
import sysModules.sysPlayer.playback.MediaSourceManager;
import sysModules.sysPlayer.queue.PlayQueue;

/**
 * A singleton bridge class that provides centralized access to core playback
 * components including the {@link PlaybackService}, {@link MediaEngine},
 * and {@link MediaSourceManager}. This class acts as a service locator, allowing
 * various parts of the application to obtain references to these components
 * without passing them through deep call chains or using global state.
 *
 * <p>All public methods are synchronized to ensure thread safety.
 */
public final class ServiceBridge {
	private static final LoggerUtils logger = LoggerUtils.from(ServiceBridge.class);
	private static final ServiceBridge instance = new ServiceBridge();

	@Nullable private PlaybackService service;
	@Nullable private MediaEngine engine;
	@Nullable private MediaSourceManager sourceManager;
	@Nullable private PlayerType playerType;
	private boolean initialized;

	private ServiceBridge() {}

	@NonNull
	public static ServiceBridge getInstance() {
		return instance;
	}

	/**
	 * Initializes the ServiceBridge with the core playback components.
	 *
	 * @param service      the playback service
	 * @param engine       the media engine
	 * @param sourceManager the media source manager
	 * @param playerType   the player type
	 */
	public synchronized void init(@NonNull PlaybackService service,
	                              @NonNull MediaEngine engine,
	                              @NonNull MediaSourceManager sourceManager,
	                              @NonNull PlayerType playerType) {
		this.service = service;
		this.engine = engine;
		this.sourceManager = sourceManager;
		this.playerType = playerType;
		this.initialized = true;
		logger.debug("ServiceBridge initialized: " + playerType);
	}

	/**
	 * Clears all references held by the ServiceBridge.
	 */
	public synchronized void clear() {
		service = null;
		engine = null;
		sourceManager = null;
		playerType = null;
		initialized = false;
		logger.debug("ServiceBridge cleared");
	}

	public synchronized boolean isInitialized() {
		return initialized && engine != null;
	}

	@Nullable
	public synchronized MediaEngine getEngine() {
		return engine;
	}

	@Nullable
	public synchronized PlaybackService getService() {
		return service;
	}

	@Nullable
	public synchronized PlayerType getPlayerType() {
		return playerType;
	}

	@Nullable
	public synchronized MediaSourceManager getSourceManager() {
		return sourceManager;
	}

	public synchronized void addCallback(@NonNull EngineCallbacks callback) {
		if (engine != null) {
			engine.addCallback(callback);
		}
	}

	public synchronized void removeCallback(@NonNull EngineCallbacks callback) {
		if (engine != null) {
			engine.removeCallback(callback);
		}
	}

	/**
	 * Returns the currently bound play queue, if any.
	 */
	@Nullable
	public synchronized PlayQueue getPlayQueue() {
		return sourceManager != null ? sourceManager.getPlayQueue() : null;
	}
}
