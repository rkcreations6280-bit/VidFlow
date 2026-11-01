package coreUtils.library.process;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import coreUtils.base.BaseApplication;
import coreUtils.library.networks.NetworkUtils;

/**
 * Utility class for retrieving comprehensive diagnostic and identification information about the Android device.
 *
 * <p>This class gathers various hardware and software metrics, including:
 * <ul>
 *     <li>Unique device identification and application versioning.</li>
 *     <li>Hardware specifications (Model, Manufacturer, Screen Resolution, Density).</li>
 *     <li>System state (Android version, API level, Locale, Language).</li>
 *     <li>Storage capacity and availability.</li>
 *     <li>Network and telephony status (Service provider, Country, SIM operator).</li>
 *     <li>Real-time battery health and status.</li>
 * </ul>
 *
 * <p>The information is primarily intended for logging, crash reporting, or support diagnostics.
 * All collected data is formatted into human-readable strings.
 */
public final class DeviceInfoUtils {

    /**
     * Logger instance for capturing and reporting errors or diagnostic information
     * within the {@link DeviceInfoUtils} class.
     */
    private static final LoggerUtils logger = LoggerUtils.from(DeviceInfoUtils.class);

    private DeviceInfoUtils() {}

    /**
     * Collects and formats detailed information about the device and the current application state.
     * <p>
     * The returned string includes details such as:
     * <ul>
     *     <li>Unique Device Identifier and App Version</li>
     *     <li>Hardware specifications (Model, Manufacturer)</li>
     *     <li>Operating System details (Android Version, API Level)</li>
     *     <li>Display metrics (Resolution, Density)</li>
     *     <li>Storage statistics (Available and Total)</li>
     *     <li>Network information (Operator, Country, SIM details)</li>
     *     <li>Battery status and level</li>
     *     <li>System locale and language settings</li>
     * </ul>
     *
     * @return A formatted, multi-line string containing key-value pairs of device information,
     * or an error message if the information could not be retrieved.
     */
    public static String getDeviceInformation() {
        Context appContext = BaseApplication.getInstance();
        Map<String, Object> data = new LinkedHashMap<>();

        try {
            android.content.pm.PackageManager pm = appContext.getPackageManager();
            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(appContext.getPackageName(), 0);
            data.put("Device Id", DeviceSignature.getInstance(appContext).generate());
            data.put("App Version", VersionInfo.getVersionName(appContext) + " ("
                    + VersionInfo.getVersionCode(appContext) + ")");

            data.put("App Package Name", packageInfo.packageName);
            data.put("Device Model", Build.MODEL);
            data.put("Manufacturer", Build.MANUFACTURER);
            data.put("Android Version", Build.VERSION.RELEASE);
            data.put("API Level", Build.VERSION.SDK_INT);

            DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
            data.put("Resolution", metrics.widthPixels + "x" + metrics.heightPixels);
            data.put("Density", metrics.densityDpi + " dpi");
            data.put("Available Storage", formatSize(getStorage(appContext, true)));

            data.put("Total Storage", formatSize(getStorage(appContext, false)));
            Object systemService = appContext.getSystemService(Context.TELEPHONY_SERVICE);
            TelephonyManager telephony = (TelephonyManager) systemService;

            data.put("Network Operator", NetworkUtils.getServiceProvider());
            String networkCountry = "Unknown";

            if (telephony != null && telephony.getNetworkCountryIso() != null
                    && !telephony.getNetworkCountryIso().isEmpty()) {
                networkCountry = telephony.getNetworkCountryIso()
                        .toUpperCase(Locale.getDefault());
            }

            data.put("Network Country", networkCountry);
            String simOperatorName = Objects.requireNonNull(telephony).getSimOperatorName();
            if (simOperatorName != null) {
                data.put("Sim Operator", simOperatorName.isEmpty()
                        ? "Unknown" : simOperatorName);
            }

            BatteryStatus batteryInfo = getDeviceBatteryStatus(appContext);
            if (batteryInfo != null) {
                data.put("Battery Status", batteryInfo.status);
                data.put("Battery Level", batteryInfo.level + "%");
            }

            Locale locale = Locale.getDefault();
            data.put("Locale", locale.getDisplayName());
            data.put("Language", locale.getLanguage());

        } catch (Exception error) {
            logger.error("Error getting device information:", error);
            return "Error: " + error.getMessage();
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            builder.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }

        return builder.toString().trim();
    }

    /**
     * Calculates the storage space of the device in bytes.
     * <p>
     * This method attempts to retrieve the size of the external files' directory.
     * If unavailable, it falls back to the internal data directory.
     * </p>
     *
     * @param context   The application context used to access file paths.
     * @param available If true, returns the available (free) bytes;
     *                  if false, returns the total capacity in bytes.
     * @return The storage size in bytes.
     */
    private static long getStorage(Context context, boolean available) {
        java.io.File path = context.getExternalFilesDir(null);
        if (path == null) path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getAbsolutePath());

        if (available) {
            return stat.getAvailableBlocksLong()
                    * stat.getBlockSizeLong();
        } else {
            return stat.getBlockCountLong()
                    * stat.getBlockSizeLong();
        }
    }

    /**
     * Formats a size in bytes into a human-readable string representation (e.g., KiB, MiB, GiB).
     *
     * @param bytes The size in bytes to be formatted.
     * @return A formatted string containing the size with its corresponding unit (B, KB, MB, GB, or TB),
     * rounded to two decimal places.
     */
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};

        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, digitGroups);
        return String.format(Locale.getDefault(), "%.2f %s", size, units[digitGroups]);
    }

    /**
     * Retrieves the current battery status and percentage from the device.
     * <p>
     * This method registers a receiver for the {@link Intent#ACTION_BATTERY_CHANGED} sticky broadcast
     * to extract battery information such as charging state and current charge level.
     * </p>
     *
     * @param context The application or activity context used to register the receiver.
     * @return A {@link BatteryStatus} object containing the status string and battery percentage,
     * or {@code null} if the battery intent could not be retrieved.
     */
    public static BatteryStatus getDeviceBatteryStatus(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) return null;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        int batteryPct = scale > 0 ? (level * 100 / scale) : -1;
        String statusString = switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING -> "Charging";
            case BatteryManager.BATTERY_STATUS_FULL -> "Full";
            case BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging";
            default -> "Unknown";
        };

        return new BatteryStatus(statusString, batteryPct);
    }

    /**
     * Callback interface used to handle the results of device information retrieval processes.
     *
     * @see #getDeviceInformation()
     */
    public interface DeviceInfoCallback {
        void onResult(String info);
    }

    /**
     * Represents the current state of the device's battery, including its charging status and charge level.
     *
     * @param status A string description of the battery's current state (e.g., "Charging", "Full", "Discharging").
     * @param level  The current battery charge percentage, typically ranging from 0 to 100.
     */
    public record BatteryStatus(String status, int level) {}
}