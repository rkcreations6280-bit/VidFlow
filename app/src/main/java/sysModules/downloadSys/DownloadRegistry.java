package sysModules.downloadSys;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory list of downloads that are running right now. The Downloads screen observes it. */
public final class DownloadRegistry {

	public static final int QUEUED = 0;
	public static final int RUNNING = 1;
	public static final int PAUSED = 2;
	public static final int FAILED = 3;

	public static final class Job {
		public String id;
		public String url;
		public String title;
		public boolean audio;
		public int percent;
		public String status = "Waiting…";
		public int height;
		public long estimate;
		public int state = QUEUED;
		public String error = "";
		public long startedAt = System.currentTimeMillis();
	}

	public interface Listener {
		void onChanged();
	}

	private static final List<Job> JOBS = new ArrayList<>();
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final Handler MAIN = new Handler(Looper.getMainLooper());

	private DownloadRegistry() {}

	public static synchronized void add(Job job) {
		JOBS.add(job);
		fire();
	}

	public static synchronized void update(String id, int percent, String status) {
		for (Job job : JOBS) {
			if (job.id.equals(id)) {
				if (job.state == PAUSED || job.state == FAILED) return;
				job.percent = percent;
				job.status = status;
				break;
			}
		}
		fire();
	}

	public static synchronized void setState(String id, int state, String status) {
		for (Job job : JOBS) {
			if (job.id.equals(id)) {
				job.state = state;
				job.status = status;
				break;
			}
		}
		fire();
	}

	/** Returns a copy of the job, or null. */
	public static synchronized Job get(String id) {
		for (Job job : snapshot()) {
			if (job.id.equals(id)) return job;
		}
		return null;
	}

	public static synchronized void remove(String id) {
		for (int i = 0; i < JOBS.size(); i++) {
			if (JOBS.get(i).id.equals(id)) {
				JOBS.remove(i);
				break;
			}
		}
		fire();
	}

	public static synchronized List<Job> snapshot() {
		List<Job> copy = new ArrayList<>();
		for (Job job : JOBS) {
			Job c = new Job();
			c.id = job.id;
			c.url = job.url;
			c.title = job.title;
			c.audio = job.audio;
			c.percent = job.percent;
			c.status = job.status;
			c.startedAt = job.startedAt;
			c.height = job.height;
			c.estimate = job.estimate;
			c.state = job.state;
			c.error = job.error;
			copy.add(c);
		}
		return copy;
	}

	public static void addListener(Listener listener) {
		if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
	}

	public static void removeListener(Listener listener) {
		LISTENERS.remove(listener);
	}

	private static void fire() {
		MAIN.post(() -> {
			for (Listener listener : LISTENERS) listener.onChanged();
		});
	}
}
