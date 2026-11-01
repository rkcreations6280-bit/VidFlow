package sysModules.sysPlayer.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import sysModules.sysPlayer.service.PlaybackService;

public final class NotificationActions {
    private NotificationActions() {}

    public static PendingIntent play(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_PLAY,
                NotificationConstants.REQUEST_CODE_PLAY);
    }

    public static PendingIntent pause(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_PAUSE,
                NotificationConstants.REQUEST_CODE_PAUSE);
    }

    public static PendingIntent playPause(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_PLAY_PAUSE,
                NotificationConstants.REQUEST_CODE_PLAY_PAUSE);
    }

    public static PendingIntent next(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_NEXT,
                NotificationConstants.REQUEST_CODE_NEXT);
    }

    public static PendingIntent previous(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_PREVIOUS,
                NotificationConstants.REQUEST_CODE_PREVIOUS);
    }

    public static PendingIntent stop(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_STOP,
                NotificationConstants.REQUEST_CODE_STOP);
    }

    public static PendingIntent close(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_CLOSE,
                NotificationConstants.REQUEST_CODE_CLOSE);
    }

    public static PendingIntent cycleRepeat(@NonNull Context context) {
        return build(context, NotificationConstants.ACTION_CYCLE_REPEAT,
                NotificationConstants.REQUEST_CODE_CYCLE_REPEAT);
    }

    private static PendingIntent build(@NonNull Context context, @NonNull String action, int requestCode) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
}
