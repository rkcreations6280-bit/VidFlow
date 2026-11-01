package coreUtils.base;

import static java.lang.Thread.setDefaultUncaughtExceptionHandler;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.LifecycleObserver;


import coreUtils.library.process.CrashLogWriter;
import sysModules.crashedHandler.CrashLogger;
import coreUtils.library.process.LoggerUtils;
import dataRepo.appConfigs.AppConfigsRepo;
import dataRepo.dbManager.DbHelper;
import sysModules.crashedHandler.GlobalCrashedHandler;
import sysModules.interCaches.AppRawFiles;
import sysModules.newPipeLib.cache.YtStreamInfoRepo;
import sysModules.newPipeLib.libs.NewPipeLibraryManager;
import sysModules.ytdlpWrapper.YtDlpLibraryManager;

/**
 * Base application class that serves as the entry point for the entire app process.
 * This class provides global application context, crash handling, repository
 * initialization, animation loading, and media extraction library setup. It also
 * offers static utility methods for build type detection and instance access.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Stores a static reference to the application instance for global context access.</li>
 * <li>Registers a global uncaught exception handler for crash logging and reporting.</li>
 * <li>Initializes the SQLite database and all application repositories.</li>
 * <li>Pre-loads Lottie animation compositions from raw resources.</li>
 * <li>Initializes yt-dlp native library and NewPipe Extractor services.</li>
 * <li>Configures the app's default night mode (currently fixed to light theme).</li>
 * <li>Provides {@link #isDebugBuild()} for build-type detection.</li>
 * </ul>
 *
 * <p><strong>Lifecycle order:</strong>
 * {@link #onCreate()} → configure theme → super call → store instance → crash handler
 * → repositories → animations → yt-dlp → NewPipe Extractor.
 *
 * <p><strong>Cleanup:</strong>
 * {@link #onTerminate()} flushes crash logs and releases repository resources,
 * though this method is not guaranteed to be called in production environments.
 *
 * @see android.app.Application
 * @see #getInstance()
 * @see #isDebugBuild()
 * @see #onCreate()
 * @see #onTerminate()
 */
public class BaseApplication extends Application {
	
	private final LoggerUtils logger = LoggerUtils.from(getClass());
	public static BaseApplication AppContext;
	
	/**
	 * Returns the singleton instance of the application class. This static method
	 * provides global access to the application context, which is stored in the
	 * static field {@code AppContext} during {@link #onCreate()}. The returned
	 * instance can be used for context-sensitive operations where an activity
	 * context is not available.
	 *
	 * <p><strong>Usage considerations:</strong>
	 * The application instance is safe to use as a singleton because only one
	 * instance exists per process. However, avoid holding long-lived references
	 * to activities or views from this context. Use {@link Context#getApplicationContext()}
	 * when possible instead of storing a static reference, though this method
	 * provides convenience for dependency injection in non-Android classes.
	 *
	 * @return The singleton {@link BaseApplication} instance. Never {@code null}
	 * after {@link #onCreate()} has completed.
	 * @see #onCreate()
	 * @see android.app.Application
	 */
	public static BaseApplication getInstance() {
		return AppContext;
	}
	
