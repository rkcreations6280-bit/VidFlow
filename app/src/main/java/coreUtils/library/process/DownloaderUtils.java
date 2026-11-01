package coreUtils.library.process;

import android.media.MediaMetadataRetriever;
import android.util.Pair;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreUtils.base.BaseApplication;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;
import dataRepo.downloads.DownloadInfo;

/**
 * Utility class providing helper methods for download-related operations.
 * <p>
 * This class includes functionality for formatting file sizes and download speeds,
 * calculating optimal parallel download segments based on user subscription status,
 * resolving file naming conflicts, and extracting media metadata (duration and resolution)
 * from both local files and remote URLs.
 * </p>
 *
 * <p>All methods in this class are static, and the class is final to prevent instantiation.</p>
 */
public final class DownloaderUtils {

    /**
     * Logger instance for this class, used to record error and debug information
     * related to download processing and utility operations.
     */
    private static final LoggerUtils logger = LoggerUtils.from(DownloaderUtils.class);

    /**
     * Shared formatter used to display numeric values with up to two decimal places.
     * Common use cases include formatting download percentages and transfer speeds.
     */
    private static final DecimalFormat decimalFormat = new DecimalFormat("##.##");

    private DownloaderUtils() {}

    /**
     * Formats the progress percentage of a download into a string representation.
     * The output is formatted according to a predefined decimal pattern (e.g., ##.##).
     *
     * @param downloadInfo The {@link DownloadInfo} object containing the progress percentage.
     * @return A formatted string representing the download percentage.
     */
    public static String getFormattedPercentage(DownloadInfo downloadInfo) {
        return decimalFormat.format(downloadInfo.progressPercentage);
    }

    /**
     * Formats a double value as a string using the predefined decimal format (##.##).
     *
     * @param input The double value to be formatted.
     * @return A string representation of the input, rounded to up to two decimal places.
     */
    public static String getFormatted(double input) {
        return decimalFormat.format(input);
    }

    /**
     * Formats a float value into a string using the predefined decimal format (##.##).
     *
     * @param input the float value to be formatted
     * @return a string representation of the float formatted to a maximum of two decimal places
     */
    public static String getFormatted(float input) {
        return decimalFormat.format(input);
    }

    /**
     * Calculates the optimal number of parallel download parts based on the file size
     * (between 1 and 5 parts).
     *
     * @param totalFileLength The total size of the file in bytes.
     * @return An integer representing the recommended number of segments to split the download into.
     */
    public static int getOptimalNumberOfDownloadParts(long totalFileLength) {
        long mb1 = 1_000_000L;
        long mb5 = 5_000_000L;
        long mb10 = 10_000_000L;
        long mb50 = 50_000_000L;
        long mb100 = 100_000_000L;
        long mb200 = 200_000_000L;
        long mb400 = 400_000_000L;

        if (totalFileLength < mb1) return 1;
        if (totalFileLength < mb5) return 2;
        if (totalFileLength < mb10) return 2;
        if (totalFileLength < mb50) return 3;
        if (totalFileLength < mb100) return 3;
        if (totalFileLength < mb200) return 4;
        if (totalFileLength < mb400) return 5;
        return 5;
    }

