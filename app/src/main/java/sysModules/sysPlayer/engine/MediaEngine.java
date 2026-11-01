package sysModules.sysPlayer.engine;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.video.VideoSize;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.model.PlaybackState;
import sysModules.sysPlayer.model.RepeatMode;
import sysModules.sysPlayer.queue.PlayQueue;
import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.helper.CustomRenderersFactory;
import sysModules.sysPlayer.resolver.AudioPlaybackResolver;
import sysModules.sysPlayer.resolver.PlayerDataSource;
import sysModules.sysPlayer.resolver.VideoPlaybackResolver;

public final class MediaEngine implements Player.Listener, AnalyticsListener {
    private static final LoggerUtils logger = LoggerUtils.from(MediaEngine.class);

    public static final int PROGRESS_LOOP_INTERVAL_MILLIS = 1000;
    private static final float MUTED_VOLUME = 0f;
    private static final float NORMAL_VOLUME = 1f;

    private final Context context;
    private final Handler mainHandler;

    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private CustomRenderersFactory renderFactory;
    private LoadController loadController;
    private PlayerDataSource dataSource;

    private final VideoPlaybackResolver videoResolver;
    private final AudioPlaybackResolver audioResolver;

    @Nullable private PlayQueue playQueue;

    private long lastFalseEndedRecoveryMs;

    private static final VideoPlaybackResolver.Config DEFAULT_CONFIG =
            new VideoPlaybackResolver.Config() {
                @Nullable
                @Override
                public MediaFormat getDefaultVideoFormat() {
                    return null;
                }

                @Nullable
                @Override
                public MediaFormat getDefaultAudioFormat() {
                    return null;
                }

                @Override
                public boolean showHigherResolutions() {
                    return true;
                }

                @Nullable
                @Override
                public String getPreferredAudioLanguage() {
                    return null;
                }

                @Override
                public boolean limitDataUsage() {
                    return false;
                }

                @Override
                public boolean preferVideoOnly() {
                    return false;
                }

                @Override
                public boolean preferOriginalAudio() {
                    return false;
                }

                @Override
                public boolean preferDescriptiveAudio() {
                    return false;
                }
            };

    private PlaybackState.Phase currentPhase = PlaybackState.Phase.PREFLIGHT;
    private RepeatMode repeatMode = RepeatMode.NONE;
    private boolean muted;
    private boolean audioOnly;

    @Nullable private PlayQueueItem currentItem;
    @Nullable private StreamInfo currentInfo;

    private final List<EngineCallbacks> callbacks = new CopyOnWriteArrayList<>();

    private boolean progressLoopRunning;
    private boolean released;
    private int audioSessionId;

    public MediaEngine(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.dataSource = new PlayerDataSource(this.context);
        this.loadController = new LoadController();

        this.trackSelector = new DefaultTrackSelector(this.context);
        this.renderFactory = new CustomRenderersFactory(this.context);

        this.videoResolver = new VideoPlaybackResolver(dataSource, DEFAULT_CONFIG);
        this.audioResolver = new AudioPlaybackResolver(dataSource);
    }

    // ─── Callbacks ───────────────────────────────────────────────────────────

    public void addCallback(@NonNull EngineCallbacks cb) {
        if (!callbacks.contains(cb)) callbacks.add(cb);
    }

    public void removeCallback(@NonNull EngineCallbacks cb) {
        callbacks.remove(cb);
    }

    public void setPlayQueue(@Nullable PlayQueue queue) {
        this.playQueue = queue;
    }

    // ─── Playback lifecycle ─────────────────────────────────────────────────

    public void load(@NonNull StreamInfo info, long startPosition) {
        this.currentInfo = info;

        MediaSource mediaSource;
        if (audioOnly) {
            mediaSource = audioResolver.resolve(info);
        } else {
            mediaSource = videoResolver.resolve(info);
        }

        if (mediaSource == null) {
            notifyError(new RuntimeException("No playable streams found for " + info.getName()), false);
            return;
        }

        ensurePlayerReady();

        if (currentItem != null) {
            exoPlayer.setMediaSource(mediaSource, startPosition > 0 ? startPosition : C.TIME_UNSET);
        } else {
            exoPlayer.setMediaSource(mediaSource);
            if (startPosition > 0) exoPlayer.seekTo(startPosition);
        }

        exoPlayer.prepare();
        setState(PlaybackState.Phase.BUFFERING);
        notifyMetadataChanged();
    }

