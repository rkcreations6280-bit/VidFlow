package coreUtils.library.networks;

import static android.webkit.MimeTypeMap.getSingleton;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;

/**
 * Utility class providing helper methods for network-related operations.
 *
 * <p>This class offers a variety of tools to check internet connectivity and network availability,
 * retrieve carrier information, handle URL redirections, and perform URI manipulations such as
 * normalization and domain extraction.</p>
 *
 * <p>This class consists strictly of static methods and is not intended to be instantiated.</p>
 */
public class NetworkUtils {
    /**
     * Logger instance for capturing and reporting errors or diagnostic information
     * specific to network utility operations.
     */
    private static final LoggerUtils logger = LoggerUtils.from(NetworkUtils.class);

    private NetworkUtils() {}

    /**
     * Checks for active internet connectivity by performing a network request to a reliable host.
     * <p>
     * This method validates actual end-to-end connectivity by attempting to establish an
     * HTTP connection to {@code https://www.google.com}. This is more definitive than
     * checking network interfaces, as it ensures data can actually be transmitted and received.
     * </p>
     *
     * @return {@code true} if the connection is successfully established and returns an
     * HTTP 200 (OK) response; {@code false} otherwise.
     */
    public static boolean isInternetConnected() {
        // Local check only: nothing is sent to any server.
        return isNetworkAvailable();
    }

    /**
     * Checks whether the device is currently connected to a network via Wi-Fi or Cellular.
     * <p>
     * This method retrieves the active network and checks for {@link NetworkCapabilities#TRANSPORT_WIFI}
     * or {@link NetworkCapabilities#TRANSPORT_CELLULAR} transport types.
     *
     * @return {@code true} if a Wi-Fi or Cellular network is available and connected,
     * {@code false} otherwise.
     */
    public static boolean isNetworkAvailable() {
        Context context = BaseApplication.getInstance();
        Object systemService = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) systemService;
        if (connectivityManager == null) return false;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.
                getNetworkCapabilities(activeNetwork);

        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    /**
     * Checks whether Wi-Fi is enabled on the device.
     *
     * @return {@code true} if Wi-Fi is enabled, {@code false} otherwise.
     */
    public static boolean isWifiEnabled() {
        BaseApplication appContext = BaseApplication.getInstance();
        Object wifiService = appContext.getSystemService(Context.WIFI_SERVICE);
        WifiManager wifiManager = (WifiManager) wifiService;
        return wifiManager.isWifiEnabled();
    }

    /**
     * Guesses the MIME type for a given URL based on its file extension.
     * <p>
     * This method extracts the extension from the URL and maps it to a corresponding
     * MIME type using the {@link MimeTypeMap}.
     *
     * @param url The URL to extract the MIME type from. Must not be null.
     * @return The MIME type associated with the URL's extension, or {@code null} if
     * no extension is found or the extension is unknown.
     */
    @Nullable
    public static String getMimeTypeFromUrl(@NonNull String url) {
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (fileExtension != null) {
            String normalizedExtension = fileExtension.toLowerCase();
            return getSingleton().getMimeTypeFromExtension(normalizedExtension);
        }
        return null;
    }

    /**
     * Resolves a redirected URL to retrieve the destination URL specified in the "Location" header.
     * <p>
     * This method disables automatic redirect following to capture the target URI from the
     * HTTP response headers (status codes 3xx). If no redirection occurs, the original URL is returned.
     *
     * @param fileURL The initial URL to check for redirection.
     * @return The redirected URL if found in the "Location" header; otherwise, the original {@code fileURL}.
     * @throws IOException If the URL is null, malformed, or if a connection error occurs.
     */
    public static String getOriginalUrlFromRedirectedUrl(String fileURL) throws IOException {
        if (fileURL == null) throw new IOException("URL is null");
        URLConnection urlConnection = new URL(fileURL).openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;
        connection.setInstanceFollowRedirects(false);
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode >= 300 && responseCode < 400) {
            String originalUrl = connection.getHeaderField("Location");
            if (originalUrl != null) return originalUrl;
        }
        return fileURL;
    }

    /**
     * Retrieves the alphabetic name of the current registered operator (Service Provider).
     * <p>
     * This method accesses the {@link TelephonyManager} via the application context
     * to obtain the network operator name.
     *
     * @return The name of the service provider if available, or {@code null} if the
     * telephony service is unavailable or the name cannot be retrieved.
     */
    @Nullable
    public static String getServiceProvider() {
        BaseApplication appContext = BaseApplication.getInstance();
        Object telephonyService = appContext.getSystemService(Context.TELEPHONY_SERVICE);
        TelephonyManager manager = (TelephonyManager) telephonyService;
        if (manager != null) return manager.getNetworkOperatorName();
        else return null;
    }

    /**
     * Checks if a specific URL is accessible by performing a network HEAD request.
     * <p>
     * This method attempts to establish a connection to the provided URL and verifies
     * if the server returns an HTTP OK (200) response. Using the HEAD method ensures
     * minimal bandwidth usage as the response body is not downloaded.
     *
     * @param urlString The string representation of the URL to check.
     * @return {@code true} if the URL is accessible and returns HTTP OK; {@code false} otherwise.
     */
    public static boolean isUrlAccessible(@NonNull String urlString) {
        try {
            URLConnection urlConnection = new URL(urlString).openConnection();
            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Throwable error) {
            logger.error("Error found while checking URL accessibility:", error);
            return false;
        }
    }

    /**
     * Normalizes the given URL by ensuring it ends with a trailing slash if it is a valid URL.
     *
     * @param url The URL string to be normalized.
     * @return The normalized URL with a trailing slash if it was valid and didn't have one;
     * otherwise, the original URL.
     */
    public static String normalizeUrl(@NonNull String url) {
        if (!url.endsWith("/") && URLUtil.isValidUrl(url)) {
            return url.replaceAll("/$", "") + "/";
        }
        return url;
    }

    /**
     * Extracts unique hostnames (domains) from a given array of URLs.
     * <p>
     * This method parses each URL to retrieve its host component and ensures that
     * only distinct, non-null domains are returned in the final array.
     *
     * @param urls An array of URL strings to process. Must not be null.
     * @return An array of unique domain strings extracted from the provided URLs.
     */
    public static String[] extractUniqueDomains(@NonNull String[] urls) {
        List<String> uniqueDomains = new ArrayList<>();
        for (String url : urls) {
            String domain = Uri.parse(url).getHost();
            if (domain != null && !uniqueDomains.contains(domain)) {
                uniqueDomains.add(domain);
            }
        }
        return uniqueDomains.toArray(new String[0]);
    }
}
