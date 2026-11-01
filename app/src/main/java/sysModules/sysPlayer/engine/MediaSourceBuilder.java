package sysModules.sysPlayer.engine;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

public final class MediaSourceBuilder {

    private MediaSourceBuilder() {}

    @NonNull
    public static MediaSource fromUri(@NonNull Uri uri,
                                       @NonNull DataSource.Factory dataSourceFactory) {
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
    }
}
