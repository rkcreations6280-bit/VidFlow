package coreUtils.library.networks;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreUtils.library.process.LoggerUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sysModules.newPipeLib.parsers.YtSteamExtractor;

/**
 * A utility class providing static methods for URL manipulation, validation,
 * and network-related metadata extraction.
 *
 * <p>This class includes functionality for:
 * <ul>
 *     <li>URL and domain validation using regex and native parsing.</li>
 *     <li>Path and query parameter manipulation (appending, removing, normalizing).</li>
 *     <li>Extracting metadata such as file names, domain names, and content disposition headers.</li>
 *     <li>Network accessibility checks and file size retrieval using {@link HttpURLConnection}
 *         or {@link OkHttpClient}.</li>
 *     <li>Analyzing server support for multipart or resumable downloads via HTTP headers.</li>
 *     <li>URL encoding and decoding with UTF-8 support.</li>
 * </ul>
 */
public class URLUtility {

    private static final LoggerUtils logger = LoggerUtils.from(URLUtility.class);

    /**
     * The standard HTTP header field name for Content-Disposition.
     * This header is typically used to provide information on how to process the response
     * content (e.g., as an attachment) and often contains the suggested filename.
     */
    public static final String CONTENT_DISPOSITION = "Content-Disposition";

    /**
     * The HTTP "Accept-Ranges" response header field name, used by servers to
     * advertise their support for partial requests (range requests).
     */
    public static final String ACCEPT_RANGES = "Accept-Ranges";

    /**
     * The value used in the HTTP 'Accept-Ranges' header to indicate that the
     * server supports partial content requests measured in bytes.
     */
    public static final String BYTES = "bytes";

    /**
     * Standard HTTP header field indicating the date and time at which the origin server
     * believes the resource was last modified.
     */
    public static final String LAST_MODIFIED = "Last-Modified";

    /**
     * Standard HTTP response header field used for entity tags.
     * <p>
     * The ETag (entity tag) provides a unique identifier for a specific version of a resource,
     * which helps in cache validation and supporting resumable downloads by verifying if
     * the content has changed.
     */
    public static final String E_TAG = "ETag";

    /**
     * Represents the HTTP HEAD request method, used to retrieve metadata and headers
     * from a server without downloading the actual content body.
     */
    public static final String HEAD = "HEAD";

