package sysModules.sysPlayer.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.text.CueGroup;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import sysModules.sysPlayer.model.PlaybackState;
import sysModules.sysPlayer.model.RepeatMode;
import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * Callback interface for receiving playback events and state updates from a
 * media playback engine. Implementers (typically activities or fragments) can
 * observe changes including playback phase transitions, progress updates,
 * metadata loading, video dimension changes, error notifications, track changes,
 * subtitle cues, and playback state flags.
 *
 * <p><strong>Callback methods overview:</strong>
 * <ul>
 * <li>{@link #onStateChanged(PlaybackState.Phase)} – Playback phase (buffering,
 *     ready, ended, etc.).</li>
 * <li>{@link #onProgressChanged(long, long, int)} – Current position, duration,
 *     and buffer percentage.</li>
 * <li>{@link #onMetadataChanged(PlayQueueItem, StreamInfo)} – Queue item or
 *     stream info updates.</li>
 * <li>{@link #onVideoSizeChanged(int, int)} – Video dimensions (for UI scaling).</li>
 * <li>{@link #onError(Throwable, boolean)} – Error events with recoverable flag.</li>
 * <li>{@link #onTracksChanged(Tracks)} – Available audio/video/text tracks.</li>
 * <li>{@link #onCues(CueGroup)} – Subtitle or closed caption cues.</li>
 * <li>{@link #onIsPlayingChanged(boolean)} – Current playing state.</li>
 * <li>{@link #onPlayWhenReadyChanged(boolean, int)} – Play-when-ready flag with
 *     reason code.</li>
 * </ul>
 *
 * @see MediaEngine
 * @see PlaybackState.Phase
 * @see PlayQueueItem
 * @see StreamInfo
 */
@SuppressWarnings("ALL")
public interface EngineCallbacks {
	void onStateChanged(@NonNull PlaybackState.Phase phase);
	void onProgressChanged(long position, long duration, int bufferPercent);
	void onMetadataChanged(@NonNull PlayQueueItem item, @Nullable StreamInfo info);
	void onVideoSizeChanged(int width, int height);
	void onError(@NonNull Throwable error, boolean recoverable);
	void onTracksChanged(@NonNull Tracks tracks);
	void onCues(@NonNull CueGroup cueGroup);
	void onIsPlayingChanged(boolean isPlaying);
	void onPlayWhenReadyChanged(boolean playWhenReady, int reason);
	void onRepeatModeChanged(@NonNull RepeatMode mode);
}