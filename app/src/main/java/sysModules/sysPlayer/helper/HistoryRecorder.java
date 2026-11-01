package sysModules.sysPlayer.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.queue.PlayQueueItem;

public final class HistoryRecorder {
    private static final LoggerUtils logger = LoggerUtils.from(HistoryRecorder.class);
    private static final String PREFS_NAME = "playback_history";
    private static final String KEY_PROGRESS_PREFIX = "progress_";
    private static final String KEY_COMPLETED_PREFIX = "completed_";

    private HistoryRecorder() {}

    public static void recordProgress(@NonNull Context context,
                                       @NonNull PlayQueueItem item,
                                       long positionMillis,
                                       long durationMillis) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = KEY_PROGRESS_PREFIX + item.getUrl();
        prefs.edit()
                .putLong(key, positionMillis)
                .putLong(key + "_dur", durationMillis)
                .putLong(key + "_time", System.currentTimeMillis())
                .apply();

        if (durationMillis > 0 && positionMillis >= durationMillis - 2000) {
            prefs.edit().putBoolean(KEY_COMPLETED_PREFIX + item.getUrl(), true).apply();
        } else if (positionMillis > 3000) {
            prefs.edit().remove(KEY_COMPLETED_PREFIX + item.getUrl()).apply();
        }
    }

    public static void markCompleted(@NonNull Context context, @NonNull PlayQueueItem item) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED_PREFIX + item.getUrl(), true)
                .apply();
    }

    public static long getRecoveryPosition(@NonNull Context context, @NonNull PlayQueueItem item) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_COMPLETED_PREFIX + item.getUrl(), false)) {
            return PlayQueueItem.RECOVERY_UNSET;
        }
        return prefs.getLong(KEY_PROGRESS_PREFIX + item.getUrl(), PlayQueueItem.RECOVERY_UNSET);
    }
}