    /**
     * Extracts the duration of a downloaded media file and returns it as a formatted string.
     * <p>
     * This method checks if the file associated with the {@link DownloadInfo} is an audio or video file.
     * If valid, it retrieves the duration using {@link MediaMetadataRetriever} and formats it
     * (e.g., "(HH:mm:ss)").
     *
     * @param downloadInfo The download information containing the destination file path.
     * @return A formatted duration string in parentheses, or an empty string if the file
     *         is not media, is inaccessible, or an error occurs.
     */
    public static String fetchMediaDuration(DownloadInfo downloadInfo) {
        File downloadedFile = downloadInfo.getDestinationFile();
        DocumentFile documentFile = DocumentFile.fromFile(downloadedFile);
        if (FileStorageUtility.isAudio(documentFile) || FileStorageUtility.isVideo(documentFile)) {
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                if (!FileStorageUtility.isWritableFile(documentFile)) return "";

                BaseApplication appContext = BaseApplication.getInstance();
                retriever.setDataSource(appContext, documentFile.getUri());
                int metadataKeyDuration = MediaMetadataRetriever.METADATA_KEY_DURATION;
                String durationStr = retriever.extractMetadata(metadataKeyDuration);
                retriever.release();

                if (durationStr != null) {
                    long durationMs = Long.parseLong(durationStr);
                    String formatted = TimeFormats.formatVideoDuration(durationMs);
                    return "(" + formatted + ")";
                }
            } catch (Exception error) {
                logger.error("Error fetching media duration", error);
            }
        }
        return "";
    }

    /**
     * Converts a file size in bytes into a human-readable format (e.g., KiB, MiB, GiB).
     * <p>
     * This method uses binary prefixes (multiples of 1024) and returns a string
     * formatted to one decimal place with the appropriate unit.
     * </p>
     *
     * @param fileSizeInByte The size of the file in bytes.
     * @return A formatted string representing the file size with its unit (B, KB, MB, GB, etc.).
     */
    public static String getHumanReadableFormat(long fileSizeInByte) {
        if (fileSizeInByte < 1024) return fileSizeInByte + " B";
        int exp = (int) (Math.log(fileSizeInByte) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %sB",
                fileSizeInByte / Math.pow(1024, exp), pre);
    }

    /**
     * Converts a raw download speed in bytes per second into a human-readable string format
     * (e.g., "1.5 MB/s", "500 KB/s").
     * <p>
     * The method scales the input through TB/s, GB/s, MB/s, and KB/s based on 1024-byte
     * increments and formats the value using a two-decimal precision pattern.
     *
     * @param speedBytesPerSecond The transfer speed in bytes per second.
     * @return A formatted string representing the speed with the appropriate unit suffix.
     */
    public static String getHumanReadableSpeed(double speedBytesPerSecond) {
        long oneKB = 1024L;
        long oneMB = oneKB * 1024;
        long oneGB = oneMB * 1024;
        long oneTB = oneGB * 1024;

        if (speedBytesPerSecond >= oneTB) return decimalFormat.format(speedBytesPerSecond / oneTB) + "TB/s";
        if (speedBytesPerSecond >= oneGB) return decimalFormat.format(speedBytesPerSecond / oneGB) + "GB/s";
        if (speedBytesPerSecond >= oneMB) return decimalFormat.format(speedBytesPerSecond / oneMB) + "MB/s";
        if (speedBytesPerSecond >= oneKB) return decimalFormat.format(speedBytesPerSecond / oneKB) + "KB/s";
        return decimalFormat.format(speedBytesPerSecond) + "B/s";
    }

    /**
     * Generates a unique file name by appending a current millisecond timestamp to the base file name.
     * If the file name contains an extension, the timestamp is inserted before the extension.
     * Otherwise, it is appended to the end of the string.
     *
     * @param baseFileName The original file name (e.g., "video.mp4" or "document").
     * @return A string containing the unique file name (e.g., "video_1625000000000.mp4").
     */
    public static String generateUniqueDownloadFileName(String baseFileName) {
        long timestamp = System.currentTimeMillis();
        int extensionIndex = baseFileName.lastIndexOf('.');
        if (extensionIndex != -1) {
            return baseFileName.substring(0, extensionIndex) +
                    "_" + timestamp + baseFileName.substring(extensionIndex);
        } else {
            return baseFileName + "_" + timestamp;
        }
    }

    /**
     * Updates the download directory for a {@link DownloadInfo} object based on smart cataloging settings.
     * <p>
     * If smart directory categorization is enabled, it attempts to create a sub-folder based on the
     * file's category name within the root directory. If disabled or unsuccessful, it uses the root directory.
     * Finally, it sanitizes the resulting path by removing duplicate slashes and updates the
     * {@code downloadInfo.fileDirectory} field.
     * </p>
     *
     * @param downloadInfo The download metadata object containing directory paths and category
     *                    preferences to be updated.
     */
    public static void applySmartCatalogDirectory(DownloadInfo downloadInfo) {
        if (downloadInfo.isSmartDirEnabled) {
            String fileCategoryName = downloadInfo.getFileCategoryName();
            File downloadDir = new File(downloadInfo.fileDirectory);
            DocumentFile categoryFolder = DocumentFile.fromFile(downloadDir)
                    .createDirectory(fileCategoryName);
            if (categoryFolder != null && categoryFolder.canWrite()) {
                downloadInfo.fileCategoryName = fileCategoryName;
            }
        } else downloadInfo.fileCategoryName = "";

        boolean isCategoryValid = !downloadInfo.fileCategoryName.isEmpty();
        String categoryPart = isCategoryValid ? downloadInfo.fileCategoryName + "/" : "";
        String fullPath = downloadInfo.fileDirectory + "/" + categoryPart;
        downloadInfo.fileDirectory = StringHelper.removeDuplicateSlashes(fullPath);
    }

    /**
     * Resolves file name conflicts by prepending a numeric index to the file name.
     * <p>
     * If the destination file already exists, this method checks if the file name
     * starts with a numeric index (e.g., "1_filename.ext"). If it does, it increments
     * the index; otherwise, it starts with "1_". It repeats this process until
     * a unique file name is found that does not exist on the file system.
     * </p>
     *
     * @param downloadInfo The {@link DownloadInfo} object containing the current
     *                     file name and directory information to be updated.
     */
    public static void resolveFileNameConflict(DownloadInfo downloadInfo) {
        Pattern pattern = Pattern.compile("^(\\d+)_");
        while (downloadInfo.getDestinationFile().exists()) {
            Matcher matcher = pattern.matcher(downloadInfo.fileName);
            int index;
            if (matcher.find()) {
                String fileIndex = Objects.requireNonNull(matcher.group(1));
                int currentIndex = Integer.parseInt(fileIndex);
                downloadInfo.fileName = downloadInfo.fileName.replaceFirst("^(\\d+)_", "");
                index = currentIndex + 1;
            } else {
                index = 1;
            }
            downloadInfo.fileName = index + "_" + downloadInfo.fileName;
        }
    }

    /**
     * Converts a standard semicolon-separated cookie string into the Netscape HTTP Cookie File format.
     * This format is commonly used by command-line tools like curl and wget.
     * <p>
     * Each line in the output follows the tab-separated structure:
     * domain, flag (all domain), path, secure, expiration, name, and value.
     *
     * @param cookieString The raw cookie string (e.g., "name1=value1; name2=value2").
     * @return A formatted string compatible with Netscape cookie files.
     */
    public static String convertToNetscapeCookies(String cookieString) {
        String[] cookies = cookieString.split(";");
        String domain = "";
        String path = "/";
        String secure = "FALSE";
        String expiry = "2147483647";

        StringBuilder sb = new StringBuilder();
        sb.append("# Netscape HTTP Cookie File\n");
        sb.append("# This file was generated by the app.\n\n");

        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String value = parts[1].trim();
                sb.append(domain).append("\tFALSE\t")
                        .append(path).append("\t")
                        .append(secure).append("\t")
                        .append(expiry).append("\t")
                        .append(name).append("\t")
                        .append(value).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Extracts the resolution (width and height) of a video from a given URL.
     * <p>
     * This method uses {@link MediaMetadataRetriever} to fetch metadata from the network.
     * Note that this is a blocking network operation and should not be called on the UI thread.
     * </p>
     *
     * @param videoUrl The remote URL of the video file.
     * @return A {@link Pair} containing the width (first) and height (second) in pixels,
     *         or {@code null} if the resolution could not be extracted or an error occurred.
     */
    public static Pair<Integer, Integer> getVideoResolutionFromUrl(String videoUrl) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(videoUrl, new HashMap<>());
            int metadataKeyVideoWidth = MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH;
            int metadataKeyVideoHeight = MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT;

            String widthStr = retriever.extractMetadata(metadataKeyVideoWidth);
            String heightStr = retriever.extractMetadata(metadataKeyVideoHeight);
            try {retriever.release();} catch (Exception ignored) {}

            if (widthStr != null && heightStr != null) {
                int width = Integer.parseInt(widthStr);
                int height = Integer.parseInt(heightStr);
                logger.debug("Video resolution for " + videoUrl +
                        ": " + width + "x" + height);
                return new Pair<>(width, height);
            }
        } catch (Exception error) {
            logger.error("Error extracting video resolution: " + error.getMessage(), error);
        }
        return null;
    }

    /**
     * Extracts the duration of a video file from a given URL.
     * <p>
     * This method uses {@link MediaMetadataRetriever} to fetch the metadata of the remote
     * video file and retrieves the duration in milliseconds.
     *
     * @param videoUrl The network URL of the video file.
     * @return The duration of the video in milliseconds, or 0L if the duration
     *         could not be retrieved or an error occurred.
     */
    public static long getVideoDurationFromUrl(String videoUrl) {
        try(MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(videoUrl, new HashMap<>());
            int metadataKeyDuration = MediaMetadataRetriever.METADATA_KEY_DURATION;
            String durationStr = retriever.extractMetadata(metadataKeyDuration);
            long durationMs = (durationStr != null) ? Long.parseLong(durationStr) : 0L;
            logger.debug("Video duration extracted: " + durationMs + "ms");
            try { retriever.release(); } catch (Exception ignored) {}
            return durationMs;
        } catch (Exception e) {
            logger.error("Error extracting video duration: " + e.getMessage(), e);
            return 0L;
        }
    }

}
