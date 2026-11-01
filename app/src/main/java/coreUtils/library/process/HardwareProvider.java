package coreUtils.library.process;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.nextgen.R;

import java.util.Locale;
import java.util.TimeZone;

import coreUtils.library.strings.StringHelper;

/**
 * A utility class providing static helper methods to retrieve and process device-specific information.
 * <p>
 * This class includes functionality for:
 * <ul>
 *     <li>Accessing display metrics and screen density formats.</li>
 *     <li>Retrieving hardware manufacturer and model information.</li>
 *     <li>Checking network connectivity status.</li>
 *     <li>Determining user location and country codes based on locale, telephony, and time zone.</li>
 *     <li>Normalizing region-specific data such as Indian phone numbers.</li>
 * </ul>
 *
 * This class is final and cannot be instantiated.
 */
public final class HardwareProvider {

    private HardwareProvider() {}

    /**
     * Returns the logical density of the display. This is a scaling factor for the
     * Density Independent Pixel unit, where one DIP is one pixel on an approx. 160 dpi
     * screen (e.g. mdpi, 1.0 scaling factor).
     *
     * @param context The context used to retrieve the display metrics.
     * @return The logical density of the display.
     */
    public static float getDeviceScreenDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    /**
     * Returns the screen density bucket of the device as a string identifier.
     *
     * @param context The application or activity context used to access display metrics.
     * @return A string representing the density bucket (e.g., "xxxhdpi", "xxhdpi", "xhdpi",
     * "hdpi", "mdpi", or "ldpi").
     */
    public static String getDeviceScreenDensityInFormat(Context context) {
        float density = context.getResources().getDisplayMetrics().density;

        if (density >= 4.0f) {
            return "xxxhdpi";
        } else if (density >= 3.0f) {
            return "xxhdpi";
        } else if (density >= 2.0f) {
            return "xhdpi";
        } else if (density >= 1.5f) {
            return "hdpi";
        } else if (density >= 1.0f) {
            return "mdpi";
        } else {
            return "ldpi";
        }
    }

    /**
     * Retrieves the combined manufacturer and model name of the device.
     * <p>
     * If the model name already starts with the manufacturer name, it returns the model name.
     * Otherwise, it concatenates the manufacturer and model. The resulting string is capitalized.
     * </p>
     *
     * @return A capitalized string representing the device's manufacturer and model,
     *         or an empty string if the name cannot be determined.
     */
    public static String getDeviceManufactureModelName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        String name;
        String manufacturerName = manufacturer.toLowerCase(Locale.ROOT);
        if (model.toLowerCase(Locale.ROOT).startsWith(manufacturerName)) {
            name = model;
        } else {
            name = manufacturer + " " + model;
        }

        String capitalized = StringHelper.capitalizeFirstLetter(name);
        return capitalized != null ? capitalized : "";
    }

    /**
     * Checks whether the device is currently connected to the internet via Wi-Fi,
     * Cellular, Ethernet, or Bluetooth.
     *
     * @param context The context used to retrieve the connectivity manager system service.
     * @return {@code true} if the device has an active network connection with supported transport
     * capabilities, {@code false} otherwise.
     */
    public static boolean isDeviceConnectedToInternet(Context context) {
        Context applicationContext = context.getApplicationContext();
        Object systemService = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) systemService;
        if (connectivityManager == null) return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH);
    }

    /**
     * Retrieves the country code associated with the current device user,
     * typically derived from the system's default locale or region settings.
     *
     * @return A string representing the user's country code (e.g., ISO 3166-1 alpha-2).
     */
    public static String getDeviceUserCountry(Context context) {
        String localeCountry = Locale.getDefault().getCountry();
        if (!localeCountry.isEmpty()) return localeCountry;
        Context applicationContext = context.getApplicationContext();

        TelephonyManager tm = (TelephonyManager)
                applicationContext.getSystemService(Context.TELEPHONY_SERVICE);

        if (tm != null) {
            String simCountry = tm.getSimCountryIso();
            if (simCountry != null && !simCountry.isEmpty()) {
                return simCountry.toUpperCase(Locale.getDefault());
            }
        }

        return StringHelper.getText(R.string.label_unknown);
    }

    /**
     * Checks whether the current user is located in India based on the device's
     * system locale, network country ISO, or SIM provider settings.
     *
     * @return true if the user is identified as being from India, false otherwise.
     */
    public static boolean isUserFromIndia(Context context) {
        final String indiaCode = "in";
        Context applicationContext = context.getApplicationContext();
        String telephonyService = Context.TELEPHONY_SERVICE;
        Object systemService = applicationContext.getSystemService(telephonyService);
        TelephonyManager tm = (TelephonyManager) systemService;

        if (tm != null) {
            String networkCountry = tm.getNetworkCountryIso();
            String simCountry = tm.getSimCountryIso();

            if (networkCountry != null) {
                if (networkCountry.equalsIgnoreCase(indiaCode)) return true;
            }

            if (simCountry != null) {
                if (simCountry.equalsIgnoreCase(indiaCode)) return true;
            }
        }

        String country = Locale.getDefault().getCountry();
        if (country.equalsIgnoreCase(indiaCode)) return true;

        return TimeZone.getDefault().getID()
                .equalsIgnoreCase("Asia/Kolkata");
    }

    /**
     * Normalizes an Indian phone number by removing common prefixes such as "+91", "91",
     * or leading zeros to return a consistent 10-digit format.
     *
     * @param phoneNumber The raw phone number string to be normalized.
     * @return A normalized 10-digit phone number string.
     */
    public static String normalizeIndianNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        StringBuilder digitsBuilder = new StringBuilder();

        for (char c : phoneNumber.toCharArray()) {
            if (Character.isDigit(c)) digitsBuilder.append(c);
        }

        String digits = digitsBuilder.toString();
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            return digits.substring(1);
        }

        return digits;
    }
}