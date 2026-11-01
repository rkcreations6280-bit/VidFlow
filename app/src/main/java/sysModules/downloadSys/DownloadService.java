package sysModules.downloadSys;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.RemoteViews;

import com.nextgen.R;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sysModules.crashedHandler.CrashLogger;

/**
 * Runs downloads with yt-dlp in a foreground service (so they continue when the app is closed),
 * shows progress in a notification and saves the finished file to Downloads/NextGen.
 */
public class DownloadService extends Service {

	public static final String ACTION_START = "com.nextgen.action.DOWNLOAD_START";
	public static final String ACTION_CANCEL = "com.nextgen.action.DOWNLOAD_CANCEL";
	public static final String ACTION_PAUSE = "com.nextgen.action.DOWNLOAD_PAUSE";
	public static final String ACTION_RESUME = "com.nextgen.action.DOWNLOAD_RESUME";
	public static final String EXTRA_URL = "dl_url";
	public static final String EXTRA_TITLE = "dl_title";
	public static final String EXTRA_AUDIO = "dl_audio";
	public static final String EXTRA_HEIGHT = "dl_height";
	public static final String EXTRA_JOB_ID = "dl_job_id";
	public static final String EXTRA_TOTAL_BYTES = "dl_total_bytes";
	public static final String EXTRA_HEADERS = "dl_headers";

	/** Request headers (Referer, Cookie, User-Agent) for links found by the link resolver, by url. */
	private static final java.util.Map<String, String[]> HEADERS = new ConcurrentHashMap<>();

	private static final String CHANNEL_ID = "downloads";
	private static final int FOREGROUND_ID = 7001;
	private static final AtomicInteger NEXT_DONE_ID = new AtomicInteger(8000);
	private static final Pattern P_TOTAL =
		Pattern.compile("of\\s+~?\\s*([\\d.]+)\\s*([KMGT]?i?B)", Pattern.CASE_INSENSITIVE);
	private static final Pattern P_SPEED =
		Pattern.compile("at\\s+~?\\s*([\\d.]+)\\s*([KMGT]?i?B)/s", Pattern.CASE_INSENSITIVE);

	private final ExecutorService pool = Executors.newSingleThreadExecutor();
	private final Map<String, Boolean> cancelled = new ConcurrentHashMap<>();
	private final Map<String, Boolean> paused = new ConcurrentHashMap<>();
	/** Jobs that already switched to the backup engine; they stay on it when resumed. */
	private static final java.util.Set<String> NEWPIPE_JOBS = ConcurrentHashMap.newKeySet();

	private boolean stopRequested(String jobId) {
		return cancelled.containsKey(jobId) || paused.containsKey(jobId);
	}

