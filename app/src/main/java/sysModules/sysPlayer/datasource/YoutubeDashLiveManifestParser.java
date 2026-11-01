package sysModules.sysPlayer.datasource;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.ServiceDescriptionElement;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;

import java.util.List;

/**
 * A {@link DashManifestParser} that fixes YouTube live DASH manifests to allow
 * starting playback from the newest period instead of the earliest one.
 *
 * <p>Overrides the {@code availabilityStartTime} to 0, forcing ExoPlayer to
 * select the latest period for live streams.</p>
 */
public class YoutubeDashLiveManifestParser extends DashManifestParser {

    private static final long AVAILABILITY_START_TIME_TO_USE = 0;

    @SuppressWarnings("checkstyle:ParameterNumber")
    @NonNull
    @Override
    protected DashManifest buildMediaPresentationDescription(
            final long availabilityStartTime,
            final long durationMs,
            final long minBufferTimeMs,
            final boolean dynamic,
            final long minUpdateTimeMs,
            final long timeShiftBufferDepthMs,
            final long suggestedPresentationDelayMs,
            final long publishTimeMs,
            @Nullable final ProgramInformation programInformation,
            @Nullable final UtcTimingElement utcTiming,
            @Nullable final ServiceDescriptionElement serviceDescription,
            @Nullable final Uri location,
            @NonNull final List<Period> periods) {
        return super.buildMediaPresentationDescription(
                AVAILABILITY_START_TIME_TO_USE,
                durationMs,
                minBufferTimeMs,
                dynamic,
                minUpdateTimeMs,
                timeShiftBufferDepthMs,
                suggestedPresentationDelayMs,
                publishTimeMs,
                programInformation,
                utcTiming,
                serviceDescription,
                location,
                periods);
    }
}
