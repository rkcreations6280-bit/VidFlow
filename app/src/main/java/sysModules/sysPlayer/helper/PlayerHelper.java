package sysModules.sysPlayer.helper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.queue.SinglePlayQueue;

public final class PlayerHelper {

    public static final int AUTOPLAY_TYPE_ALWAYS = 0;
    public static final int AUTOPLAY_TYPE_WIFI = 1;
    public static final int AUTOPLAY_TYPE_NEVER = 2;

    private PlayerHelper() {}

    // ─── Time formatting ──────────────────────────────────────────────────

    @NonNull
    public static String getTimeString(final long milliSeconds) {
        if (milliSeconds < 0) return "00:00";
        final long seconds = (milliSeconds % 60000) / 1000;
        final long minutes = (milliSeconds % 3600000) / 60000;
        final long hours = (milliSeconds % 86400000) / 3600000;
        final long days = milliSeconds / 86400000;

        if (days > 0) {
            return String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    @NonNull
    public static String formatSpeed(final float speed) {
        if (speed == (long) speed) {
            return String.format(Locale.US, "%dx", (long) speed);
        }
        return String.format(Locale.US, "%.2fx", speed).replaceAll("\\.?0*x$", "x");
    }

    @NonNull
    public static String formatPitch(final float pitch) {
        final double semitones = 12.0 * Math.log(pitch) / Math.log(2);
        return String.format(Locale.US, "%.1f st (%.2fx)", semitones, pitch);
    }

    // ─── Playback speeds ──────────────────────────────────────────────────

    public static int[] getPlaybackSpeeds() {
        return new int[]{25, 50, 75, 100, 125, 150, 175, 200, 300, 400, 500};
    }

    public static float speedForIndex(final int index) {
        final int[] speeds = getPlaybackSpeeds();
        if (index < 0 || index >= speeds.length) return 1.0f;
        return speeds[index] / 100.0f;
    }

    public static int indexForSpeed(final float speed) {
        final int[] speeds = getPlaybackSpeeds();
        int closest = 3;
        float minDiff = Math.abs(speed - 1.0f);
        for (int i = 0; i < speeds.length; i++) {
            final float diff = Math.abs(speed - speeds[i] / 100.0f);
            if (diff < minDiff) {
                minDiff = diff;
                closest = i;
            }
        }
        return closest;
    }

    // ─── Track selection ──────────────────────────────────────────────────

    /**
     * Creates an {@link ExoTrackSelection.Factory} for adaptive track selection.
     * To enforce a maximum video height, use {@code DefaultTrackSelector.Parameters}
     * with {@code setMaxVideoSize()} instead.
     *
     * @return the track selection factory
     */
    @NonNull
    public static ExoTrackSelection.Factory getQualitySelector() {
        return new AdaptiveTrackSelection.Factory();
    }

    // ─── Seek parameters ──────────────────────────────────────────────────

    /**
     * Returns the seek parameters for the given seek duration preference.
     *
     * @param seekDurationMillis the seek duration in milliseconds
     * @return appropriate {@link SeekParameters}
     */
    @NonNull
    public static SeekParameters getSeekParameters(final long seekDurationMillis) {
        if (seekDurationMillis <= 5000) {
            return SeekParameters.CLOSEST_SYNC;
        } else if (seekDurationMillis <= 15000) {
            return SeekParameters.NEXT_SYNC;
        } else {
            return SeekParameters.PREVIOUS_SYNC;
        }
    }

    // ─── Network helpers ──────────────────────────────────────────────────

    /**
     * Checks if the device is connected to a WiFi network.
     */
    public static boolean hasWifi(@NonNull final Context context) {
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        final NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    // ─── Auto-queue ───────────────────────────────────────────────────────

    /**
     * Given a {@link StreamInfo} and existing queue items, provides the next
     * video for auto-queueing from related items. Detects and prevents cycles
     * by checking if a candidate's URL already exists in the queue.
     *
     * @param info          currently playing stream
     * @param existingItems existing items in the queue
     * @return a {@link SinglePlayQueue} with the next stream, or null
     */
    @Nullable
    public static SinglePlayQueue autoQueueOf(@NonNull final StreamInfo info,
                                              @NonNull final List<PlayQueueItem> existingItems) {
        final Set<String> urls = existingItems.stream()
                .map(PlayQueueItem::getUrl)
                .collect(Collectors.toUnmodifiableSet());

        final List<InfoItem> relatedItems = info.getRelatedItems();
        if (relatedItems == null || relatedItems.isEmpty()) {
            return null;
        }

        // Try first related item
        if (relatedItems.get(0) instanceof StreamInfoItem
                && !urls.contains(relatedItems.get(0).getUrl())) {
            final StreamInfoItem item = (StreamInfoItem) relatedItems.get(0);
            return new SinglePlayQueue(new PlayQueueItem(item));
        }

        // Shuffle and pick a non-duplicate
        final List<StreamInfoItem> candidates = new ArrayList<>();
        for (final InfoItem item : relatedItems) {
            if (item instanceof StreamInfoItem && !urls.contains(item.getUrl())) {
                candidates.add((StreamInfoItem) item);
            }
        }
        Collections.shuffle(candidates);
        return candidates.isEmpty()
                ? null
                : new SinglePlayQueue(new PlayQueueItem(candidates.get(0)));
    }

    // ─── Live stream helpers ──────────────────────────────────────────────

    public static boolean isLiveStream(final long duration) {
        return duration == Long.MAX_VALUE || duration == -1;
    }

    public static boolean isLiveStream(@NonNull final org.schabi.newpipe.extractor.stream.StreamType type) {
        return type == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
                || type == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM;
    }
}
