package sysModules.sysPlayer.datasource;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;

import java.nio.charset.StandardCharsets;

/**
 * An {@link HlsDataSourceFactory} that allows playback of non-URI HLS playlists.
 * When the manifest type is requested, returns a {@link ByteArrayDataSource}
 * backed by the playlist string bytes. For all other data types, delegates to
 * the configured {@link DataSource.Factory}.
 *
 * <p>This is needed for YouTube OTF (on-the-fly) HLS content where the playlist
 * is provided as a POST response string rather than a URI.</p>
 */
public final class NonUriHlsDataSourceFactory implements HlsDataSourceFactory {

    public static final class Builder {
        private DataSource.Factory dataSourceFactory;
        private String playlistString;

        public void setDataSourceFactory(
                @NonNull final DataSource.Factory dataSourceFactoryForNonManifestContents) {
            this.dataSourceFactory = dataSourceFactoryForNonManifestContents;
        }

        public void setPlaylistString(@NonNull final String hlsPlaylistString) {
            this.playlistString = hlsPlaylistString;
        }

        @NonNull
        public NonUriHlsDataSourceFactory build() {
            if (dataSourceFactory == null) {
                throw new IllegalArgumentException(
                        "No DataSource.Factory valid instance has been specified.");
            }
            if (playlistString == null || playlistString.isEmpty()) {
                throw new IllegalArgumentException("No HLS valid playlist has been specified.");
            }
            return new NonUriHlsDataSourceFactory(dataSourceFactory,
                    playlistString.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final DataSource.Factory dataSourceFactory;
    private final byte[] playlistStringByteArray;

    private NonUriHlsDataSourceFactory(@NonNull final DataSource.Factory dataSourceFactory,
                                       @NonNull final byte[] playlistStringByteArray) {
        this.dataSourceFactory = dataSourceFactory;
        this.playlistStringByteArray = playlistStringByteArray;
    }

    @NonNull
    @Override
    public DataSource createDataSource(final int dataType) {
        if (dataType == C.DATA_TYPE_MANIFEST) {
            return new ByteArrayDataSource(playlistStringByteArray);
        }
        return dataSourceFactory.createDataSource();
    }
}
