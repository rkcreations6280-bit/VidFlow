package coreUtils.library.process;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

/**
 * Utility class for retrieving and caching application version information and device SDK details.
 * <p>
 * This class provides thread-safe access to the application's version name and version code,
 * utilizing a double-checked locking pattern to ensure the {@link PackageInfo}
 * is only queried once. It handles compatibility differences between various Android API levels,
 * specifically for package information retrieval on API 33 and above.
 */
public final class VersionInfo {

    /**
     * Logger instance used for logging errors and diagnostic information within this class.
     */
    private static final LoggerUtils logger = LoggerUtils.from(VersionInfo.class);

    /**
     * The cached application version name (e.g., "1.0.0").
     * This field is volatile to ensure thread-safe visibility when using the double-checked locking pattern.
     */
    private static volatile String cachedVersionName;

    /**
     * Cached version code of the application to avoid repeated calls to {@link PackageManager}.
     * This value is lazily initialized and thread-safe.
     */
    private static volatile Long cachedVersionCode;

    /**
     * Lock object used for synchronization to ensure thread-safe initialization of
     * the cached version name and version code.
     */
    private static final Object LOCK = new Object();

    private VersionInfo() {}

    /**
     * Ensures that the application version name and version code are loaded into the cache.
     * <p>
     * This method uses a thread-safe double-checked locking pattern to initialize the
     * cached values if they haven't been set yet. It retrieves package information
     * using the provided {@link Context}, handling API-specific differences for
     * Android Tiramisu (API 33) and above.
     *
     * @param context The context used to access the {@link PackageManager}.
     */
    private static void ensureCacheLoaded(@NonNull Context context) {
        if (cachedVersionName != null && cachedVersionCode != null) return;
        synchronized (LOCK) {
            if (cachedVersionName != null && cachedVersionCode != null) return;
            try {
                String packageName = context.getPackageName();
                PackageManager packageManager = context.getPackageManager();
                PackageInfo info;

                if (Build.VERSION.SDK_INT >= 33) {
                    PackageManager.PackageInfoFlags flags =
                            PackageManager.PackageInfoFlags.of(0);
                    info = packageManager.getPackageInfo(packageName, flags);
                } else {
                    info = packageManager.getPackageInfo(packageName, 0);
                }

                cachedVersionName = info.versionName;
                cachedVersionCode = PackageInfoCompat.getLongVersionCode(info);
            } catch (Exception error) {
                logger.error("Failed to get app version info", error);
            }
        }
    }

    /**
     * Retrieves the version name of the application.
     *
     * @param context The context used to access the package manager and retrieve information.
     * @return The version name string (e.g., "1.0.0"), or {@code null} if the information could not be retrieved.
     */
    @Nullable
    public static String getVersionName(@NonNull Context context) {
        ensureCacheLoaded(context);
        return cachedVersionName;
    }

    /**
     * Retrieves the version code of the application.
     *
     * <p>This method uses {@link PackageInfoCompat#getLongVersionCode} to ensure compatibility
     * across different Android API levels. The value is cached after the first successful retrieval.</p>
     *
     * @param context The context used to access the {@link PackageManager}.
     * @return The version code as a {@code long}, or {@code 0L} if the version info could not be retrieved.
     */
    public static long getVersionCode(@NonNull Context context) {
        ensureCacheLoaded(context);
        return cachedVersionCode != null ? cachedVersionCode : 0L;
    }

    /**
     * Retrieves the SDK version (API level) of the Android operating system running on the device.
     *
     * @return the SDK API level as an integer.
     */
    public static int getDeviceSDKVersion() {
        return Build.VERSION.SDK_INT;
    }
}