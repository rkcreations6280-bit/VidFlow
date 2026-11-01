package sysModules.interCaches;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieResult;
import com.nextgen.R;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;

/**
 * Utility class that preloads and caches all Lottie animation compositions from
 * the application's raw resources. This class follows a singleton pattern and
 * loads compositions in parallel to minimize startup time. Once loaded,
 * compositions are kept in memory for reuse across the application.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Preloads Lottie animations from raw resources (JSON animation files).</li>
 * <li>Uses parallel loading with a thread pool based on available CPU cores.</li>
 * <li>Provides type-safe getters for each animation composition.</li>
 * <li>Prevents duplicate loading with a loaded state flag.</li>
 * <li>Logs loading performance metrics for monitoring.</li>
 * </ul>
 *
 * <p>Usage: Call {@link #loadAllCompositions()} during app initialization,
 * then retrieve compositions via getters like {@link #getLayoutLoadComposition()}.
 *
 * @see LottieComposition
 * @see LottieCompositionFactory
 * @see #loadAllCompositions()
 */
public final class AppRawFiles {
	
	private static final LoggerUtils logger = LoggerUtils.from(AppRawFiles.class);
	private static final AppRawFiles instance = new AppRawFiles();
	private volatile boolean isLoadedAllComposition = false;
	private volatile LottieComposition layoutLoadComposition,
		waitingLoadingComposition, noResultEmptyComposition,
		openActiveTasksComposition, downloadReadyComposition,
		successfulDownloadComposition, circleLoadingComposition,
		audioVisualizingComposition2, audioVisualizingComposition,
		emptyGhostComposition, newVersionUpdateComposition,
		loginRequiredComposition, premiumUserComposition,
		upgradeBoxComposition;
	
	/**
	 * Private constructor to enforce the singleton pattern. Prevents external
	 * instantiation of the {@link AppRawFiles} utility class. All functionality
	 * is exposed through static methods, and the singleton instance is created
	 * eagerly at class initialization.
	 *
	 * @see #getInstance()
	 */
	private AppRawFiles() {}
	
	/**
	 * Returns the singleton instance of the AppRawFiles utility class. The instance
	 * is created eagerly during class loading, ensuring thread safety without
	 * additional synchronization overhead.
	 *
	 * @return The singleton {@link AppRawFiles} instance.
	 */
	public static AppRawFiles getInstance() {
		return instance;
	}
	
	/**
	 * Loads all Lottie animation compositions from the app's raw resources into
	 * memory. This method is idempotent; if compositions have already been loaded,
	 * it returns immediately with a debug log message. Loading is performed
	 * asynchronously on a background thread via {@link ThreadTask}.
	 *
	 * <p>After loading completes (successfully or partially), the result task
	 * logs a debug message indicating that compositions are loaded. Individual
	 * composition loading progress and errors are handled within
	 * {@link #loadSync(ThreadTask.ProgressCallback)}.
	 *
	 * @see #loadSync(ThreadTask.ProgressCallback)
	 * @see ThreadTask
	 */
	public static void loadAllCompositions() {
		if (instance.isLoadedAllComposition) {
			logger.debug("Compositions already loaded.");
			return;
		}
		
		ThreadTask<Boolean, LottieComposition> bulkLoadJob = new ThreadTask<>();
		bulkLoadJob.setBackgroundTask(progressCallback -> {
			instance.loadSync(progressCallback);
			return true;
		});
		
		bulkLoadJob.setResultTask(result ->
			logger.debug("All compositions loaded into memory."));
		bulkLoadJob.start();
	}
	
