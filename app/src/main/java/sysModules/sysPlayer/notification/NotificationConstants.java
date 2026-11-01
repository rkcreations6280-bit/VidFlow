package sysModules.sysPlayer.notification;

import com.nextgen.R;

public final class NotificationConstants {
    private NotificationConstants() {}

    public static final String CHANNEL_ID = "playback_channel";
    public static final int CHANNEL_IMPORTANCE = android.app.NotificationManager.IMPORTANCE_LOW;
    public static final int CHANNEL_NAME_RES = R.string.player_notification_channel_name;
    public static final int CHANNEL_DESC_RES = R.string.player_notification_channel_desc;

    public static final String ACTION_PLAY = "sysModules.player.action.PLAY";
    public static final String ACTION_PAUSE = "sysModules.player.action.PAUSE";
    public static final String ACTION_PLAY_PAUSE = "sysModules.player.action.PLAY_PAUSE";
    public static final String ACTION_NEXT = "sysModules.player.action.NEXT";
    public static final String ACTION_PREVIOUS = "sysModules.player.action.PREVIOUS";
    public static final String ACTION_STOP = "sysModules.player.action.STOP";
    public static final String ACTION_CLOSE = "sysModules.player.action.CLOSE";
    public static final String ACTION_CYCLE_REPEAT = "sysModules.player.action.CYCLE_REPEAT";
    public static final String ACTION_HOST_EXTERNAL = "sysModules.player.action.HOST_EXTERNAL";
    public static final String ACTION_LOAD_AND_PLAY = "sysModules.player.action.LOAD_AND_PLAY";

    public static final String EXTRA_PLAY_QUEUE = "sysModules.player.extra.PLAY_QUEUE";
    public static final String EXTRA_PLAYER_TYPE = "sysModules.player.extra.PLAYER_TYPE";
    public static final String EXTRA_START_POSITION = "sysModules.player.extra.START_POSITION";
    public static final String EXTRA_AUDIO_ONLY = "sysModules.player.extra.AUDIO_ONLY";

    public static final int NOTIFICATION_ID = 1001;

    public static final int REQUEST_CODE_PLAY = 100;
    public static final int REQUEST_CODE_PAUSE = 101;
    public static final int REQUEST_CODE_PLAY_PAUSE = 102;
    public static final int REQUEST_CODE_NEXT = 103;
    public static final int REQUEST_CODE_PREVIOUS = 104;
    public static final int REQUEST_CODE_STOP = 105;
    public static final int REQUEST_CODE_CLOSE = 106;
    public static final int REQUEST_CODE_CYCLE_REPEAT = 107;
}