	/** Removes yt-dlp's own partial files; keeps the backup engine's resumable parts. */
	private static void deleteYtDlpLeftovers(File dir) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			String name = file.getName();
			if (name.endsWith(".tmpv.part") || name.endsWith(".tmpa.part")) continue;
			deleteRecursively(file);
		}
	}
	private final AtomicInteger active = new AtomicInteger(0);
	private volatile int lastStartId = 0;
	private volatile long lastNotifyTime = 0;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		lastStartId = startId;
		createChannel();
		// Must be called quickly after startForegroundService().
		startForeground(FOREGROUND_ID, buildProgress(null, "Preparing download…", "", "", "", 0, true),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

		if (intent == null) {
			finishIfIdle();
			return START_NOT_STICKY;
		}

		final String action = intent.getAction();
		if (ACTION_CANCEL.equals(action)) {
			String id = intent.getStringExtra(EXTRA_JOB_ID);
			if (id != null) {
				DownloadRegistry.Job job = DownloadRegistry.get(id);
				if (job != null && (job.state == DownloadRegistry.PAUSED
					|| job.state == DownloadRegistry.FAILED)) {
					// Not running: nothing to stop, just clean up.
					DownloadRegistry.remove(id);
					deleteRecursively(workDirOf(id));
				} else {
					cancelled.put(id, true);
					YtDlpRunner.cancel(id);
				}
			}
			finishIfIdle();
			return START_NOT_STICKY;
		}

		if (ACTION_PAUSE.equals(action)) {
			String id = intent.getStringExtra(EXTRA_JOB_ID);
			if (id != null) {
				paused.put(id, true);
				YtDlpRunner.cancel(id);
			}
			finishIfIdle();
			return START_NOT_STICKY;
		}

		if (ACTION_RESUME.equals(action)) {
			String id = intent.getStringExtra(EXTRA_JOB_ID);
			DownloadRegistry.Job job = id == null ? null : DownloadRegistry.get(id);
			if (job == null || (job.state != DownloadRegistry.PAUSED
				&& job.state != DownloadRegistry.FAILED)) {
				finishIfIdle();
				return START_NOT_STICKY;
			}
			paused.remove(id);
			cancelled.remove(id);
			DownloadRegistry.setState(id, DownloadRegistry.QUEUED, "Waiting…");
			launch(job.id, job.url, job.title, job.audio, job.height, job.estimate);
			return START_NOT_STICKY;
		}

		final String url = intent.getStringExtra(EXTRA_URL);
		if (url == null || url.isEmpty()) {
			finishIfIdle();
			return START_NOT_STICKY;
		}
		final String[] headerLines = intent.getStringArrayExtra(EXTRA_HEADERS);
		if (headerLines != null && headerLines.length > 0) HEADERS.put(url, headerLines);
		final String title = intent.getStringExtra(EXTRA_TITLE);
		final boolean audio = intent.getBooleanExtra(EXTRA_AUDIO, false);
		final int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
		final long estimate = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0);
		final String jobId = "job" + System.currentTimeMillis();

		DownloadRegistry.Job fresh = new DownloadRegistry.Job();
		fresh.id = jobId;
		fresh.url = url;
		fresh.title = title == null ? "Video" : title;
		fresh.audio = audio;
		fresh.height = height;
		fresh.estimate = estimate;
		DownloadRegistry.add(fresh);
		DownloadSounds.added(this);
		launch(jobId, url, fresh.title, audio, height, estimate);
		return START_NOT_STICKY;
	}

	private void launch(String jobId, String url, String title, boolean audio, int height,
	                    long estimate) {
		active.incrementAndGet();
		pool.execute(() -> {
			try {
				runJob(jobId, url, title, audio, height, estimate);
			} catch (Throwable error) {
				CrashLogger.breadcrumb("download failed", error);
			} finally {
				active.decrementAndGet();
				finishIfIdle();
			}
		});
	}

	@Override
	public void onCreate() {
		super.onCreate();
		final Context app = getApplicationContext();
		new Thread(() -> cleanupOrphans(app)).start();
	}

	/**
	 * Deletes leftover partial downloads that no job in the list owns
	 * (app was killed, crash, old versions). Paused jobs are kept.
	 */
	public static void cleanupOrphans(Context context) {
		try {
			File base = context.getExternalFilesDir(null);
			if (base == null) base = context.getFilesDir();
			File[] dirs = new File(base, "tmp_downloads").listFiles();
			if (dirs == null) return;
			for (File dir : dirs) {
				if (DownloadRegistry.get(dir.getName()) == null) deleteRecursively(dir);
			}
		} catch (Throwable ignored) {
		}
	}

	private File workDirOf(String jobId) {
		File base = getExternalFilesDir(null);
		if (base == null) base = getFilesDir();
		return new File(base, "tmp_downloads/" + jobId);
	}

	@Override
	public void onDestroy() {
		pool.shutdownNow();
		super.onDestroy();
	}

	// ── Job ──────────────────────────────────────────────────────────

	private void runJob(String jobId, String url, String title, boolean audio, int height,
	                    long estimate) {
		File workDir = workDirOf(jobId);
		//noinspection ResultOfMethodCallIgnored
		workDir.mkdirs();
		boolean keepFiles = false; // only for paused jobs
		boolean keepEntry = false; // paused or failed: stays in the Active list

		if (cancelled.containsKey(jobId)) {
			DownloadRegistry.remove(jobId);
			cancelled.remove(jobId);
			deleteRecursively(workDir);
			return;
		}
		if (paused.containsKey(jobId)) {
			DownloadRegistry.setState(jobId, DownloadRegistry.PAUSED, "Paused");
			return; // keep the list entry; nothing downloaded yet
		}
		DownloadRegistry.setState(jobId, DownloadRegistry.RUNNING, "Starting…");

		try {
			YoutubeDLRequest request = new YoutubeDLRequest(url);
			request.addOption("--no-playlist");
			request.addOption("--no-mtime");
			request.addOption("--continue");
			request.addOption("--windows-filenames");
			request.addOption("--concurrent-fragments", "4");
			String[] extraHeaders = HEADERS.get(url);
			if (extraHeaders != null) {
				for (String line : extraHeaders) request.addOption("--add-header", line);
			}
			request.addOption("-o", workDir.getAbsolutePath() + "/"
				+ safeFileName(title).replace("%", "%%") + ".%(ext)s");

			if (audio) {
				request.addOption("-f", "ba[ext=m4a]/ba/b");
			} else if (height > 0) {
				request.addOption("-f", "bv*[height<=" + height + "][ext=mp4]+ba[ext=m4a]/bv*[height<="
					+ height + "]+ba/b[height<=" + height + "]/b");
				request.addOption("--merge-output-format", "mp4/mkv");
			} else {
				request.addOption("-f", "bv*+ba/b");
				request.addOption("--merge-output-format", "mp4/mkv");
			}

			final Tracker tracker = new Tracker(estimate);
			postProgress(jobId, title, tracker, true);

			boolean useNewPipe = NEWPIPE_JOBS.contains(jobId) && NewPipeEngine.isYouTube(url);
			if (!useNewPipe) {
				try {
				YtDlpRunner.execute(this, request, jobId, (percent, eta, line) -> {
					tracker.update(percent, line);
					long now = SystemClock.elapsedRealtime();
					if (now - lastNotifyTime < 500) return;
					lastNotifyTime = now;
					postProgress(jobId, title, tracker, false);
				});
				} catch (Throwable ytDlpError) {
					// Second engine: if yt-dlp cannot do a YouTube link, NewPipe takes over.
					if (stopRequested(jobId) || !NewPipeEngine.isYouTube(url)) throw ytDlpError;
					CrashLogger.breadcrumb("yt-dlp failed, switching to NewPipe: " + title, ytDlpError);
					NEWPIPE_JOBS.add(jobId);
					useNewPipe = true;
				}
			}
			if (useNewPipe) {
				deleteYtDlpLeftovers(workDir);
				DownloadRegistry.update(jobId, 0, "Switching to backup engine…");
				try {
					NewPipeDownloader.run(url, workDir, safeFileName(title), audio, height, estimate,
						new NewPipeDownloader.Sink() {
							@Override
							public void onBytes(long done, long total, long bytesPerSecond) {
								tracker.setManual(done, total, bytesPerSecond);
								long now = SystemClock.elapsedRealtime();
								if (now - lastNotifyTime < 500) return;
								lastNotifyTime = now;
								postProgress(jobId, title, tracker, false);
							}

							@Override
							public boolean stopped() {
								return stopRequested(jobId);
							}
						});
				} catch (NewPipeDownloader.Stopped stopped) {
					// pause / cancel: handled by the checks right below
				}
			}

			if (cancelled.containsKey(jobId)) return;
			if (paused.containsKey(jobId)) {
				keepFiles = true;
				keepEntry = true;
				DownloadRegistry.setState(jobId, DownloadRegistry.PAUSED, "Paused");
				return;
			}

			File result = findResultFile(workDir);
			if (result == null) throw new Exception("Download finished but no file was created.");

			Uri saved = saveToDownloads(result);
			DownloadHistory.Item item = new DownloadHistory.Item();
			item.title = title;
			item.uri = saved.toString();
			item.audio = audio;
			item.size = result.length();
			item.time = System.currentTimeMillis();
			DownloadHistory.add(this, item);

			DownloadSounds.finished(this);
			notifyDone(title, "Download complete", saved, mimeOf(result.getName(), audio));
		} catch (Throwable error) {
			if (cancelled.containsKey(jobId)) return;
			if (paused.containsKey(jobId)) {
				keepFiles = true;
				keepEntry = true;
				DownloadRegistry.setState(jobId, DownloadRegistry.PAUSED, "Paused");
				return;
			}
			CrashLogger.breadcrumb("download error: " + title, error);
			String message = error.getMessage() == null ? error.toString() : error.getMessage();
			int at = message.lastIndexOf("ERROR:");
			if (at >= 0) message = message.substring(at);
			if (message.length() > 160) message = message.substring(0, 160) + "…";
			keepEntry = true; // partial files are deleted below; Retry starts again
			DownloadRegistry.setState(jobId, DownloadRegistry.FAILED, "Failed: " + message);
			notifyDone(title, "Download failed: " + message, null, null);
		} finally {
			if (!keepEntry) DownloadRegistry.remove(jobId);
			if (!keepFiles) deleteRecursively(workDir);
			cancelled.remove(jobId);
			if (!keepEntry) NEWPIPE_JOBS.remove(jobId);
		}
	}

	private static File findResultFile(File dir) {
		File best = null;
		File[] files = dir.listFiles();
		if (files == null) return null;
		for (File file : files) {
			String name = file.getName().toLowerCase(Locale.US);
			if (!file.isFile() || name.endsWith(".part") || name.endsWith(".ytdl")
				|| name.endsWith(".temp") || name.endsWith(".json")) continue;
			if (best == null || file.length() > best.length()) best = file;
		}
		return best;
	}

	private Uri saveToDownloads(File source) throws Exception {
		String name = source.getName();
		ContentValues values = new ContentValues();
		values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
		values.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name, false));
		values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NextGen");
		values.put(MediaStore.MediaColumns.IS_PENDING, 1);
		Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
		if (uri == null) throw new Exception("Could not create the file in Downloads.");
		try (InputStream in = new FileInputStream(source);
		     OutputStream out = getContentResolver().openOutputStream(uri)) {
			if (out == null) throw new Exception("Could not write to Downloads.");
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
		} catch (Exception error) {
			getContentResolver().delete(uri, null, null);
			throw error;
		}
		ContentValues done = new ContentValues();
		done.put(MediaStore.MediaColumns.IS_PENDING, 0);
		getContentResolver().update(uri, done, null, null);
		return uri;
	}

	private static String mimeOf(String fileName, boolean audio) {
		int dot = fileName.lastIndexOf('.');
		String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.US) : "";
		String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
		if (mime != null) return mime;
		switch (ext) {
			case "m4a": return "audio/mp4";
			case "mkv": return "video/x-matroska";
			case "webm": return audio ? "audio/webm" : "video/webm";
			default: return audio ? "audio/*" : "video/*";
		}
	}

	private static void deleteRecursively(File file) {
		File[] children = file.listFiles();
		if (children != null) for (File child : children) deleteRecursively(child);
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	private static String formatEta(long seconds) {
		long m = seconds / 60;
		long s = seconds % 60;
		return m > 0 ? m + "m " + s + "s" : s + "s";
	}

	// ── Notifications ────────────────────────────────────────────────

	/** Tracks overall progress across the video and audio streams that yt-dlp downloads one by one. */
	private static final class Tracker {
		final long estimate;
		long completed = 0;
		long streamTotal = 0;
		float lastPercent = 0;
		long speedBps = 0;

		Tracker(long estimate) {
			this.estimate = estimate;
		}

		/** Progress reported directly (backup engine) instead of parsed from yt-dlp output. */
		void setManual(long done, long total, long speed) {
			completed = 0;
			streamTotal = Math.max(total, done);
			lastPercent = streamTotal > 0 ? done * 100f / streamTotal : 0f;
			speedBps = speed;
		}

		void update(float percent, String line) {
			if (percent < lastPercent - 20f) completed += streamTotal; // next stream started
			lastPercent = percent;
			Matcher total = P_TOTAL.matcher(line);
			if (total.find()) streamTotal = toBytes(total.group(1), total.group(2));
			Matcher speed = P_SPEED.matcher(line);
			speedBps = speed.find() ? toBytes(speed.group(1), speed.group(2)) : 0;
		}

		long downloaded() {
			return completed + (long) (Math.max(0f, lastPercent) / 100f * streamTotal);
		}

		long total() {
			long known = completed + streamTotal;
			return estimate > 0 ? Math.max(estimate, known) : known;
		}

		int percent() {
			long total = total();
			if (total <= 0) return Math.max(0, Math.min(100, Math.round(lastPercent)));
			return (int) Math.max(0, Math.min(100, downloaded() * 100 / total));
		}

		long etaSeconds() {
			long total = total();
			long done = downloaded();
			if (speedBps > 0 && total > done) return (total - done) / speedBps;
			return -1;
		}
	}

	private static long toBytes(String number, String unit) {
		try {
			double value = Double.parseDouble(number);
			String u = unit.toUpperCase(Locale.US);
			double factor = 1;
			if (u.startsWith("K")) factor = 1024d;
			else if (u.startsWith("M")) factor = 1024d * 1024d;
			else if (u.startsWith("G")) factor = 1024d * 1024d * 1024d;
			else if (u.startsWith("T")) factor = 1024d * 1024d * 1024d * 1024d;
			return (long) (value * factor);
		} catch (Exception error) {
			return 0;
		}
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
		if (bytes < 1024L * 1024L * 1024L)
			return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
		return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
	}

	private static String formatSpeed(long bytesPerSecond) {
		if (bytesPerSecond <= 0) return "—";
		return formatBytes(bytesPerSecond) + "/s";
	}

	/** File name from the (possibly renamed) title: no illegal characters, max ~200 bytes. */
	private static String safeFileName(String title) {
		String name = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", " ");
		name = name.replaceAll("\\s+", " ").trim();
		while (name.startsWith(".")) name = name.substring(1).trim();
		while (name.getBytes(StandardCharsets.UTF_8).length > 200 && name.length() > 1) {
			name = name.substring(0, name.length() - 1);
		}
		name = name.trim();
		return name.isEmpty() ? "video" : name;
	}

	private void postProgress(String jobId, String title, Tracker tracker, boolean starting) {
		NotificationManager manager = getSystemService(NotificationManager.class);
		if (manager == null) return;
		if (starting) {
			manager.notify(FOREGROUND_ID, buildProgress(jobId, title, "Starting…", "", "", 0, true));
			return;
		}
		int percent = tracker.percent();
		long total = tracker.total();
		long done = tracker.downloaded();
		String size = total > 0 ? formatBytes(done) + " / " + formatBytes(total) : formatBytes(done);
		boolean finishing = percent >= 100;
		long eta = tracker.etaSeconds();
		String etaText = finishing ? "Finalizing…" : (eta >= 0 ? formatEta(eta) + " left" : "…");
		String speed = finishing ? "" : formatSpeed(tracker.speedBps);
		manager.notify(FOREGROUND_ID, buildProgress(jobId, title, size, etaText, speed, percent, finishing));
		DownloadRegistry.update(jobId, percent, finishing ? "Finalizing…"
			: (speed.isEmpty() ? size : size + " • " + speed + " • " + etaText));
	}

	public static void pause(Context context, String jobId) {
		sendJobAction(context, ACTION_PAUSE, jobId);
	}

	/** Resumes a paused download, or retries a failed one (keeps already downloaded parts). */
	public static void resume(Context context, String jobId) {
		sendJobAction(context, ACTION_RESUME, jobId);
	}

	private static void sendJobAction(Context context, String action, String jobId) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.setAction(action);
		intent.putExtra(EXTRA_JOB_ID, jobId);
		androidx.core.content.ContextCompat.startForegroundService(context, intent);
	}

	/** Cancels a running download (used by the Downloads screen). */
	public static void cancel(Context context, String jobId) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.setAction(ACTION_CANCEL);
		intent.putExtra(EXTRA_JOB_ID, jobId);
		androidx.core.content.ContextCompat.startForegroundService(context, intent);
	}

	private void createChannel() {
		NotificationManager manager = getSystemService(NotificationManager.class);
		if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
		NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads",
			NotificationManager.IMPORTANCE_LOW);
		channel.setDescription("Download progress and results");
		manager.createNotificationChannel(channel);
	}

	/** Custom progress notification: title, bar, size / time left / speed, and a Cancel button. */
	private Notification buildProgress(String jobId, String title, String size, String eta,
	                                   String speed, int progress, boolean indeterminate) {
		RemoteViews small = new RemoteViews(getPackageName(), R.layout.notification_download_small);
		RemoteViews big = new RemoteViews(getPackageName(), R.layout.notification_download);
		fill(small, title, size, eta, speed, progress, indeterminate);
		fill(big, title, size, eta, speed, progress, indeterminate);

		if (jobId != null) {
			Intent cancel = new Intent(this, DownloadService.class);
			cancel.setAction(ACTION_CANCEL);
			cancel.putExtra(EXTRA_JOB_ID, jobId);
			PendingIntent cancelPending = PendingIntent.getService(this, jobId.hashCode(), cancel,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
			big.setOnClickPendingIntent(R.id.nd_cancel, cancelPending);

			Intent pause = new Intent(this, DownloadService.class);
			pause.setAction(ACTION_PAUSE);
			pause.putExtra(EXTRA_JOB_ID, jobId);
			PendingIntent pausePending = PendingIntent.getService(this, jobId.hashCode() + 1, pause,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
			big.setOnClickPendingIntent(R.id.nd_pause, pausePending);
		} else {
			big.setViewVisibility(R.id.nd_cancel, android.view.View.GONE);
			big.setViewVisibility(R.id.nd_pause, android.view.View.GONE);
		}

		// Fully custom (colourful) layout: no system decoration around it.
		return new Notification.Builder(this, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setColor(0xFFFF8A00)
			.setCustomContentView(small)
			.setCustomBigContentView(big)
			.setCategory(Notification.CATEGORY_PROGRESS)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.build();
	}

	private static void fill(RemoteViews views, String title, String size, String eta,
	                         String speed, int progress, boolean indeterminate) {
		views.setTextViewText(R.id.nd_title, title);
		views.setTextViewText(R.id.nd_size, size);
		views.setTextViewText(R.id.nd_eta, eta);
		views.setTextViewText(R.id.nd_speed, speed);
		views.setProgressBar(R.id.nd_bar, 100, progress, indeterminate);
		views.setTextViewText(R.id.nd_percent, indeterminate ? "…" : progress + "%");
	}

	private void notifyDone(String title, String message, Uri uri, String mime) {
		NotificationManager manager = getSystemService(NotificationManager.class);
		if (manager == null) return;
		Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
			.setSmallIcon(uri != null ? android.R.drawable.stat_sys_download_done
				: android.R.drawable.stat_notify_error)
			.setColor(0xFFFF8A00)
			.setContentTitle(title)
			.setContentText(message)
			.setStyle(new Notification.BigTextStyle().bigText(message))
			.setAutoCancel(true);
		if (uri != null) {
			Intent view;
			boolean playable = mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"));
			if (playable) {
				// Opens in the app's own player.
				view = userInterface.watchVideo.PlayRequest
					.file(uri, title, mime.startsWith("audio/")).toIntent(this);
				view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			} else {
				view = new Intent(Intent.ACTION_VIEW);
				view.setDataAndType(uri, mime == null ? "*/*" : mime);
				view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			builder.setContentIntent(PendingIntent.getActivity(this, NEXT_DONE_ID.get(), view,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
		}
		manager.notify(NEXT_DONE_ID.getAndIncrement(), builder.build());
	}

	private void finishIfIdle() {
		if (active.get() > 0) return;
		stopForeground(true);
		stopSelf(lastStartId);
	}

	/** Convenience used by the UI. */
	public static void start(Context context, String url, String title, boolean audio, int height,
	                         long totalBytes) {
		start(context, url, title, audio, height, totalBytes, null);
	}

	/** Same, with request headers ("Referer: ...", "Cookie: ...") needed by the link. */
	public static void start(Context context, String url, String title, boolean audio, int height,
	                         long totalBytes, String[] headers) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.setAction(ACTION_START);
		intent.putExtra(EXTRA_URL, url);
		intent.putExtra(EXTRA_TITLE, title);
		intent.putExtra(EXTRA_AUDIO, audio);
		intent.putExtra(EXTRA_HEIGHT, height);
		intent.putExtra(EXTRA_TOTAL_BYTES, totalBytes);
		if (headers != null) intent.putExtra(EXTRA_HEADERS, headers);
		androidx.core.content.ContextCompat.startForegroundService(context, intent);
	}
}
