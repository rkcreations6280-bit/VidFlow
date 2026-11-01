package sysModules.sysPlayer.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;

import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.text.CueGroup;

import com.nextgen.R;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coreUtils.library.process.LoggerUtils;
import sysModules.newPipeLib.parsers.YtSteamExtractor;
import sysModules.sysPlayer.engine.EngineCallbacks;
import sysModules.sysPlayer.model.PlaybackState;
import sysModules.sysPlayer.model.RepeatMode;
import sysModules.sysPlayer.queue.PlayQueueItem;

public final class PlaybackNotification implements EngineCallbacks {
    private static final LoggerUtils logger = LoggerUtils.from(PlaybackNotification.class);
    private static final long NOTIFICATION_DEBOUNCE_MILLIS = 200L;

    private final Context context;
    private final NotificationManager notificationManager;
    private final Handler mainHandler;
    private final ExecutorService imageLoader;
    @Nullable private MediaSessionCompat.Token sessionToken;
    private boolean started;
    private boolean isPlaying;
    private RepeatMode currentRepeatMode = RepeatMode.NONE;

    @Nullable private String currentTitle;
    @Nullable private String currentUploader;
    @Nullable private Bitmap currentAlbumArt;
    @Nullable private String lastArtUrl;

    private boolean notificationPending;
    private final Runnable debouncedShow = () -> {
        notificationPending = false;
        showNotification(isPlaying);
    };