	/**
	 * Determines whether the current application build is a debug variant.
	 * This method returns the value of the {@code IS_DEBUG_MODE_ON} flag from
	 * the {@code FLAG_DEBUGGABLE} bit of the app info, which is set based on
	 * the build type (debug vs. release) in the Gradle configuration.
	 *
	 * <p><strong>Typical usage patterns:</strong>
	 * <ul>
	 * <li>Enable verbose logging or crash reporting only in debug builds.</li>
	 * <li>Show developer options or debug overlays in the UI.</li>
	 * <li>Use mock data sources or test endpoints instead of production APIs.</li>
	 * <li>Conditionally bypass certain security checks or rate limits.</li>
	 * </ul>
	 *
	 * <p><strong>Security note:</strong>
	 * Do not rely on this flag for security enforcement, as the value is
	 * compiled into the APK and can be bypassed in repackaged applications.
	 *
	 * @return {@code true} if the app is running a debug build,
	 * {@code false} for release builds.
	 */
	public static boolean isDebugBuild() {
		return AppContext != null
			&& (AppContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
	}
	
	/**
	 * Performs application-level initialization when the app process is created.
	 * This method configures the theme based on system settings, stores the
	 * application context for global access, registers a crash handler, and
	 * initializes all core dependencies including repositories, animation
	 * compositions, and media extraction libraries.
	 *
	 * <p><strong>Initialization order:</strong>
	 * Theme configuration occurs first to ensure consistent styling before any UI
	 * components are created. The static {@code AppContext} reference is set to
	 * enable context access in non-Android classes. Each dependency is initialized
	 * sequentially; failures in one component are isolated via internal error
	 * handling where available.
	 *
	 * <p><strong>Note:</strong>
	 * This method runs on the main thread. Consider moving heavy initialization to
	 * background threads if performance becomes a concern.
	 *
	 * @see #configureThemeBySystem()
	 * @see #registerGlobalCrashHandler()
	 * @see #initRepositories()
	 * @see #initLottieComposition()
	 */
	@Override
	public void onCreate() {
		configureThemeBySystem();
		super.onCreate();
		AppContext = this;
		// The crash-log screen runs in its own process and must stay minimal.
		if (CrashLogger.isCrashLogProcess()) return;
		CrashLogger.install(this);
		registerGlobalCrashHandler();
		// Each startup step is isolated so one failing library cannot stop the
		// whole app from opening; the error is logged and the app continues.
		runStartupStep("repositories", this::initRepositories);
		runStartupStep("lottie", this::initLottieComposition);
		runStartupStep("yt-dlp", this::initYtDlpLibrary);
		runStartupStep("newpipe", this::initNewPipeExtractorLibrary);
	}
	
	private void runStartupStep(String name, Runnable step) {
		try {
			step.run();
			CrashLogger.breadcrumb("startup step ok: " + name);
		} catch (Throwable error) {
			logger.error("Startup step failed: " + name, error);
			CrashLogger.breadcrumb("startup step FAILED: " + name, error);
		}
	}
	
	/**
	 * Performs cleanup operations when the application process is being terminated.
	 * This method shuts down the crash log writer to ensure all pending log entries
	 * are flushed to disk, releases repository resources to prevent
	 * memory leaks, and then delegates to the superclass for final cleanup.
	 *
	 * <p><strong>Important:</strong>
	 * The {@code onTerminate()} method is not guaranteed to be called on production
	 * devices (e.g., when the system kills the app to reclaim resources). Do not
	 * rely on this for critical data persistence. Use {@link LifecycleObserver}
	 * for user-session critical cleanup.
	 *
	 * @see CrashLogWriter#shutdown()
	 * @see AppConfigsRepo#release()
	 * @see android.app.Application#onTerminate()
	 */
	@Override
	public void onTerminate() {
		CrashLogWriter.shutdown();
		AppConfigsRepo.release();
		super.onTerminate();
	}
	
	/**
	 * Configures the application's default night mode to follow the system setting.
	 * This method sets the {@link androidx.appcompat.app.AppCompatDelegate} night mode
	 * to {@link androidx.appcompat.app.AppCompatDelegate#MODE_NIGHT_NO}, which forces
	 * the app to use light theme regardless of system setting.
	 *
	 * <p><strong>Current behavior:</strong>
	 * The implementation currently uses {@code MODE_NIGHT_NO} (value {@code 1}), which
	 * disables dark mode. To follow the system setting dynamically, replace the value
	 * with {@link androidx.appcompat.app.AppCompatDelegate#MODE_NIGHT_FOLLOW_SYSTEM}
	 * (typically {@code -1} or {@code 0}) or {@code MODE_NIGHT_YES} (value {@code 2}).
	 *
	 * <p>This configuration is applied before {@code super.onCreate()} in
	 * {@link #onCreate()} to ensure all activities inherit the correct theme.
	 *
	 * @see androidx.appcompat.app.AppCompatDelegate#setDefaultNightMode(int)
	 * @see androidx.appcompat.app.AppCompatDelegate#MODE_NIGHT_NO
	 * @see #onCreate()
	 */
	private static void configureThemeBySystem() {
		int modeNightFollowSystem = AppCompatDelegate.MODE_NIGHT_NO;
		AppCompatDelegate.setDefaultNightMode(modeNightFollowSystem);
	}
	
	/**
	 * Registers a global uncaught exception handler for all threads in the application.
	 * This method delegates to
	 * {@link Thread#setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)}
	 * using a custom {@link GlobalCrashedHandler} instance. Any uncaught exception
	 * thrown by any thread will be routed to the handler's {@code uncaughtException()}
	 * method for logging, reporting, and graceful termination.
	 *
	 * <p><strong>Important:</strong>
	 * This replaces the platform's default handler. The custom handler should perform
	 * crash logging, optionally upload diagnostics, and terminate the app process.
	 *
	 * @see GlobalCrashedHandler
	 * @see Thread#setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)
	 */
	private void registerGlobalCrashHandler() {
		setDefaultUncaughtExceptionHandler(new GlobalCrashedHandler());
	}
	
	/**
	 * Initializes Lottie animation compositions by loading all animation resources
	 * from the app's raw directory. This method delegates to
	 * {@link AppRawFiles#loadAllCompositions()}, which typically parses JSON animation
	 * files and prepares them for playback via Lottie animation views.
	 *
	 * <p><strong>Performance note:</strong>
	 * Loading compositions may perform disk I/O. Consider calling this method on a
	 * background thread if multiple large compositions exist. Early initialization
	 * ensures animations are ready when UI components request them.
	 *
	 * @see AppRawFiles#loadAllCompositions()
	 * @see com.airbnb.lottie.LottieAnimationView
	 */
	private void initLottieComposition() {
		AppRawFiles.loadAllCompositions();
	}
	
	/**
	 * Initializes all repository layer dependencies for the application. This method
	 * sets up SQLite database access, then initializes each repository with its
	 * corresponding object box instance. The repositories managed include:
	 * {@link AppConfigsRepo} and {@link YtStreamInfoRepo}.
	 *
	 * <p><strong>Initialization order:</strong>
	 * {@link DbHelper#initialize(Context)} must be called first to establish
	 * the database connection. Each repository's {@code initialize()} method is then
	 * called with its respective box. The total initialization time is measured and
	 * logged at debug level for performance monitoring.
	 *
	 * <p><strong>Logging output:</strong>
	 * Example log: {@code initRepositories took: 47 ms}
	 *
	 * @see DbHelper#initialize(Context)
	 * @see AppConfigsRepo#initialize()
	 * @see YtStreamInfoRepo#initialize()
	 */
	private void initRepositories() {
		long startTime = System.currentTimeMillis();
		try {
			DbHelper.initialize(this);
			AppConfigsRepo.initialize();
			YtStreamInfoRepo.initialize();
		} catch (Throwable error) {
			logger.error("Repository initialization failed", error);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("initRepositories took: " + (endTime - startTime) + " ms");
	}
	
	/**
	 * Initializes the yt-dlp native library service. This method delegates to
	 * {@link YtDlpLibraryManager#initialize(BaseApplication)} to load the native binary,
	 * set up the execution environment, and prepare the service for video
	 * information extraction and download operations.
	 *
	 * <p><strong>Requirements:</strong>
	 * The yt-dlp binary and its dependencies must be packaged appropriately in the
	 * app's assets or native libraries folder. Initialization may involve extracting
	 * binaries to accessible storage and setting execute permissions. Failure to
	 * initialize will cause subsequent video download operations to fail.
	 *
	 * @see YtDlpLibraryManager#initialize(BaseApplication)
	 * @see #initNewPipeExtractorLibrary()
	 */
	private void initYtDlpLibrary() {
		YtDlpLibraryManager.initialize(this);
	}
	
	/**
	 * Initializes the NewPipe Extractor library for parsing video metadata from
	 * streaming platforms. This method delegates to
	 * {@link NewPipeLibraryManager#initializeNewPipeLibrary()}, which configures
	 * the extractor with necessary HTTP client settings, cache policies, and
	 * platform-specific parser implementations.
	 *
	 * <p><strong>Usage context:</strong>
	 * NewPipe Extractor is used alongside yt-dlp to provide fallback or supplementary
	 * metadata extraction capabilities. Initialization is idempotent and typically
	 * performs no blocking I/O after the first call. Call this early in application
	 * startup to avoid delays during first extraction request.
	 *
	 * @see NewPipeLibraryManager#initializeNewPipeLibrary()
	 * @see #initYtDlpLibrary()
	 */
	private void initNewPipeExtractorLibrary() {
		NewPipeLibraryManager.initializeNewPipeLibrary();
	}
}
