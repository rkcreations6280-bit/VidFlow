package sysModules.ytParser;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import coreUtils.library.process.LoggerUtils;

/**
 * Utility class for extracting metadata and stream information from YouTube URLs.
 * <p>
 * This class provides a high-level wrapper around the NewPipe Extractor library to retrieve
 * details such as video titles, thumbnails, and stream manifests. It includes a built-in
 * retry mechanism (up to 3 attempts) to handle transient network issues and YouTube's
 * "anti-bot" challenges.
 * </p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Automatic retry logic with configurable wait times based on error type.</li>
 */
public final class YtStreamParser {

    /**
     * Logger instance for tracking stream parsing attempts, debugging video URL processing,
     * and recording YouTube anti-bot or network-related errors.
     */
    private static final LoggerUtils logger = LoggerUtils.from(YtStreamParser.class);

    private YtStreamParser() {}

    /**
     * Retrieves the thumbnail URL for a specified YouTube video.
     *
     * @param youtubeVideoUrl The URL of the YouTube video.
     * @return The URL of the video thumbnail, or {@code null} if the information could not be retrieved.
     */
    public static String getThumbnail(String youtubeVideoUrl) {
        return getThumbnail(youtubeVideoUrl, null);
    }

    /**
     * Retrieves the URL of the primary thumbnail for a specified YouTube video.
     *
     * @param youtubeVideoUrl the URL of the YouTube video to fetch the thumbnail for.
     * @param onErrorFound    a callback triggered if an error occurs during the parsing process.
     * @return the URL of the first available thumbnail, or {@code null} if no thumbnails are found
     * or if the stream info could not be retrieved.
     */
    public static String getThumbnail(String youtubeVideoUrl, ErrorCallback onErrorFound) {
        StreamInfo info = fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound);
        if (info == null || info.getThumbnails().isEmpty()) return null;
        return info.getThumbnails().get(0).getUrl();
    }

    /**
     * Retrieves the title of a YouTube video using its URL.
     *
     * @param youtubeVideoUrl The full URL of the YouTube video.
     * @return The title of the video, or {@code null} if the information could not be retrieved.
     */
    public static String getTitle(String youtubeVideoUrl) {
        return getTitle(youtubeVideoUrl, null);
    }

    /**
     * Retrieves the title of the YouTube video from the specified URL.
     *
     * @param youtubeVideoUrl the URL of the YouTube video
     * @param onErrorFound    a callback to be executed if an error occurs after all retry attempts
     * @return the title of the video, or {@code null} if the information could not be retrieved
     */
    public static String getTitle(String youtubeVideoUrl, ErrorCallback onErrorFound) {
        StreamInfo info = fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound);
        return info != null ? info.getName() : null;
    }

    /**
     * Retrieves the stream information for a given YouTube video URL.
     * This is a convenience method that performs the fetch without a custom error callback.
     *
     * @param youtubeVideoUrl The full URL of the YouTube video to parse.
     * @return A {@link StreamInfo} object containing the video metadata and available streams,
     * or {@code null} if the information could not be retrieved after multiple attempts.
     */
    public static StreamInfo getStreamInfo(String youtubeVideoUrl) {
        return getStreamInfo(youtubeVideoUrl, null);
    }

    /**
     * Fetches detailed stream information for a specified YouTube video URL.
     * <p>
     * This method attempts to retrieve video metadata, including available streams,
     * titles, and thumbnails. It includes a retry mechanism (up to 3 attempts) to
     * handle transient network issues or anti-bot triggers.
     * </p>
     *
     * @param youtubeVideoUrl The full URL of the YouTube video to parse.
     * @param onErrorFound    A callback to handle any exceptions encountered after
     *                        all retry attempts have failed. Can be null.
     * @return A {@link StreamInfo} object containing the video details, or {@code null}
     * if the information could not be retrieved.
     */
    public static StreamInfo getStreamInfo(String youtubeVideoUrl, ErrorCallback onErrorFound) {
        return fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound);
    }

    /**
     * Internal method to fetch stream information from YouTube with a retry mechanism.
     * <p>
     * This method attempts to retrieve {@link StreamInfo} up to three times. It includes specific
     * handling for YouTube's anti-bot measures (detecting "page needs to be reloaded" errors)
     * by adjusting wait times between attempts. It validates that the returned info contains
     * actual video streams before returning.
     * </p>
     *
     * @param videoUrl     The URL of the YouTube video to fetch info for.
     * @param onErrorFound A callback to handle the exception if all retry attempts fail.
     * @return A {@link StreamInfo} object if successful; {@code null} if the information
     */
    private static StreamInfo fetchStreamInfoInternal(String videoUrl, ErrorCallback onErrorFound) {
        logger.debug("Fetching stream info: " + videoUrl);
        for (int attempt = 0; attempt < 3; attempt++) {

            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                boolean hasVideoOnlyStreams = info.getVideoOnlyStreams() != null
                        && !info.getVideoOnlyStreams().isEmpty();

                boolean hasVideoStreams = info.getVideoStreams() != null
                        && !info.getVideoStreams().isEmpty();

                if (hasVideoOnlyStreams || hasVideoStreams) return info;
                else logger.warning("Empty streams found on attempt " + (attempt + 1));
            } catch (Exception error) {
                String errorMessage = error.getMessage() != null
                        ? error.getMessage()
                        : "";

                boolean isReloadRequired = errorMessage.toLowerCase()
                        .contains("page needs to be reloaded");

                if (isReloadRequired) {
                    String logMessage = "YouTube Anti-Bot triggered (Reload Required) on attempt:";
                    logger.error(logMessage + (attempt + 1), error);
                } else {
                    String logMessage = "Error fetching stream info (attempt:";
                    logger.error(logMessage + (attempt + 1) + "/3): " + errorMessage, error);
                }

                if (attempt == 2) {
                    if (onErrorFound != null) {
                        onErrorFound.onError(error);
                    }
                } else {
                    long waitTime = isReloadRequired ? 1500L : 500L;
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Callback interface for handling exceptions encountered during the YouTube stream parsing process.
     * This is typically invoked when the parser fails to retrieve information after all retry attempts.
     */
    public interface ErrorCallback {
        void onError(Exception exception);
    }
}