    public PlaybackNotification(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager)
                this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.imageLoader = Executors.newSingleThreadExecutor();
        createChannel();
    }

    public void setSessionToken(@Nullable MediaSessionCompat.Token token) {
        this.sessionToken = token;
    }

    public void start() {
        started = true;
        showNotification(false);
    }

    public void stop() {
        started = false;
        imageLoader.shutdownNow();
        notificationManager.cancel(NotificationConstants.NOTIFICATION_ID);
    }

    public void post() {
        if (started) {
            showNotification(isPlaying);
        }
    }

    private void showNotification(boolean playing) {
        mainHandler.removeCallbacks(debouncedShow);
        notificationPending = false;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, NotificationConstants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentTitle(currentTitle != null ? currentTitle :
                        context.getString(R.string.player_no_title))
                .setContentText(currentUploader)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(buildOpenPlayerIntent())
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT < 33 && progressDuration > 0) {
            // Android 12 and older do not draw the media session's seek bar; show a plain one.
            builder.setProgress((int) Math.min(Integer.MAX_VALUE, progressDuration),
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0, progressPosition)), false);
        }

        if (sessionToken != null) {
            builder.setStyle(new MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2));
        }

        Bitmap art = currentAlbumArt;
        if (art != null) {
            builder.setLargeIcon(art);
        }

        // Order: Repeat, Previous, Play/Pause, Next, Cancel
        builder.addAction(buildRepeatAction());
        builder.addAction(buildPreviousAction());
        if (playing) {
            builder.addAction(buildPauseAction());
        } else {
            builder.addAction(buildPlayAction());
        }
        builder.addAction(buildNextAction());
        builder.addAction(buildCloseAction());

        notificationManager.notify(NotificationConstants.NOTIFICATION_ID, builder.build());
    }

    private long progressPosition = 0;
    private long progressDuration = 0;

    /** Position for the notification's own progress bar (Android 12 and older). */
    public void setProgress(long position, long duration) {
        if (Build.VERSION.SDK_INT >= 33) return;
        progressPosition = position;
        progressDuration = duration;
        if (started) showNotification(isPlaying);
    }

    /** Tapping the notification opens the VidFlow player on the session that is playing. */
    private android.app.PendingIntent buildOpenPlayerIntent() {
        Intent intent = new Intent();
        intent.setClassName(context, "userInterface.watchVideo.WatchVideoActivity");
        intent.putExtra("pr_resume", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        return android.app.PendingIntent.getActivity(context, 4711, intent, flags);
    }

    private void scheduleNotification() {
        if (!started) return;
        if (!notificationPending) {
            notificationPending = true;
            mainHandler.postDelayed(debouncedShow, NOTIFICATION_DEBOUNCE_MILLIS);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationConstants.CHANNEL_ID,
                    context.getString(NotificationConstants.CHANNEL_NAME_RES),
                    NotificationConstants.CHANNEL_IMPORTANCE);
            channel.setDescription(
                    context.getString(NotificationConstants.CHANNEL_DESC_RES));
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Action buildPlayAction() {
        return new NotificationCompat.Action(
                R.drawable.ic_play_arrow,
                context.getString(R.string.player_notification_play),
                NotificationActions.playPause(context));
    }

    private NotificationCompat.Action buildPauseAction() {
        return new NotificationCompat.Action(
                R.drawable.ic_pause_solid,
                context.getString(R.string.player_notification_pause),
                NotificationActions.playPause(context));
    }

    private NotificationCompat.Action buildNextAction() {
        return new NotificationCompat.Action(
                R.drawable.ic_skip_next,
                context.getString(R.string.player_notification_next),
                NotificationActions.next(context));
    }

    private NotificationCompat.Action buildPreviousAction() {
        return new NotificationCompat.Action(
                R.drawable.ic_skip_previous,
                context.getString(R.string.player_notification_previous),
                NotificationActions.previous(context));
    }

    private NotificationCompat.Action buildCloseAction() {
        return new NotificationCompat.Action(
                R.drawable.ic_close_cross,
                context.getString(R.string.player_notification_close),
                NotificationActions.close(context));
    }

    private NotificationCompat.Action buildRepeatAction() {
        int icon;
        switch (currentRepeatMode) {
            case ONE:
                icon = R.drawable.ic_repeat_one;
                break;
            case ALL:
                icon = R.drawable.ic_repeat_all;
                break;
            case NONE:
            default:
                icon = R.drawable.ic_repeat_disable;
                break;
        }
        return new NotificationCompat.Action(
                icon,
                context.getString(R.string.player_notification_repeat),
                NotificationActions.cycleRepeat(context));
    }

    /**
     * Loads album art bitmap from a URL in a background thread.
     * Updates the notification when the image is loaded.
     */
    private void loadAlbumArt(@NonNull String imageUrl) {
        imageLoader.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) {
                    Bitmap raw = BitmapFactory.decodeStream(in);
                    if (raw != null) {
                        final Bitmap scaled = Bitmap.createScaledBitmap(raw, 128, 128, true);
                        mainHandler.post(() -> {
                            currentAlbumArt = scaled;
                            if (started) {
                                showNotification(isPlaying);
                            }
                        });
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                logger.debug("Failed to load album art: " + e.getMessage());
            }
        });
    }

    /** Title / artist / art for a player that was not started from a play queue (the VidFlow player). */
    public void setExternal(@Nullable String title, @Nullable String uploader, @Nullable String artUrl) {
        boolean sameArt = currentAlbumArt != null && artUrl != null && artUrl.equals(lastArtUrl);
        currentTitle = title;
        currentUploader = uploader;
        if (!sameArt) currentAlbumArt = null;
        if (artUrl != null && !artUrl.isEmpty() && !sameArt) {
            lastArtUrl = artUrl;
            loadAlbumArt(artUrl);
        }
        if (started) showNotification(isPlaying);
    }

    @Override
    public void onStateChanged(@NonNull PlaybackState.Phase phase) {
        isPlaying = phase == PlaybackState.Phase.PLAYING
                || phase == PlaybackState.Phase.BUFFERING;
        scheduleNotification();
    }

    @Override
    public void onProgressChanged(long position, long duration, int bufferPercent) {
    }

    @Override
    public void onMetadataChanged(@NonNull PlayQueueItem item, @Nullable StreamInfo info) {
        currentTitle = item.getTitle();
        currentUploader = item.getUploader();
        currentAlbumArt = null;

        // Load album art from thumbnails
        List<Image> thumbnails = item.getThumbnails();
        if (!thumbnails.isEmpty()) {
            String artUrl = YtSteamExtractor.getBestImageQuality(thumbnails);
            loadAlbumArt(artUrl);
        }

        if (started) {
            showNotification(isPlaying);
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
    }

    @Override
    public void onError(@NonNull Throwable error, boolean recoverable) {
        logger.error("Playback error: " + error.getMessage());
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
    }

    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
    }

    @Override
    public void onIsPlayingChanged(boolean playing) {
        isPlaying = playing;
        scheduleNotification();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
    }

    @Override
    public void onRepeatModeChanged(@NonNull RepeatMode mode) {
        currentRepeatMode = mode;
        scheduleNotification();
    }
}