    public void load(@NonNull MediaItem mediaItem, long startPosition) {
        this.currentInfo = null;
        ensurePlayerReady();

        if (currentItem != null && startPosition > 0) {
            exoPlayer.setMediaItem(mediaItem, startPosition);
        } else {
            exoPlayer.setMediaItem(mediaItem);
            if (startPosition > 0) exoPlayer.seekTo(startPosition);
        }

        exoPlayer.prepare();
        setState(PlaybackState.Phase.BUFFERING);
        notifyMetadataChanged();
    }

    /**
     * Resolves a {@link StreamInfo} into a {@link MediaSource} using the appropriate
     * resolver (video or audio) without loading it into the player. Used by the
     * {@link sysModules.sysPlayer.playback.MediaSourceManager} to resolve streams for
     * preloaded sources.
     *
     * @param info the stream info to resolve
     * @return the resolved media source, or null if resolution failed
     */
    @Nullable
    public MediaSource resolveSource(@NonNull StreamInfo info) {
        if (dataSource == null || exoPlayer == null) return null;
        if (audioOnly) {
            return audioResolver.resolve(info);
        } else {
            return videoResolver.resolve(info);
        }
    }

    /**
     * Sets a media source on the player and prepares it for playback. Used by
     * the {@link sysModules.sysPlayer.playback.MediaSourceManager} when unblocking
     * with a new source.
     *
     * @param mediaSource the source to play
     */
    public void setMediaSourceAndPrepare(@NonNull MediaSource mediaSource) {
        ensurePlayerReady();
        exoPlayer.setMediaSource(mediaSource, false);
        exoPlayer.prepare();
        setState(PlaybackState.Phase.BUFFERING);
    }

    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        setState(PlaybackState.Phase.PREFLIGHT);
    }

    public void release() {
        released = true;
        stopProgressLoop();
        if (exoPlayer != null) {
            notifyAudioSessionUpdate(false);
            exoPlayer.removeListener(this);
            exoPlayer.removeAnalyticsListener(this);
            exoPlayer.release();
            exoPlayer = null;
        }
        trackSelector = null;
        renderFactory = null;
        loadController = null;
        if (dataSource != null) {
            dataSource.release();
            dataSource = null;
        }
        currentItem = null;
        currentInfo = null;
        callbacks.clear();
    }

    // ─── Playback controls ─────────────────────────────────────────────────

    public void play() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
        }
    }

    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            if (currentPhase == PlaybackState.Phase.PLAYING) {
                setState(PlaybackState.Phase.PAUSED);
            }
        }
    }

    public void playPause() {
        if (isPlaying()) pause(); else play();
    }

    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            if (currentPhase == PlaybackState.Phase.PAUSED) {
                setState(PlaybackState.Phase.PAUSED_SEEK);
            }
            exoPlayer.seekTo(positionMs);
        }
    }

    public void playPrevious() {
        if (playQueue == null) return;
        setState(PlaybackState.Phase.BUFFERING);
        playQueue.previous();
    }

    public void playNext() {
        if (playQueue == null) return;
        setState(PlaybackState.Phase.BUFFERING);
        playQueue.offsetIndex(1);
    }

    public void fastForward() {
        if (exoPlayer != null) {
            seekTo(exoPlayer.getCurrentPosition() + 10_000);
        }
    }

    public void fastRewind() {
        if (exoPlayer != null) {
            seekTo(Math.max(0, exoPlayer.getCurrentPosition() - 10_000));
        }
    }

    // ─── Parameters ─────────────────────────────────────────────────────────

    public void setPlaybackSpeed(float speed) {
        if (exoPlayer != null) {
            PlaybackParameters params = exoPlayer.getPlaybackParameters();
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, params.pitch));
            notifyProgress();
        }
    }

    public void setPlaybackPitch(float pitch) {
        if (exoPlayer != null) {
            PlaybackParameters params = exoPlayer.getPlaybackParameters();
            exoPlayer.setPlaybackParameters(new PlaybackParameters(params.speed, pitch));
            notifyProgress();
        }
    }

    public void setPlaybackParameters(float speed, float pitch) {
        if (exoPlayer != null) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, pitch));
            notifyProgress();
        }
    }

    public float getPlaybackSpeed() {
        if (exoPlayer != null) {
            return exoPlayer.getPlaybackParameters().speed;
        }
        return 1.0f;
    }

    public float getPlaybackPitch() {
        if (exoPlayer != null) {
            return exoPlayer.getPlaybackParameters().pitch;
        }
        return 1.0f;
    }

    public boolean isAudioOnly() { return audioOnly; }
    public void setAudioOnly(boolean audioOnly) { this.audioOnly = audioOnly; }

    public boolean isMuted() { return muted; }

    public void setMuted(boolean mute) {
        this.muted = mute;
        setVolume(mute ? MUTED_VOLUME : NORMAL_VOLUME);
        notifyProgress();
    }

    public void toggleMute() {
        setMuted(!muted);
    }

    public void setVolume(float volume) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(volume);
        }
    }

    // ─── Queue integration ──────────────────────────────────────────────────

    public void cycleRepeatMode() {
        notifyProgress();
        repeatMode = repeatMode.cycleNext();
        notifyRepeatModeChanged();
        if (exoPlayer != null) {
            switch (repeatMode) {
                case NONE: exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF); break;
                case ONE:  exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE); break;
                case ALL:  exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL); break;
            }
        }
    }

    public void setRepeatMode(@NonNull RepeatMode mode) {
        this.repeatMode = mode;
        notifyRepeatModeChanged();
        if (exoPlayer != null) {
            switch (mode) {
                case NONE: exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF); break;
                case ONE:  exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE); break;
                case ALL:  exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL); break;
            }
        }
    }

    public RepeatMode getRepeatMode() { return repeatMode; }

    // ─── Stream selection ───────────────────────────────────────────────────

    public void setPlaybackQuality(@Nullable String quality) {
        videoResolver.setPlaybackQuality(quality);
    }

    @Nullable
    public String getPlaybackQuality() {
        return videoResolver.getPlaybackQuality();
    }

    public void setVideoQuality(int streamIndex) {
        if (trackSelector != null) {
            MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
            if (info != null) {
                for (int renderer = 0; renderer < info.getRendererCount(); renderer++) {
                    if (info.getRendererType(renderer) == C.TRACK_TYPE_VIDEO) {
                        trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                        .setSelectionOverride(renderer, info.getTrackGroups(renderer),
                                                new DefaultTrackSelector.SelectionOverride(
                                                        streamIndex, 0)));
                        break;
                    }
                }
            }
        }
    }

    public void setAudioTrack(int streamIndex) {
        if (trackSelector != null) {
            MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
            if (info != null) {
                for (int renderer = 0; renderer < info.getRendererCount(); renderer++) {
                    if (info.getRendererType(renderer) == C.TRACK_TYPE_AUDIO) {
                        trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                        .setSelectionOverride(renderer, info.getTrackGroups(renderer),
                                                new DefaultTrackSelector.SelectionOverride(
                                                        streamIndex, 0)));
                        break;
                    }
                }
            }
        }
    }

    public void setCaptionLanguage(@Nullable String language) {
        if (trackSelector != null) {
            DefaultTrackSelector.Parameters.Builder params =
                    trackSelector.buildUponParameters();
            if (language != null) {
                params.setPreferredTextLanguage(language);
                params.setRendererDisabled(C.TRACK_TYPE_TEXT, false);
            } else {
                params.setRendererDisabled(C.TRACK_TYPE_TEXT, true);
            }
            trackSelector.setParameters(params.build());
        }
    }

    public void disableCaptions() {
        if (trackSelector != null) {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                            .build());
        }
    }

    // ─── Video ──────────────────────────────────────────────────────────────

    public void setResizeMode(@AspectRatioFrameLayout.ResizeMode int mode) {
        notifyProgress();
    }

    public boolean isPlaying() {
        return exoPlayer != null
                && exoPlayer.getPlaybackState() == Player.STATE_READY
                && exoPlayer.getPlayWhenReady();
    }

    public boolean isPlayingOrBuffering() {
        return currentPhase == PlaybackState.Phase.PLAYING
                || currentPhase == PlaybackState.Phase.BUFFERING;
    }

    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return exoPlayer != null ? exoPlayer.getDuration() : 0;
    }

    public int getBufferedPercentage() {
        return exoPlayer != null ? exoPlayer.getBufferedPercentage() : 0;
    }

    // ─── Internal ───────────────────────────────────────────────────────────

    private void ensurePlayerReady() {
        if (released || exoPlayer != null) return;
        if (renderFactory == null || trackSelector == null || loadController == null) return;
        exoPlayer = new ExoPlayer.Builder(context, renderFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadController)
                .build();
        exoPlayer.addListener(this);
        exoPlayer.addAnalyticsListener(this);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.setHandleAudioBecomingNoisy(true);
    }

    @Nullable
    public ExoPlayer getExoPlayer() {
        if (released) return null;
        ensurePlayerReady();
        return exoPlayer;
    }

    private void setState(PlaybackState.Phase newPhase) {
        if (currentPhase == newPhase) return;
        if (!currentPhase.canTransitionTo(newPhase)) {
            logger.warning("Invalid state transition: " + currentPhase + " → " + newPhase);
        }
        currentPhase = newPhase;
        for (EngineCallbacks cb : callbacks) {
            cb.onStateChanged(currentPhase);
        }
        if (newPhase == PlaybackState.Phase.PLAYING
                || newPhase == PlaybackState.Phase.BUFFERING) {
            startProgressLoop();
        } else {
            stopProgressLoop();
        }
    }

    public PlaybackState.Phase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentItem(@Nullable PlayQueueItem item) {
        this.currentItem = item;
        if (item != null) {
            this.currentInfo = item.getStreamInfo();
        }
        notifyMetadataChanged();
    }

    @Nullable
    public PlayQueueItem getCurrentItem() {
        return currentItem;
    }

    @Nullable
    public StreamInfo getCurrentInfo() {
        return currentInfo;
    }

    private void notifyMetadataChanged() {
        if (currentItem == null) return;
        for (EngineCallbacks cb : callbacks) {
            cb.onMetadataChanged(currentItem, currentInfo);
        }
    }

    private void notifyRepeatModeChanged() {
        for (EngineCallbacks cb : callbacks) {
            cb.onRepeatModeChanged(repeatMode);
        }
    }

    private void notifyProgress() {
        long pos = getCurrentPosition();
        long dur = getDuration();
        int buf = getBufferedPercentage();
        for (EngineCallbacks cb : callbacks) {
            cb.onProgressChanged(pos, dur, buf);
        }
    }

    private void notifyError(Throwable error, boolean recoverable) {
        for (EngineCallbacks cb : callbacks) {
            cb.onError(error, recoverable);
        }
    }

    public void onPlayerError(@NonNull Throwable error) {
        notifyError(error, true);
    }

    private void notifyAudioSessionUpdate(boolean active) {
        if (audioSessionId == 0) return;
        Intent intent = new Intent(active
                ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }

    // ─── Progress loop ─────────────────────────────────────────────────────

    private void startProgressLoop() {
        if (progressLoopRunning) return;
        progressLoopRunning = true;
        mainHandler.post(progressRunnable);
    }

    private void stopProgressLoop() {
        progressLoopRunning = false;
        mainHandler.removeCallbacks(progressRunnable);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!progressLoopRunning || exoPlayer == null) return;
            notifyProgress();
            mainHandler.postDelayed(this, PROGRESS_LOOP_INTERVAL_MILLIS);
        }
    };

    // ─── Player.Listener ───────────────────────────────────────────────────

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_IDLE:
                setState(PlaybackState.Phase.PREFLIGHT);
                break;
            case Player.STATE_BUFFERING:
                setState(PlaybackState.Phase.BUFFERING);
                break;
            case Player.STATE_READY:
                if (exoPlayer != null && exoPlayer.getPlayWhenReady()) {
                    setState(PlaybackState.Phase.PLAYING);
                } else {
                    setState(PlaybackState.Phase.PAUSED);
                }
                break;
            case Player.STATE_ENDED:
                if (exoPlayer != null) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastFalseEndedRecoveryMs < 5000) {
                        setState(PlaybackState.Phase.COMPLETED);
                        break;
                    }
                    long pos = exoPlayer.getCurrentPosition();
                    long dur = exoPlayer.getDuration();
                    if (dur > 0 && pos < dur - 1000) {
                        lastFalseEndedRecoveryMs = now;
                        exoPlayer.stop();
                        exoPlayer.prepare();
                        exoPlayer.seekTo(pos);
                        break;
                    }
                }
                setState(PlaybackState.Phase.COMPLETED);
                break;
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        for (EngineCallbacks cb : callbacks) {
            cb.onPlayWhenReadyChanged(playWhenReady, reason);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        for (EngineCallbacks cb : callbacks) {
            cb.onIsPlayingChanged(isPlaying);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        logger.error("Player error: " + error.getMessage());
        notifyError(error, true);
        setState(PlaybackState.Phase.BLOCKED);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        for (EngineCallbacks cb : callbacks) {
            cb.onVideoSizeChanged(videoSize.width, videoSize.height);
        }
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        for (EngineCallbacks cb : callbacks) {
            cb.onTracksChanged(tracks);
        }
    }

    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
        for (EngineCallbacks cb : callbacks) {
            cb.onCues(cueGroup);
        }
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull PlaybackParameters params) {
        notifyProgress();
    }

    // ─── AnalyticsListener (audio session for equalizers) ──────────────────

    @Override
    public void onAudioSessionIdChanged(@NonNull EventTime eventTime, int audioSessionId) {
        this.audioSessionId = audioSessionId;
        notifyAudioSessionUpdate(true);
    }

    public Context getContext() {
        return context;
    }

    public PlayerDataSource getDataSource() {
        return dataSource;
    }
}
