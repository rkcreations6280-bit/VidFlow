package sysModules.crashedHandler;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone crash / hang / black-screen logger. It does NOT depend on the database,
 * ViewBinding, app theme or any other part of the app, so it still works when the rest
 * of the app is broken.
 *
 * <ul>
 * <li><b>Crash:</b> {@link #reportCrash(Thread, Throwable)} builds a report and opens
 * {@link CrashLogActivity} (separate process) automatically.</li>
 * <li><b>Hang:</b> a watchdog thread reports when the main thread is blocked for
 * {@link #HANG_MS}.</li>
 * <li><b>Black screen:</b> the watchdog reports when the app is in the foreground but no
 * activity became visible within {@link #NO_UI_MS}.</li>
 * </ul>
 * The report is also saved as {@code last_crash.txt} in the app's external files folder
 * (Android/data/&lt;package&gt;/files/).
 */
public final class CrashLogger {

	public static final String EXTRA_REPORT_PATH = "crash_report_path";
	public static final String EXTRA_MAIN_PID = "crash_main_pid";
	public static final String REPORT_FILE_NAME = "last_crash.txt";
	public static final String PENDING_FLAG_NAME = "crash_unseen.flag";

	private static final long HANG_MS = 8_000L;
	private static final long NO_UI_MS = 12_000L;
	private static final int MAX_CRUMBS = 120;

	private static final ArrayDeque<String> crumbs = new ArrayDeque<>();
	private static final AtomicBoolean reportStarted = new AtomicBoolean(false);
	private static volatile boolean anyActivityResumed = false;
	private static volatile Application app;

	private CrashLogger() {}

	/** True when running inside the separate crash-log process. */
	public static boolean isCrashLogProcess() {
		String name = Application.getProcessName();
		return name != null && name.endsWith(":crashlog");
	}

	/** Call once from Application.onCreate() of the main process. */
	public static void install(Application application) {
		app = application;
		breadcrumb("process started, pid=" + Process.myPid());
		application.registerActivityLifecycleCallbacks(new LifecycleLogger());
		startWatchdog();
	}

	/** Adds a line to the "what happened before the problem" list in the report. */
	public static void breadcrumb(String message) {
		String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
		synchronized (crumbs) {
			if (crumbs.size() >= MAX_CRUMBS) crumbs.removeFirst();
			crumbs.addLast(time + "  " + message);
		}
	}

	/** Adds a caught (non-fatal) exception to the breadcrumbs. */
	public static void breadcrumb(String message, Throwable error) {
		breadcrumb(message + "\n" + stackTraceOf(error));
	}

	// ── Crash ────────────────────────────────────────────────────────

	public static void reportCrash(Thread thread, Throwable throwable) {
		try {
			if (app == null) return;
			String report = buildReport("APP CRASHED",
				"Thread: " + thread.getName(), throwable, false);
			writeAndShow(report);
		} catch (Throwable ignored) {
		}
	}

	// ── Pending report (shown on next launch if it could not be shown) ──

	/** @return true if an unseen report existed and the crash screen was started. */
	public static boolean launchPendingReportIfAny(Context context) {
		try {
			File dir = reportDir(context);
			File flag = new File(dir, PENDING_FLAG_NAME);
			File report = new File(dir, REPORT_FILE_NAME);
			if (!flag.exists() || !report.exists()) return false;
			launch(context, report);
			return true;
		} catch (Throwable error) {
			return false;
		}
	}

	// ── Watchdog (hang + black screen) ───────────────────────────────

	private static void startWatchdog() {
		Thread watchdog = new Thread(() -> {
			Handler main = new Handler(Looper.getMainLooper());
			long start = SystemClock.elapsedRealtime();
			try {
				while (!reportStarted.get()) {
					final AtomicBoolean answered = new AtomicBoolean(false);
					main.post(() -> answered.set(true));

					long waited = 0;
					while (!answered.get() && waited < HANG_MS) {
						Thread.sleep(250);
						waited += 250;
					}

					if (!answered.get()) {
						reportProblem("APP NOT RESPONDING",
							"Main thread was blocked for more than " + (HANG_MS / 1000) + " seconds.");
						return;
					}

					long elapsed = SystemClock.elapsedRealtime() - start;
					if (!anyActivityResumed && elapsed > NO_UI_MS && isInForeground()) {
						reportProblem("BLACK SCREEN",
							"App is in the foreground but no screen became visible within "
								+ (NO_UI_MS / 1000) + " seconds.");
						return;
					}
					if (anyActivityResumed && elapsed > NO_UI_MS) {
						// App started fine; keep only the cheap hang check running.
						Thread.sleep(2000);
						continue;
					}
					Thread.sleep(1000);
				}
			} catch (Throwable ignored) {
			}
		}, "CrashLogWatchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	private static boolean isInForeground() {
		try {
			ActivityManager.RunningAppProcessInfo info =
				new ActivityManager.RunningAppProcessInfo();
			ActivityManager.getMyMemoryState(info);
			return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
		} catch (Throwable error) {
			return true;
		}
	}

	private static void reportProblem(String title, String reason) {
		if (app == null || !reportStarted.compareAndSet(false, true)) return;
		try {
			String report = buildReport(title, reason, null, true);
			writeAndShow(report);
		} catch (Throwable ignored) {
		}
	}

	// ── Report building ──────────────────────────────────────────────

	private static String buildReport(String title, String reason,
	                                  Throwable throwable, boolean withAllThreads) {
		StringBuilder sb = new StringBuilder();
		sb.append("=== ").append(title).append(" ===\n");
		sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
			.append("\n");
		sb.append(reason).append("\n\n");

		sb.append("--- Device / app ---\n");
		sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
		sb.append("Android: ").append(Build.VERSION.RELEASE)
			.append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
		sb.append("ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?")
			.append('\n');
		try {
			PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
			sb.append("App: ").append(info.packageName).append(' ')
				.append(info.versionName).append('\n');
		} catch (Throwable ignored) {
		}
		sb.append("Process: ").append(Application.getProcessName())
			.append(" pid=").append(Process.myPid()).append("\n\n");

		if (throwable != null) {
			sb.append("--- Exception ---\n").append(stackTraceOf(throwable)).append('\n');
		}

		if (withAllThreads) {
			sb.append("--- Main thread stack ---\n");
			try {
				for (StackTraceElement element : Looper.getMainLooper().getThread().getStackTrace()) {
					sb.append("    at ").append(element).append('\n');
				}
			} catch (Throwable ignored) {
			}
			sb.append("\n--- All threads ---\n");
			try {
				for (Map.Entry<Thread, StackTraceElement[]> entry
					: Thread.getAllStackTraces().entrySet()) {
					sb.append("[").append(entry.getKey().getName()).append("] state=")
						.append(entry.getKey().getState()).append('\n');
					for (StackTraceElement element : entry.getValue()) {
						sb.append("    at ").append(element).append('\n');
					}
				}
			} catch (Throwable ignored) {
			}
			sb.append('\n');
		}

		sb.append("--- Breadcrumbs (oldest first) ---\n");
		synchronized (crumbs) {
			for (String crumb : crumbs) sb.append(crumb).append('\n');
		}

		sb.append("\n--- Logcat (this app only, last 400 lines) ---\n");
		sb.append(readLogcat());
		return sb.toString();
	}

	private static String readLogcat() {
		final StringBuilder out = new StringBuilder();
		Thread reader = new Thread(() -> {
			java.lang.Process process = null;
			try {
				process = Runtime.getRuntime().exec(
					new String[]{"logcat", "-d", "-v", "threadtime", "-t", "400"});
				try (BufferedReader br = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = br.readLine()) != null) {
						synchronized (out) {
							out.append(line).append('\n');
						}
					}
				}
			} catch (Throwable ignored) {
			} finally {
				if (process != null) process.destroy();
			}
		}, "CrashLogLogcat");
		reader.setDaemon(true);
		reader.start();
		try {
			reader.join(2500);
		} catch (InterruptedException ignored) {
		}
		synchronized (out) {
			return out.length() == 0 ? "(logcat not available)\n" : out.toString();
		}
	}

	private static String stackTraceOf(Throwable error) {
		StringWriter sw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sw)) {
			error.printStackTrace(pw);
		}
		return sw.toString();
	}

	// ── Writing and showing ──────────────────────────────────────────

	private static File reportDir(Context context) {
		File dir = context.getExternalFilesDir(null);
		if (dir == null) dir = context.getFilesDir();
		return dir;
	}

	private static void writeAndShow(String report) throws Exception {
		File dir = reportDir(app);
		File file = new File(dir, REPORT_FILE_NAME);
		try (FileOutputStream out = new FileOutputStream(file, false)) {
			out.write(report.getBytes(StandardCharsets.UTF_8));
		}
		//noinspection ResultOfMethodCallIgnored
		new File(dir, PENDING_FLAG_NAME).createNewFile();
		launch(app, file);
	}

	private static void launch(Context context, File reportFile) {
		Intent intent = new Intent(context, CrashLogActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.putExtra(EXTRA_REPORT_PATH, reportFile.getAbsolutePath());
		intent.putExtra(EXTRA_MAIN_PID, Process.myPid());
		context.startActivity(intent);
	}

	// ── Screen lifecycle breadcrumbs ─────────────────────────────────

	private static final class LifecycleLogger implements Application.ActivityLifecycleCallbacks {
		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
			breadcrumb(activity.getClass().getSimpleName() + " created");
		}

		@Override
		public void onActivityStarted(Activity activity) {
		}

		@Override
		public void onActivityResumed(Activity activity) {
			anyActivityResumed = true;
			breadcrumb(activity.getClass().getSimpleName() + " resumed (visible)");
		}

		@Override
		public void onActivityPaused(Activity activity) {
		}

		@Override
		public void onActivityStopped(Activity activity) {
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
			breadcrumb(activity.getClass().getSimpleName() + " destroyed");
		}
	}
}
