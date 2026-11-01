package sysModules.sysPlayer.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PendingPlaybackQueue {
	private static SinglePlayQueue pending;
	
	private PendingPlaybackQueue() {}
	
	public static synchronized void set(@NonNull SinglePlayQueue queue) {
		pending = queue;
	}
	
	@Nullable
	public static synchronized SinglePlayQueue consume() {
		SinglePlayQueue q = pending;
		pending = null;
		return q;
	}
}