    /**
     * The standard HTTP header field "Content-Length" indicating the size of the
     * message body, in bytes, sent to the recipient.
     */
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final List<String> APP_DEFAULT_MOBILE_AGENTS =
        List.of("Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML, " +
                "like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
            "Mozilla/5.0 (Linux; Android 4.4.2; Nexus 5 Build/KOT49H) AppleWebKit/537.36 (KHTML, " +
                "like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; U; Android 2.3.6; en-us; GT-I9000 Build/GINGERBREAD) AppleWebKit/533.1 " +
                "(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
    
    public static final String APP_ALL_MEDIA_TYPES = "text/html,application/xhtml+xml,application/xml;q=0.9," +
        "image/webp,image/apng,image/avif,image/jpeg,image/png,image/gif,image/svg+xml,image/*," +
        "*/*;q=0.8";
    
    
    /**
     * Validates whether a given string is a syntactically valid URL.
     * <p>
     * This method attempts to parse the string using the {@link URL} constructor.
     * It returns {@code false} if the string is null, empty, or fails to parse
     * (e.g., missing a valid protocol like http/https).
     *
     * @param url The string to be validated.
     * @return {@code true} if the string is a valid URL, {@code false} otherwise.
     */
    public static boolean isValidURL(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            new URL(url);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    /**
     * Extracts the file name from a given URL string.
     * <p>
     * This method parses the URL to retrieve the path component and returns the
     * substring following the last forward slash. If no slash is found in the path,
     * the full path is returned.
     * </p>
     *
     * @param urlString The full URL string from which to extract the file name.
     * @return The extracted file name, or {@code null} if the URL is malformed or an error occurs.
     */
    @Nullable
    public static String getFileNameFromURL(@NonNull String urlString) {
        try {
            URL url = new URL(urlString);
            String filePath = url.getPath();
            int lastSlashIndex = filePath.lastIndexOf('/');
            if (lastSlashIndex == -1) return filePath;
            else return filePath.substring(lastSlashIndex + 1);
        } catch (Exception error) {
            logger.error("Error found while getting file name from url:", error);
            return null;
        }
    }

    /**
     * Validates whether a given string follows a basic domain name format.
     * <p>
     * The validation checks for alphanumeric characters, hyphens, and dots,
     * ensuring the string ends with a top-level domain (TLD) of 2 to 6 characters.
     * </p>
     *
     * @param domain The domain string to validate.
     * @return {@code true} if the domain matches the pattern; {@code false} otherwise.
     */
    public static boolean isValidDomain(String domain) {
        String domainRegex = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$";
        return domain.matches(domainRegex);
    }

    /**
     * Ensures that the provided URL uses the HTTPS protocol and strips common prefixes.
     * <p>
     * This method validates the input as a domain. If valid, it removes existing
     * "http://", "https://", and "www." prefixes to create a "naked" domain,
     * then prepends "https://" to the result.
     *
     * @param url The URL or domain string to process. Must not be null.
     * @return The formatted HTTPS URL string, or {@code null} if the input is not a valid domain.
     */
    @Nullable
    public static String ensureHttps(@NonNull String url) {
        if (!isValidDomain(url)) return null;
        String nakedDomain = url.replaceFirst("^(https?://)?(www\\.)?", "");
        if (!nakedDomain.startsWith("https://")) {
            nakedDomain = "https://" + nakedDomain;
        }
        return nakedDomain;
    }

    /**
     * Checks if a URL is accessible by sending an HTTP HEAD request.
     * <p>
     * This method attempts to establish a connection to the provided URL string and
     * verifies if the server returns an HTTP 200 OK response. It uses the HEAD
     * method to minimize bandwidth usage.
     *
     * @param urlString The string representation of the URL to check.
     * @return {@code true} if the URL returns an HTTP OK response; {@code false}
     * otherwise or if an error occurs during the connection attempt.
     */
    public static boolean isUrlAccessible(@NonNull String urlString) {
        try {
            URLConnection urlConnection = new URL(urlString).openConnection();
            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestMethod(HEAD);
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Throwable error) {
            logger.error("Error checking url accessibility:", error);
            return false;
        }
    }

    /**
     * Extracts all web URLs from the provided text string using Android's {@link Patterns#WEB_URL}.
     *
     * @param text The input text to search for links. Must not be null.
     * @return An array of strings containing the URLs found in the text. Returns an empty array
     * if no matches are found.
     */
    @NonNull
    public static String[] extractLinks(@NonNull String text) {
        List<String> links = new ArrayList<>();
        Pattern pattern = Patterns.WEB_URL;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            links.add(url);
        }
        return links.toArray(new String[0]);
    }

    /**
     * Retrieves the file size from the specified URL using a HEAD request.
     * <p>
     * This method attempts to open a connection to the provided URL and fetch the
     * {@code Content-Length} header without downloading the actual file content.
     *
     * @param url The {@link URL} to check the file size for. Must not be null.
     * @return The size of the file in bytes, or -1 if the size could not be determined
     * or an error occurred.
     */
    public static long getFileSizeFromUrl(@NonNull URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HEAD);
            connection.connect();
            return connection.getContentLength();
        } catch (IOException error) {
            logger.error("Error found while getting file size form url:", error);
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Retrieves the file size of a remote resource using the OkHttp library.
     * <p>
     * This method performs an HTTP HEAD request to fetch the {@code Content-Length} header
     * without downloading the actual file body. It is configured to follow both HTTP
     * and SSL redirects.
     * </p>
     *
     * @param url The {@link URL} of the remote file.
     * @return The size of the file in bytes if successful; otherwise, returns {@code -1}
     * if the header is missing, the response is unsuccessful, or an exception occurs.
     */
    public static long getFileSizeFromURL_OkHttp(@NonNull URL url) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .followRedirects(true).followSslRedirects(true).build();

            Request request = new Request.Builder().url(url).head().build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String contentLength = response.header(CONTENT_LENGTH);
                    if (contentLength != null) return Long.parseLong(contentLength);
                    else throw new IOException("Content-Length header is missing");
                } else {
                    throw new IOException("Failed to fetch file size: " +
                            response.message());
                }
            }
        } catch (Exception error) {
            logger.error("Error getting file name from url:", error);
            return -1;
        }
    }

    /**
     * Checks if the server supporting the provided file URL allows multipart (range-based)
     * downloads. This is determined by checking the "Accept-Ranges" header in the HTTP response.
     *
     * @param fileUrl The URL of the file to check.
     * @return {@code true} if the server supports multipart downloads (Accept-Ranges: bytes),
     * {@code false} otherwise.
     * @throws IOException If an I/O error occurs while opening the connection or connecting
     *                     to the server.
     */
    public static boolean supportsMultipartDownload(
            @NonNull String fileUrl) throws IOException {
        URLConnection urlConnection = new URL(fileUrl).openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;
        connection.setRequestMethod(HEAD);
        connection.connect();

        boolean supportsMultipart = false;
        String acceptRanges = connection.getHeaderField(ACCEPT_RANGES);
        if (acceptRanges != null && acceptRanges.equals(BYTES)) {
            supportsMultipart = true;
        }

        connection.disconnect();
        return supportsMultipart;
    }

    /**
     * Checks if the server supports resumable downloads for the given file URL.
     * <p>
     * This method sends a {@code HEAD} request and examines the response headers.
     * A download is considered resumable if the server provides an {@code Accept-Ranges: bytes}
     * header, an {@code ETag}, or a {@code Last-Modified} timestamp, which are necessary
     * for validating partial content requests.
     *
     * @param fileUrl The direct URL of the file to check.
     * @return {@code true} if the server likely supports range requests or provides
     * validation headers for resuming; {@code false} otherwise.
     * @throws IOException If an I/O error occurs while opening the connection.
     */
    public static boolean supportsResumableDownload(
            @NonNull String fileUrl) throws IOException {
        URLConnection urlConnection = new URL(fileUrl).openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;
        connection.setRequestMethod(HEAD);
        connection.connect();

        boolean supportsResume = false;
        String acceptRanges = connection.getHeaderField(ACCEPT_RANGES);
        String eTag = connection.getHeaderField(E_TAG);
        String lastModified = connection.getHeaderField(LAST_MODIFIED);
        if ((acceptRanges != null && acceptRanges.equals(BYTES)) ||
                eTag != null || lastModified != null) {
            supportsResume = true;
        }

        connection.disconnect();
        return supportsResume;
    }

    /**
     * Normalizes a URL by ensuring it ends with a trailing slash if it contains a
     * domain/file extension (indicated by a dot) and does not already have one.
     *
     * @param url The URL string to be normalized.
     * @return The normalized URL string with a trailing slash appended if necessary.
     */
    @NonNull
    public static String normalizeUrl(@NonNull String url) {
        if (!url.endsWith("/") && url.contains("."))
            return url.replaceAll("/$", "") + "/";
        return url;
    }

    /**
     * Extracts the host name (domain) from a given URL string.
     *
     * @param url The full URL string to parse.
     * @return The host/domain name of the URL (e.g., "example.com"),
     * or an empty string if the URL is malformed or invalid.
     */
    @NonNull
    public static String extractDomainName(@NonNull String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost();
        } catch (Throwable error) {
            logger.error("Error found while extracting domain name:", error);
            return "";
        }
    }

    /**
     * Concatenates a base URL and a path, ensuring a single forward slash exists between them.
     * <p>
     * If the base URL does not end with a slash and the path does not start with one,
     * a slash is automatically inserted.
     *
     * @param baseUrl The base URL to which the path will be appended.
     * @param path    The path segment to append to the base URL.
     * @return The combined URL string.
     */
    @NonNull
    public static String appendPath(@NonNull String baseUrl,
                                    @NonNull String path) {
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) baseUrl += "/";
        return baseUrl + path;
    }

    /**
     * Removes the query string and any fragments from the provided URL, returning only
     * the protocol, host, and path.
     *
     * @param url The full URL string from which to remove query parameters.
     * @return The base URL containing only protocol, host, and path.
     * Returns an empty string if the URL is malformed or an error occurs.
     */
    @NonNull
    public static String removeQueryParams(@NonNull String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" +
                    parsedUrl.getHost() + parsedUrl.getPath();
        } catch (Throwable error) {
            logger.error("Error found while removing query name from url:", error);
            return "";
        }
    }

    /**
     * Appends a query parameter to the given URL.
     * <p>
     * This method automatically determines whether to use '?' or '&' as a separator
     * based on the presence of existing query parameters in the URL.
     * </p>
     *
     * @param url    The base URL to which the parameter will be added. Must not be null.
     * @param param  The name of the query parameter. Must not be null.
     * @param value  The value of the query parameter. Must not be null.
     * @param encode Whether the value should be URL-encoded using UTF-8.
     * @return The updated URL string with the appended parameter, or the original URL if an error occurs.
     */
    @NonNull
    public static String addQueryParam(@NonNull String url, @NonNull String param,
                                       @NonNull String value, boolean encode) {
        try {
            URL baseUrl = new URL(url);
            StringBuilder newUrl = new StringBuilder(baseUrl.toString());

            if (baseUrl.getQuery() == null) newUrl.append('?');
            else newUrl.append('&');

            newUrl.append(param);
            newUrl.append('=');

            if (encode) {
                newUrl.append(encode(value, UTF_8));
            } else newUrl.append(value);
            return newUrl.toString();
        } catch (Throwable error) {
            logger.error("Error found while adding query parameters to url:", error);
            return url;
        }
    }

    /**
     * Generates a list of potential URLs by appending various top-level domains (TLDs)
     * from {@link URLDomains#TOP_LEVEL_DOMAINS} to the provided base URL string.
     *
     * @param baseUrl The prefix or domain name to which the TLDs will be appended.
     * @return A {@link List} of strings representing the generated candidate URLs.
     */
    @NonNull
    public static List<String> generatePossibleURLs(@NonNull String baseUrl) {
        List<String> possibleURLs = new ArrayList<>();
        for (String domainEnd : URLDomains.TOP_LEVEL_DOMAINS) {
            possibleURLs.add(baseUrl + domainEnd);
        }
        return possibleURLs;
    }

    /**
     * Resolves the target destination of a redirecting URL.
     * <p>
     * This method connects to the provided URL with redirect-following disabled to
     * identify if the server is issuing a move command (HTTP 301, 302, 303, or 201).
     * If a redirect is detected, it returns the value of the "Location" header.
     * </p>
     *
     * @param fileURL The URL to check for redirects.
     * @return The redirected URL found in the "Location" header, or {@code null}
     * if no redirect occurred or an error was encountered.
     */
    @Nullable
    public static String getOriginalURL(@NonNull String fileURL) {
        try {
            URLConnection urlConnection = new URL(fileURL).openConnection();
            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setInstanceFollowRedirects(false);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == HttpURLConnection.HTTP_CREATED) {
                return connection.getHeaderField("Location");
            }
            return null;
        } catch (Exception error) {
            logger.error("Error found while getting original url:", error);
            return null;
        }
    }

    /**
     * Fetches the "Content-Disposition" header from the specified URL by performing a GET request.
     * This header is typically used to determine the filename suggested by the server for a download.
     *
     * @param url The target URL to fetch the header from.
     * @return The value of the "Content-Disposition" header if the request is successful (HTTP 200)
     * and the header is present; otherwise, returns {@code null}.
     */
    @Nullable
    public static String fetchContentDispositionHeader(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String contentDisposition =
                        connection.getHeaderField(CONTENT_DISPOSITION);
                if (contentDisposition != null) return contentDisposition;
            }
        } catch (IOException error) {
            logger.error("Error fetching content disposition header:", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Encodes a URL string using the UTF-8 charset.
     * <p>
     * This method translates a string into {@code application/x-www-form-urlencoded} format.
     * It utilizes the modern {@link java.net.URLEncoder#encode(String, java.nio.charset.Charset)}
     * API available from Android Tiramisu (API 33) onwards.
     *
     * @param url The URL string to be encoded. Must not be null.
     * @return The encoded URL string, or an empty string if an encoding error occurs.
     */
    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static String encodeURL(@NonNull String url) {
        try {
            return encode(url, UTF_8);
        } catch (Exception error) {
            logger.error("Error found while encoding an url:", error);
            return "";
        }
    }

    /**
     * Decodes an application/x-www-form-urlencoded string using the UTF-8 encoding scheme.
     * This method reverses the transformation applied by the {@link #encodeURL(String)} method.
     *
     * @param url The encoded string to be decoded.
     * @return The decoded string, or an empty string if an error occurs during decoding.
     * @see URLDecoder#decode(String, String)
     */
    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static String decodeURL(@NonNull String url) {
        try {
            return URLDecoder.decode(url, UTF_8);
        } catch (Exception error) {
            logger.error("Error found while decoding an url:", error);
            return "";
        }
    }

    /**
     * Extracts the host URL (protocol and domain) from a given URL string.
     * <p>
     * This method parses the input string into a {@link URI} and reconstructs the base
     * address by combining the scheme and the host (e.g., "https://example.com").
     * </p>
     *
     * @param urlString The full URL string to parse.
     * @return The host URL including the protocol, or an empty string if the URL
     *         is malformed or an error occurs.
     */
    public static String extractHostUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception error) {
            logger.error("Error extracting host domain from url:", error);
            return "";
        }
    }

    /**
     * Determines whether the provided URL points to the host's root only, without a specific resource path.
     * <p>
     * This method evaluates the path component of the URL. It returns {@code true} if the path
     * is null, empty, or consists only of a single forward slash ("/").
     * </p>
     *
     * @param url The URL string to evaluate.
     * @return {@code true} if the URL contains only the host and no additional path;
     * {@code false} otherwise or if the URL is malformed.
     */
    public static boolean isHostOnly(String url) {
        try {
            URL parsedUrl = new URL(url);
            String path = parsedUrl.getPath();
            return path == null || path.isEmpty() || "/".equals(path);
        } catch (Exception error) {
            logger.error("Error retrieving host only domain:", error);
            return false;
        }
    }

    /**
     * Validates whether a given string follows a basic email address format.
     * <p>
     * This method performs a syntax check by ensuring the string contains exactly one '@'
     * character, non-empty local and domain parts, a dot within the domain segment,
     * and that the local part consists only of alphanumeric and standard special
     * characters (._%+-).
     * </p>
     *
     * @param email The email string to validate.
     * @return {@code true} if the string matches the basic email pattern; {@code false} otherwise.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || !email.contains("@")) return false;
        String[] parts = email.split("@");
        if (parts.length != 2) return false;

        String local = parts[0];
        String domain = parts[1];

        if (local.isEmpty() || domain.isEmpty()) return false;
        if (!domain.contains(".")) return false;
        return local.matches("^[A-Za-z0-9._%+-]+$");
    }

    /**
     * Asynchronously retrieves the title or description of a webpage.
     * <p>
     * This method handles specific logic for YouTube Music pages by using a dedicated parser.
     * For other pages, it fetches the HTML content (if not provided) and extracts the metadata
     * from Open Graph tags ({@code og:title} or {@code og:description}) using Jsoup.
     * </p>
     *
     * @param websiteUrl        The URL of the webpage to analyze.
     * @param returnDescription If {@code true}, attempts to retrieve the description;
     *                          if {@code false}, retrieves the title.
     * @param userGivenHtmlBody An optional pre-fetched HTML string. If null or empty,
     *                          the method will attempt to fetch the content automatically.
     * @param callback          A {@link ResponseHandler} to receive the resulting string,
     *                          or {@code null} if no metadata could be found or an error occurred.
     */
    public static void getWebpageTitleOrDescription(
            String websiteUrl, boolean returnDescription,
            String userGivenHtmlBody, ResponseHandler<String> callback) {
        try {
            boolean isYoutubeMusicPage = extractHostUrl(websiteUrl)
                    .toLowerCase(Locale.ROOT)
                    .contains("music.youtube");
            if (isYoutubeMusicPage) {
                String title = YtSteamExtractor.getTitle(websiteUrl);
                if (title != null && !title.isEmpty()) {
                    callback.onResult(title + "_Youtube_Music_Audio");
                    return;
                }
            }

            String htmlBody = userGivenHtmlBody;
            if (htmlBody == null || htmlBody.isEmpty()) {
                htmlBody = fetchWebPageContent(websiteUrl, true, 6);
            }

            if (htmlBody == null) {
                callback.onResult(null);
                return;
            }

            Document document = Jsoup.parse(htmlBody);
            Element metaTag = document.selectFirst(
                    returnDescription
                            ? "meta[property=og:description]"
                            : "meta[property=og:title]"
            );

            callback.onResult(metaTag != null
                    ? metaTag.attr("content")
                    : null);
        } catch (Exception error) {
            logger.error("Error parsing title from url", error);
            callback.onResult(null);
        }
    }

    /**
     * Attempts to find the favicon URL for a given website.
     * <p>
     * This method first checks for a standard {@code /favicon.ico} file at the root of the
     * website. If not found, it parses the HTML head of the website to search for
     * {@code <link>} tags with {@code rel} attributes containing "icon" or "shortcut icon".
     * It validates the existence of each potential favicon URL using {@link #isFaviconAvailable(String)}.
     * </p>
     *
     * @param websiteUrl The base URL of the website (e.g., "https://example.com").
     * @return The absolute URL of the favicon if found and accessible; otherwise, {@code null}.
     */
    public static String getFaviconUrl(String websiteUrl) {
        String standardFaviconUrl = websiteUrl + "/favicon.ico";
        if (isFaviconAvailable(standardFaviconUrl)) return standardFaviconUrl;

        try {
            Document doc = Jsoup.connect(websiteUrl).get();
            String cssQuery = "link[rel~=(icon|shortcut icon)]";
            for (Element element : doc.head().select(cssQuery)) {
                String href = element.attr("href");
                if (href.isEmpty()) continue;

                String faviconUrl = href.startsWith("http")
                        ? href
                        : websiteUrl + "/" + href;
                if (isFaviconAvailable(faviconUrl)) return faviconUrl;
            }
        } catch (Exception error) {
            logger.error(error);
        }

        return null;
    }

    /**
     * Checks if a favicon is available for the given website URL.
     * <p>
     * This method constructs a potential favicon URL by appending "/favicon.ico"
     * to the base URL and verifies its existence using an HTTP HEAD request.
     * </p>
     *
     * @param faviconUrl The base URL of the website to check for a favicon.
     * @return {@code true} if a favicon resource is found and returns an HTTP OK response;
     * {@code false} otherwise.
     */
    public static boolean isFaviconAvailable(String faviconUrl) {
        try {
            URL url = new URL(faviconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            return connection.getResponseCode()
                    == HttpURLConnection.HTTP_OK;
        } catch (Exception error) {
            logger.error("Error found while checking favicon url:", error);
            return false;
        }
    }

    /**
     * Fetches the size of a remote file in bytes from the specified URL.
     * <p>
     * This method performs an HTTP GET request to retrieve the {@code Content-Length} header.
     * Unlike {@link #getFileSizeFromUrl(URL)}, which uses a HEAD request, this uses GET
     * but only returns the size metadata.
     * </p>
     *
     * @param url The string representation of the URL to fetch the file size from.
     * @return The size of the file in bytes if successful (HTTP 200); otherwise,
     * returns -1 if an error occurs or the connection is unsuccessful.
     */
    public static long fetchFileSize(OkHttpClient httpClient, String url) {
        try {
            Request request = new Request.Builder().url(url).head().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String contentLength = response.header("Content-Length");
                return contentLength != null ? Long.parseLong(contentLength) : -1L;
            }
        } catch (Exception error) {
            logger.error("");
            return -1L;
        }
    }

    /**
     * Checks if the device is currently able to reach the internet by attempting to connect
     * to a reliable host.
     * <p>
     * This method verifies connectivity by opening a connection to "https://www.google.com".
     * It is more reliable than checking network interface flags as it confirms actual
     * end-to-end data transmission.
     * </p>
     *
     * @return {@code true} if a connection to the test host is successfully established;
     * {@code false} otherwise.
     */
    public static boolean isInternetConnected() {
        // Local check only: nothing is sent to any server.
        return NetworkUtils.isNetworkAvailable();
    }

    /**
     * Replaces all space characters in the provided string with the URL hex-encoded
     * equivalent ("%20").
     * <p>
     * This is useful for manual path normalization where the standard encoder might
     * convert spaces to plus signs ("+") instead of the percent-encoded hex value.
     * </p>
     *
     * @param inputUrl The string in which to encode spaces.
     * @return A string with all space characters replaced by "%20".
     */
    public static String encodeSpaceAsUrlHex(String inputUrl) {
        return inputUrl.replace(" ", "%20");
    }

    /**
     * Extracts the base domain from a given URL or domain string.
     * <p>
     * This method removes the protocol (e.g., "http://"), the "www." prefix,
     * and any trailing paths or query parameters to return the root domain.
     * </p>
     *
     * @param urlString The URL or domain string to process.
     * @return The base domain name (e.g., "example.com"), or an empty string
     * if the input is invalid or a parsing error occurs.
     */
    public static String getBaseDomain(String urlString) {
        try {
            String domain = new URL(urlString).getHost();
            String[] parts = domain.split("\\.");
            if (parts.length > 2) return parts[parts.length - 2];
            return parts[0];
        } catch (Exception error) {
            logger.error("Error found while getting base domain:", error);
            return null;
        }
    }

    /**
     * Extracts the host name (domain) from a given URL string.
     * <p>
     * This method parses the provided URL and returns the host component. If the URL
     * is malformed or invalid, an empty string is returned and the error is logged.
     * </p>
     *
     * @param urlString The full URL string to parse. Must not be null.
     * @return The host/domain name of the URL (e.g., "example.com"),
     * or an empty string if the URL is invalid.
     */
    @Nullable
    public static String getHostFromUrl(String urlString) {
        try {
            return urlString != null ? new URL(urlString).getHost() : null;
        } catch (Exception error) {
            logger.error(error);
            return null;
        }
    }

    /**
     * Determines if a URL is expired by checking its accessibility.
     * <p>
     * This method is a wrapper around {@link #isUrlAccessible(String)}. It considers a URL
     * "expired" if it is no longer accessible (i.e., the server does not return an HTTP 200 OK
     * response or a connection cannot be established).
     *
     * @param urlString The URL string to check for expiration.
     * @return {@code true} if the URL is inaccessible or an error occurs,
     * {@code false} if the URL is still valid and accessible.
     */
    public static boolean isUrlExpired(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();
            return connection.getResponseCode() >= 400;
        } catch (Exception error) {
            logger.error("Error found while checking url expiration:", error);
            return true;
        }
    }

    /**
     * Removes the "www." prefix from the host portion of a given URL.
     * <p>
     * This method parses the URL and, if the host starts with "www.", strips it while
     * preserving the rest of the URL (protocol, domain, path, etc.). If the host
     * does not contain "www.", the original URL is returned.
     * </p>
     *
     * @param url The full URL string from which to remove the "www." prefix. Must not be null.
     * @return The URL string without the "www." prefix, or the original URL if no prefix
     * was found or if the URL is malformed.
     */
    public static String removeWwwFromUrl(String url) {
        if (url == null) return "";
        try {
            return url.replaceFirst("www\\.", "");
        } catch (Exception error) {
            logger.error("Error found while removing www from url", error);
            return url;
        }
    }

    /**
     * Fetches the HTML content or body of a web page as a string using OkHttp.
     * <p>
     * This method performs a synchronous HTTP GET request to the specified URL.
     * It is designed to retrieve the full response body, which is typically used
     * for web scraping or reading page source code.
     *
     * @param url The string representation of the URL to fetch. Must not be null.
     * @return The response body as a string if the request is successful;
     * otherwise, returns {@code null} if an error occurs or the response is unsuccessful.
     */
    public static String fetchWebPageContent(String url, boolean retry, int numOfRetry) {
        if (retry && numOfRetry > 0) {
            int index = 0;
            String htmlBody = "";

            while (index < numOfRetry || htmlBody != null) {
                htmlBody = fetchMobileWebPageContent(url);
                if (htmlBody != null && !htmlBody.isEmpty()) return htmlBody;
                index++;
            }
        }

        return fetchMobileWebPageContent(url);
    }

    /**
     * Normalizes a URL that may already contain percent-encoded characters.
     * <p>
     * This method first decodes the provided URL to its raw form and then re-encodes it
     * using the UTF-8 charset. This ensures that the URL is consistently encoded and
     * prevents issues such as double-encoding or inconsistent escape sequences.
     * </p>
     *
     * @param url The potentially encoded URL string to normalize. Must not be null.
     * @return The normalized and UTF-8 encoded URL string, or an empty string if
     * an error occurs during decoding or encoding.
     */
    public static String normalizeEncodedUrl(String url) {
        try {
            String unescapedUrl = url.replace("\\/", "/");
            URI uri = new URI(unescapedUrl);
            String baseUrl = uri.getScheme()
                    + "://"
                    + uri.getHost()
                    + uri.getPath();

            String query = uri.getQuery();
            if (query == null) return baseUrl;
            Map<String, String> queryParams = new TreeMap<>();

            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                String key = URLDecoder.decode(pair[0], UTF_8);
                String value = pair.length > 1
                        ? URLDecoder.decode(pair[1], UTF_8)
                        : "";
                queryParams.put(key, value);
            }

            StringBuilder normalizedQuery = new StringBuilder();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (normalizedQuery.length() > 0) normalizedQuery.append("&");
                String encodedKey = encode(entry.getKey(), UTF_8);
                normalizedQuery.append(encodedKey);
                normalizedQuery.append("=");
                String encodedValue = encode(entry.getValue(), UTF_8);
                normalizedQuery.append(encodedValue);
            }

            return baseUrl + "?" + normalizedQuery;
        } catch (Exception error) {
            logger.error("Error normalize url", error);
            return url;
        }
    }

    /**
     * Fetches the content of a web page using a mobile User-Agent string.
     * <p>
     * This method utilizes {@link OkHttpClient} to perform a GET request, mimicking a
     * mobile browser (Chrome on Android) via the {@code User-Agent} header. This is
     * useful for retrieving the mobile-optimized version of a website's HTML.
     * </p>
     *
     * @param url The URL of the web page to fetch.
     * @return The string content of the web page if the request is successful;
     * otherwise, returns {@code null} if the response is unsuccessful or an error occurs.
     */
    private static String fetchMobileWebPageContent(String url) {
        return fetchMobileWebPageContent(url, false, 0, 30);
    }

    /**
     * Fetches the HTML content of a web page using a mobile User-Agent.
     * <p>
     * This method utilizes {@link OkHttpClient} to perform a GET request, mimicking a
     * mobile browser (Chrome on Android) to ensure the server returns mobile-optimized
     * content. It is configured to follow redirects and handles the network call
     * synchronously.
     * </p>
     *
     * @param url The URL of the web page to fetch. Must not be null.
     * @return The string content of the web page if the request is successful;
     * otherwise, returns {@code null} if an error occurs or the response body is empty.
     */
    public static String fetchMobileWebPageContent(
            String url, boolean allowRetry, int numOfRetry, int timeoutSeconds) {
        List<String> userAgents = APP_DEFAULT_MOBILE_AGENTS;
        OkHttpClient client = HttpClientProvider.getOkHttpClient(timeoutSeconds, timeoutSeconds);

        int maxAttempts = (allowRetry && numOfRetry > 0) ? numOfRetry : 1;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                String acceptLanguage = "en-US,en;q=0.5";
                int index = attempt % userAgents.size();
                String userAgent = userAgents.get(index);

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", APP_ALL_MEDIA_TYPES)
                        .header("Accept-Language", acceptLanguage)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        if (!body.isEmpty()) return body;
                    }
                }

                if (allowRetry && attempt < maxAttempts - 1) {
                    try {
                        Thread.sleep(200L * (attempt + 1));
                    } catch (InterruptedException ignored) {}
                }

            } catch (Exception error) {
                logger.error("Error fetching mobile web page content:", error);
            }
        }

        return null;
    }

    /**
     * A generic functional interface used for asynchronous communication or event handling.
     * This callback allows for returning a result of a specific type once a task is completed.
     *
     * @param <T> The type of the result value produced by the callback.
     */
    public interface ResponseHandler<T> {
        void onResult(T value);
    }
}