	/**
	 * Synchronously loads all Lottie animation compositions from raw resources
	 * using a parallel thread pool. This method divides the loading work across
	 * multiple threads based on available CPU cores, significantly reducing
	 * total loading time compared to sequential loading.
	 *
	 * <p><strong>Loading behavior:</strong>
	 * <ul>
	 * <li>Uses a fixed thread pool with size = max(2, availableProcessors()).</li>
	 * <li>Each composition is loaded via {@link LottieCompositionFactory#fromRawResSync}.</li>
	 * <li>Progress callbacks are invoked as each composition completes loading.</li>
	 * <li>Errors are logged per resource without failing the entire batch.</li>
	 * <li>A {@link CountDownLatch} ensures all compositions load before returning.</li>
	 * </ul>
	 *
	 * <p>After all compositions are loaded, {@code isLoadedAllComposition} is set
	 * to true, and total loading time is logged for performance monitoring.
	 *
	 * @param progressCallback Optional callback to receive each loaded composition
	 *                         as it becomes available. May be {@code null}.
	 * @see LottieCompositionFactory#fromRawResSync(android.content.Context, int)
	 */
	private void loadSync(ThreadTask.ProgressCallback<LottieComposition> progressCallback) {
		if (isLoadedAllComposition) return;
		long startTime = System.currentTimeMillis();
		
		ResourceMapping[] mappings = {
			new ResourceMapping(R.raw.animation_layout_load, c -> layoutLoadComposition = c),
			new ResourceMapping(R.raw.animation_no_result, c -> noResultEmptyComposition = c),
			new ResourceMapping(R.raw.animation_waiting_loading, c -> waitingLoadingComposition = c),
			new ResourceMapping(R.raw.animation_active_tasks, c -> openActiveTasksComposition = c),
			new ResourceMapping(R.raw.animation_videos_found, c -> downloadReadyComposition = c),
			new ResourceMapping(R.raw.animation_successful, c -> successfulDownloadComposition = c),
			new ResourceMapping(R.raw.animation_circle_loading, c -> circleLoadingComposition = c),
			new ResourceMapping(R.raw.animation_audio_visualizing2, c -> audioVisualizingComposition2 = c),
			new ResourceMapping(R.raw.animation_audio_visualizing, c -> audioVisualizingComposition = c),
			new ResourceMapping(R.raw.animation_empty_ghost, c -> emptyGhostComposition = c),
			new ResourceMapping(R.raw.animation_new_app_version, c -> newVersionUpdateComposition = c),
			new ResourceMapping(R.raw.animation_login_required, c -> loginRequiredComposition = c),
			new ResourceMapping(R.raw.animation_premium_user, c -> premiumUserComposition = c),
			new ResourceMapping(R.raw.animation_upgrade_box, c -> upgradeBoxComposition = c)
		};
		
		int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
		try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
			CountDownLatch latch = new CountDownLatch(mappings.length);
			for (ResourceMapping mapping : mappings) {
				executor.execute(() -> {
					try {
						BaseApplication appContext = BaseApplication.AppContext;
						LottieResult<LottieComposition> result = LottieCompositionFactory
							.fromRawResSync(appContext, mapping.resId);
						
						if (result.getValue() != null) {
							mapping.setter.set(result.getValue());
							if (progressCallback != null) {
								progressCallback.onProgress(result.getValue());
							}
						} else if (result.getException() != null) {
							logger.error("Failed to load Lottie resource: " +
								mapping.resId, result.getException());
						}
					} catch (Exception error) {
						logger.error("Unexpected error loading Lottie resource: " +
							mapping.resId, error);
					} finally {
						latch.countDown();
					}
				});
			}
			
			try {
				latch.await();
			} catch (InterruptedException error) {
				logger.error("Lottie parallel loading interrupted", error);
				Thread.currentThread().interrupt();
			} finally {
				executor.shutdown();
			}
			
			isLoadedAllComposition = true;
			long endTime = System.currentTimeMillis();
			logger.debug("Parallel Lottie loading finished in " +
				(endTime - startTime) + " ms");
		}
	}
	
	/**
	 * Internal helper class that maps a raw resource ID to a composition setter
	 * callback. This allows the parallel loading system to associate each loaded
	 * composition with the appropriate field in the {@link AppRawFiles} instance.
	 *
	 * <p>Instances of this class are used in {@link #loadSync(ThreadTask.ProgressCallback)}
	 * to define which resource ID should be stored in which composition field.
	 *
	 * @see AppRawFiles#loadSync(ThreadTask.ProgressCallback)
	 * @see CompositionSetter
	 */
	private static class ResourceMapping {
		final int resId;
		final CompositionSetter setter;
		
		ResourceMapping(int resId, CompositionSetter setter) {
			this.resId = resId;
			this.setter = setter;
		}
	}
	
	/**
	 * Functional interface for setting a loaded {@link LottieComposition} into
	 * a specific field of the {@link AppRawFiles} instance. Implementations
	 * are lambda expressions that assign the composition to a class field.
	 *
	 * <p><strong>Example usage:</strong>
	 * <pre>
	 * new ResourceMapping(R.raw.animation_example, comp -> exampleComposition = comp)
	 * </pre>
	 *
	 * @see ResourceMapping
	 * @see AppRawFiles#loadSync(ThreadTask.ProgressCallback)
	 */
	private interface CompositionSetter {
		void set(LottieComposition composition);
	}
	
	/**
	 * Returns the loaded Lottie composition for layout loading animations (e.g.,
	 * a skeleton loader or shimmer effect). This animation is displayed while
	 * the app is preparing the initial UI or loading content from the network.
	 *
	 * @return The {@link LottieComposition} for layout load animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getLayoutLoadComposition() {
		return layoutLoadComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for waiting/loading animations (e.g.,
	 * a spinner or hourglass). This animation is shown when the app is waiting
	 * for a response from a network request or background operation.
	 *
	 * @return The {@link LottieComposition} for waiting loading animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getWaitingLoadingComposition() {
		return waitingLoadingComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for empty result animations (e.g.,
	 * a magnifying glass or empty folder). This animation is displayed when
	 * a search yields no results or when a content list is empty.
	 *
	 * @return The {@link LottieComposition} for no result empty animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getNoResultEmptyComposition() {
		return noResultEmptyComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for open active tasks animations (e.g.,
	 * a task list icon or activity indicator). This animation is displayed when
	 * there are ongoing or pending background tasks, such as active downloads.
	 *
	 * @return The {@link LottieComposition} for open active tasks animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getOpenActiveTasksComposition() {
		return openActiveTasksComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for download ready animations (e.g.,
	 * a checkmark or success indicator). This animation is shown when a download
	 * is complete and ready for playback or file system access.
	 *
	 * @return The {@link LottieComposition} for download ready animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #getSuccessfulDownloadComposition()
	 */
	public LottieComposition getDownloadReadyComposition() {
		return downloadReadyComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for successful download animations
	 * (e.g., a celebratory burst or confirmation animation). This animation is
	 * shown when a download completes successfully, providing positive feedback.
	 *
	 * @return The {@link LottieComposition} for successful download animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #getDownloadReadyComposition()
	 */
	public LottieComposition getSuccessfulDownloadComposition() {
		return successfulDownloadComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for circular loading animations (e.g.,
	 * a spinning progress indicator). This animation is commonly displayed during
	 * data fetching, network requests, or background processing operations.
	 *
	 * @return The {@link LottieComposition} for circle loading animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getCircleLoadingComposition() {
		return circleLoadingComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for the second version of audio
	 * visualizing animations (e.g., an animated equalizer or waveform). This
	 * composition provides an alternative visual style for music playback screens.
	 *
	 * @return The {@link LottieComposition} for audio visualizing v2 animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #getAudioVisualizingComposition()
	 */
	public LottieComposition getAudioVisualizingComposition2() {
		return audioVisualizingComposition2;
	}
	
	/**
	 * Returns the loaded Lottie composition for audio visualizing animations (e.g.,
	 * an animated equalizer, waveform, or music spectrum display). This animation
	 * is typically shown during audio playback to provide visual feedback.
	 *
	 * @return The {@link LottieComposition} for audio visualizing animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #getAudioVisualizingComposition2()
	 */
	public LottieComposition getAudioVisualizingComposition() {
		return audioVisualizingComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for empty state animations (e.g., a
	 * ghost character indicating no content is available). This animation is
	 * typically displayed in empty lists, search results, or placeholder screens.
	 *
	 * @return The {@link LottieComposition} for empty ghost animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getEmptyGhostComposition() {
		return emptyGhostComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for new app version update animations.
	 * This animation is displayed when a newer version of the application is
	 * available, drawing user attention to the update prompt.
	 *
	 * @return The {@link LottieComposition} for new version update animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getNewVersionUpdateComposition() {
		return newVersionUpdateComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for login required animations (e.g.,
	 * a lock icon or sign-in prompt). This animation is shown to unauthenticated
	 * users when they attempt to access premium or account-bound features.
	 *
	 * @return The {@link LottieComposition} for login required animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getLoginRequiredComposition() {
		return loginRequiredComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for premium user animations (e.g.,
	 * crown icon, premium badge, or upgrade prompts). This composition is typically
	 * displayed when the user has an active premium subscription.
	 *
	 * @return The {@link LottieComposition} for premium user animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getPremiumUserComposition() {
		return premiumUserComposition;
	}
	
	/**
	 * Returns the loaded Lottie composition for upgrade box animations (e.g.,
	 * promotional banners or feature upgrade prompts). This animation is shown
	 * to free users to encourage premium subscription upgrades.
	 *
	 * @return The {@link LottieComposition} for upgrade box animations, or
	 * {@code null} if not yet loaded.
	 * @see #loadAllCompositions()
	 * @see #isLoadedAllComposition()
	 */
	public LottieComposition getUpgradeBoxComposition() {
		return upgradeBoxComposition;
	}
	
	/**
	 * Checks whether all Lottie compositions have been fully loaded into memory.
	 * This method can be used to determine if animations are ready for display
	 * without causing delays or showing placeholders.
	 *
	 * @return {@code true} if all compositions are loaded, {@code false} otherwise.
	 * @see #loadAllCompositions()
	 */
	public boolean isLoadedAllComposition() {
		return isLoadedAllComposition;
	}
}
