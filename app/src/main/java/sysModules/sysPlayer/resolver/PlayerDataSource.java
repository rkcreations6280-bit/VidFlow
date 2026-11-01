package sysModules.sysPlayer.resolver;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;

import sysModules.sysPlayer.datasource.YoutubeHttpDataSource;

/**
 * Provides {@link DataSource.Factory} instances for all content types, with
 * separate factory variants for YouTube streams requiring different URL
 * parameter configurations ({@code range}, {@code rn}).
 *
 * <p>YouTube streams require specific HTTP data source configurations:
 * <ul>
 *   <li>HLS: no range, no rn</li>
 *   <li>DASH (OTF, progressive-to-DASH, post-live DVR): range=true, rn=true</li>
 *   <li>Legacy progressive: range=false, rn=true</li>
 * </ul>
 *
 * <p>For non-YouTube services, a standard {@link DefaultHttpDataSource.Factory}
 * is used with disk caching.</p>
 */
public final class PlayerDataSource {
    private static final long MAX_CACHE_BYTES = 200L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private final DefaultHttpDataSource.Factory defaultHttpFactory;
    private final CacheDataSource.Factory cacheFactory;
    private final SimpleCache cache;

    // YouTube-specific cached factories (with different range/rn parameters)
    private final CacheDataSource.Factory ytHlsCacheFactory;
    private final CacheDataSource.Factory ytDashCacheFactory;
    private final CacheDataSource.Factory ytProgressiveDashCacheFactory;

    // Cacheless factory for live streams
    private final DefaultHttpDataSource.Factory cachelessFactory;

    public PlayerDataSource(@NonNull Context context) {
        defaultHttpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(getUserAgent(context))
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);

        File cacheDir = new File(context.getCacheDir(), "exo_player_cache");
        cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES));

        // Generic cached factory (for non-YouTube sources)
        cacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(defaultHttpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        // YouTube HLS: range=false, rn=false
        ytHlsCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new YoutubeHttpDataSource.Factory()
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        .setReadTimeoutMs(READ_TIMEOUT_MS)
                        .setAllowCrossProtocolRedirects(true))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        // YouTube DASH (OTF, progressive-to-DASH, post-live DVR): range=true, rn=true
        ytDashCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new YoutubeHttpDataSource.Factory()
                        .setRangeParameterEnabled(true)
                        .setRnParameterEnabled(true)
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        .setReadTimeoutMs(READ_TIMEOUT_MS)
                        .setAllowCrossProtocolRedirects(true))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        // YouTube legacy progressive: range=false, rn=true
        ytProgressiveDashCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new YoutubeHttpDataSource.Factory()
                        .setRnParameterEnabled(true)
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        .setReadTimeoutMs(READ_TIMEOUT_MS)
                        .setAllowCrossProtocolRedirects(true))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        // Cacheless factory for live streams (no caching)
        cachelessFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
    }

    // ─── Generic factories ───────────────────────────────────────────────

    /** Cached factory for non-YouTube progressive/DASH/HLS/SS sources. */
    @NonNull
    public DataSource.Factory getCacheDataSourceFactory() {
        return cacheFactory;
    }

    /** Raw HTTP factory without caching. Used for manifests and non-URI content. */
    @NonNull
    public DataSource.Factory getHttpDataSourceFactory() {
        return defaultHttpFactory;
    }

    /** Cacheless HTTP factory. Used for live streams. */
    @NonNull
    public DataSource.Factory getCachelessDataSourceFactory() {
        return cachelessFactory;
    }

    // ─── YouTube-specific factories ──────────────────────────────────────

    /** YouTube HLS: range=false, rn=false. Cached. */
    @NonNull
    public DataSource.Factory getYouTubeHlsDataSourceFactory() {
        return ytHlsCacheFactory;
    }

    /** YouTube DASH (OTF, progressive-to-DASH, post-live DVR): range=true, rn=true. Cached. */
    @NonNull
    public DataSource.Factory getYouTubeDashDataSourceFactory() {
        return ytDashCacheFactory;
    }

    /** YouTube legacy progressive: range=false, rn=true. Cached. */
    @NonNull
    public DataSource.Factory getYouTubeProgressiveDataSourceFactory() {
        return ytProgressiveDashCacheFactory;
    }

    // ─── MediaSource builders ────────────────────────────────────────────

    @NonNull
    public MediaSource buildProgressiveSource(@NonNull final android.net.Uri uri,
                                               @Nullable final String cacheKey,
                                               @Nullable final Object tag) {
        final com.google.android.exoplayer2.MediaItem.Builder itemBuilder =
                new com.google.android.exoplayer2.MediaItem.Builder().setUri(uri);
        if (cacheKey != null) itemBuilder.setCustomCacheKey(cacheKey);
        if (tag != null) itemBuilder.setTag(tag);
        return new ProgressiveMediaSource.Factory(cacheFactory)
                .createMediaSource(itemBuilder.build());
    }

    @NonNull
    public MediaSource buildDashSource(@NonNull final android.net.Uri uri,
                                        @Nullable final String cacheKey,
                                        @Nullable final Object tag) {
        final com.google.android.exoplayer2.MediaItem.Builder itemBuilder =
                new com.google.android.exoplayer2.MediaItem.Builder().setUri(uri);
        if (cacheKey != null) itemBuilder.setCustomCacheKey(cacheKey);
        if (tag != null) itemBuilder.setTag(tag);
        return new DashMediaSource.Factory(cacheFactory)
                .createMediaSource(itemBuilder.build());
    }

    @NonNull
    public MediaSource buildHlsSource(@NonNull final android.net.Uri uri,
                                       @Nullable final String cacheKey,
                                       @Nullable final Object tag) {
        final com.google.android.exoplayer2.MediaItem.Builder itemBuilder =
                new com.google.android.exoplayer2.MediaItem.Builder().setUri(uri);
        if (cacheKey != null) itemBuilder.setCustomCacheKey(cacheKey);
        if (tag != null) itemBuilder.setTag(tag);
        return new HlsMediaSource.Factory(cacheFactory)
                .createMediaSource(itemBuilder.build());
    }

    @NonNull
    public MediaSource buildSsSource(@NonNull final android.net.Uri uri,
                                      @Nullable final String cacheKey,
                                      @Nullable final Object tag) {
        final com.google.android.exoplayer2.MediaItem.Builder itemBuilder =
                new com.google.android.exoplayer2.MediaItem.Builder().setUri(uri);
        if (cacheKey != null) itemBuilder.setCustomCacheKey(cacheKey);
        if (tag != null) itemBuilder.setTag(tag);
        return new SsMediaSource.Factory(cacheFactory)
                .createMediaSource(itemBuilder.build());
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    public void release() {
        try {
            cache.release();
        } catch (Exception ignored) {}
    }

    @NonNull
    public SimpleCache getCache() {
        return cache;
    }

    private static String getUserAgent(@NonNull final Context context) {
        try {
            String pkg = context.getPackageName();
            String version = context.getPackageManager()
                    .getPackageInfo(pkg, 0).versionName;
            return pkg + "/" + (version != null ? version : "1.0");
        } catch (Exception e) {
            return "VidFlow-Player/1.0";
        }
    }
}
