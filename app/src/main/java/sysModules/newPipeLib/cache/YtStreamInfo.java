package sysModules.newPipeLib.cache;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.Serializable;
import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.newPipeLib.parsers.YtSteamExtractor;

/**
 * A highly optimized entity representing locally cached YouTube stream metadata.
 *
 * <p>This class serves as the primary data persistence model for the application's YouTube
 * integration. It acts as a bridge between the NewPipe extractor's transient data structures
 * ({@link StreamInfo} and {@link StreamInfoItem}) and the local database. By caching video
 * metadata, the application reduces API overhead, improves responsiveness, and enables
 * offline browsing of previously visited content.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>Dual-Source Population:</b> Seamlessly initializes from both detailed stream
 *       metadata ({@code StreamInfo}) and search/list results ({@code StreamInfoItem}).</li>
 *   <li><b>Indexing for Performance:</b> Critical fields like {@code url}, {@code streamId},
 *       and {@code streamType} are indexed to ensure sub-millisecond query times.</li>
 *   <li><b>Automatic Cache Lifecycle:</b> Implements a robust 14-day expiration policy.
 *       Stale records are automatically detected via {@link #isExpired()} and can be
 *       refreshed in the background.</li>
 *   <li><b>Media Intelligence:</b> Logic to automatically select the highest quality
 *       available thumbnail and uploader avatar based on pixel surface area.</li>
 *   <li><b>ID Normalization:</b> Robust parsing logic to extract consistent 11-character
 *       YouTube identifiers from various URL formats (shorts, embeds, standard).</li>
 * </ul>
 *
 * <h3>Threading and Persistence:</h3>
 * <p>Instances are persisted by {@link YtStreamInfoRepo} (SQLite). While the
 * class itself is a POJO, database operations (put, query) should ideally be performed
 * on background threads to maintain UI fluidity. The entity is designed to be {@link Serializable}
 * for easy pass-through between application components.</p>
 *
 * @see YtSteamExtractor
 * @see StreamInfo
 * @see StreamInfoItem
 */

public final class YtStreamInfo implements Serializable {

    /**
     * Logger utility for diagnostic messages and error tracking.
     * <p>
     * Static, so it is not persisted.
     * </p>
     */
    private final static LoggerUtils logger = LoggerUtils.from(YtStreamInfo.class);

    /**
     * Unique row identifier in the SQLite table.
     * Automatically assigned when the entity is first put into the box.
     */
    public long dbId = 0L;

    /**
     * The original URL of the stream from the source service.
     * Used for fast lookups and refreshing the cached metadata content.
     */
    public String url;

    /**
     * The unique identifier for the stream, such as a YouTube Video ID.
     * Extracted from the URL or provided directly by the stream extractor.
     */
    public String streamId;

    /**
     * User-defined or application-specific category assigned to this stream.
     * Separate from the original metadata category provided by the service.
     */
    public String customCategory;

    /**
     * The title or name of the stream/video as provided by the source.
     * Represents the primary display name used for the content in the UI.
     */
    public String name;

    /**
     * Full content or shortened summary of the stream's description.
     * Populated depending on whether detailed info or a search item was used.
     */
    public String description;

    /**
     * Timestamp in milliseconds indicating when the stream was uploaded.
     * Defaults to 0 if the upload date cannot be parsed or is unavailable.
     */
    public long uploadDate;

    /**
     * Total duration of the stream in seconds for playback calculation.
     * Helps determine if the content is categorized as short-form video.
     */
    public long duration;

    /**
     * Total number of views recorded for the stream at the time of caching.
     * Useful for displaying popularity metrics and sorting cached content.
     */
    public long viewCounts;

    /**
     * Timestamp in milliseconds when this record was stored in the local cache.
     * Serves as the starting point for calculating metadata expiration.
     */
    public long cacheCreatedAt;

    /**
     * Timestamp in milliseconds when this record is considered expired.
     * Metadata should be refreshed from the remote source after this time.
     */
    public long cacheExpiryTime;

    /**
     * Display name of the channel or user who uploaded the stream content.
     * Typically, matches the branding of the creator's channel or profile.
     */
    public String uploaderName;

    /**
     * The full URL of the uploader's channel or profile page.
     * Used for navigating to the creator's content within the application.
     */
    public String uploaderUrl;

    /**
     * Highest quality URL of the uploader's avatar image available.
     * Resolved from the extractor's image list for creator branding.
     */
    public String uploaderAvatarUrl;

    /**
     * URL of the highest quality thumbnail image available for the stream.
     * Determined by selecting the image with the largest surface area.
     */
    public String thumbnailUrl;

    /**
     * Original category of the stream as provided by the content source.
     * Examples include Music, Entertainment, Gaming, or Education.
     */
    public String category;

    /**
     * Comma-separated string containing tags or keywords for the stream.
     * Used for content categorization and enhancing internal search results.
     */
    public String tags;

    /**
     * Content format or broadcast status derived from the stream type enum.
     * Distinguishes between standard videos, audio streams, and live broadcasts.
     */
    public String streamType;

    /**
     * Unique identifier of the service this stream belongs to (e.g., YouTube).
     * Distinguishes between content providers supported by the NewPipe extractor.
     */
    public int serviceId;

    /**
     * Minimum age required to view the stream content as per source ratings.
     * Used for content filtering and enforcing parental control policies.
     */
    public int ageLimit;

    /**
     * Indicates whether the stream is classified as short-form content.
     * Determined by checking if the duration is between 1 and 60 seconds.
     */
    public boolean isShortFormContent;

    /**
     * Indicates whether the stream has available subtitles or closed captions.
     * Enables efficient filtering for accessibility and language options.
     */
    public boolean hasSubtitles;

    /**
     * Timestamp in milliseconds indicating when the user last watched this stream.
     * Used by the interest analyzer to generate personalized recommendations.
     */
    public long lastWatchedAt;

    /**
     * Total number of times the user has watched this specific stream.
     */
    public int watchCount;

    /**
     * The reason why this stream was recommended.
     */
    public int recommendationReasonOrdinal = -1;

    /**
     * The source where this content was discovered.
     */
    public int contentSourceOrdinal = 0;

    /**
     * Default constructor used when loading objects from the database.
     * <p>
     * This constructor is used when loading objects from the database. To initialize
     * the object with data from a stream source, use the constructors that accept
     * {@link StreamInfo} or {@link StreamInfoItem} instead.
     * </p>
     */
    public YtStreamInfo() {}

    /**
     * Constructs a new {@code YtStreamInfo} instance by extracting and mapping
     * data from a NewPipe {@link StreamInfo} object.
     * <p>
     * This constructor initializes the cache timestamps and populates fields
     * including video metadata, uploader details, and stream properties.
     * </p>
     *
     * @param streamInfo the {@link StreamInfo} containing the raw data to be cached
     */
    public YtStreamInfo(StreamInfo streamInfo) {
        populateFromStreamInfo(streamInfo);
    }

    /**
     * Constructs a new {@code YtStreamInfo} instance by extracting and mapping data
     * from a {@link StreamInfoItem}.
     * <p>
     * This constructor is typically used when populating stream metadata from list
     * results (such as search results or trending feeds). It initializes cache
     * timestamps and parses essential fields like the video ID and thumbnail.
     * </p>
     *
     * @param streamInfoItem the stream item containing the metadata to be cached
     */
    public YtStreamInfo(StreamInfoItem streamInfoItem) {
        populateFromStreamInfoItem(streamInfoItem);
    }

    /**
     * Extracts the YouTube video identifier from various URL formats.
     * <p>
     * This method supports standard watch URLs, short links (YouTube.be), Shorts,
     * and embedded player URLs. It attempts to isolate the unique 11-character
     * video ID.
     * </p>
     *
     * @param url The YouTube URL to parse.
     * @return The 11-character video ID if successfully extracted;
     * {@code null} if the URL is invalid, empty, or the ID cannot be found.
     */
    public static String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        String id = null;
        try {
            if (url.contains("youtu.be/")) {
                id = url.substring(url.indexOf("youtu.be/") + 9);
            } else if (url.contains("youtube.com/shorts/")) {
                id = url.substring(url.indexOf("shorts/") + 7);
            } else if (url.contains("v=")) {
                int start = url.indexOf("v=") + 2;
                id = url.substring(start);
            } else if (url.contains("embed/")) {
                id = url.substring(url.indexOf("embed/") + 6);
            }

            if (id != null && id.length() >= 11) {
                return id.substring(0, 11);
            }
        } catch (Exception error) {
            logger.error("Failed extracting video id: ", error);
            return null;
        }
        return null;
    }

    /**
     * Populates the fields of this entity using a {@link StreamInfoItem}.
     * <p>
     * This method is typically used to initialize or update cache data from search results
     * or list items. It calculates cache expiration (14 days), extracts the stream ID,
     * selects the highest quality thumbnail, and determines if the content is
     * short-form based on duration.
     * </p>
     *
     * @param streamInfoItem the stream item containing the metadata to be cached;
     *                       if null, the method returns immediately without making changes.
     */
    private void populateFromStreamInfoItem(StreamInfoItem streamInfoItem) {
        if (streamInfoItem == null) return;

        this.cacheCreatedAt = System.currentTimeMillis();
        this.cacheExpiryTime = cacheCreatedAt + (14 * 24 * 60 * 60 * 1000L);

        this.url = streamInfoItem.getUrl();
        this.streamId = extractVideoId(url);
        this.name = streamInfoItem.getName();
        this.description = streamInfoItem.getShortDescription();
        if (streamInfoItem.getUploadDate() != null) {
            try {
                this.uploadDate = streamInfoItem.getUploadDate().offsetDateTime()
                        .toInstant().toEpochMilli();
            } catch (Exception ignored) {
                this.uploadDate = 0;
            }
        }

        this.viewCounts = streamInfoItem.getViewCount();
        this.serviceId = streamInfoItem.getServiceId();
        this.duration = streamInfoItem.getDuration();
        this.uploaderName = streamInfoItem.getUploaderName();
        this.uploaderUrl = streamInfoItem.getUploaderUrl();
        this.thumbnailUrl = getBestImageQuality(streamInfoItem.getThumbnails());
        if (streamInfoItem.getStreamType() != null) {
            this.streamType = streamInfoItem.getStreamType().toString();
        }

        this.isShortFormContent = (duration > 0 && duration <= 60);
    }

    /**
     * Populates the current instance with detailed metadata from a {@link StreamInfo} object.
     * <p>
     * This method maps various properties from the NewPipe extractor's {@code StreamInfo}
     * to this entity's fields, including uploader details, thumbnails, category,
     * and duration. It also initializes cache timing: the creation timestamp is set
     * to the current system time and the expiry is set to 14 days from creation.
     * </p>
     *
     * @param streamInfo the {@link StreamInfo} containing the source data;
     *                   if {@code null}, the method returns immediately without
     *                   modifying any fields.
     */
    private void populateFromStreamInfo(StreamInfo streamInfo) {
        if (streamInfo == null) return;

        this.cacheCreatedAt = System.currentTimeMillis();
        this.cacheExpiryTime = cacheCreatedAt + (14 * 24 * 60 * 60 * 1000L);

        this.url = streamInfo.getUrl();
        this.streamId = streamInfo.getId();
        this.name = streamInfo.getName();
        this.serviceId = streamInfo.getServiceId();
        this.duration = streamInfo.getDuration();
        this.viewCounts = streamInfo.getViewCount();

        Description desc = streamInfo.getDescription();
        if (desc != null) {
            this.description = desc.getContent();
        }

        if (streamInfo.getUploadDate() != null) {
            try {
                this.uploadDate =
                        streamInfo.getUploadDate().offsetDateTime()
                                .toInstant().toEpochMilli();
            } catch (Exception ignored) {
                this.uploadDate = 0;
            }
        }

        this.uploaderName = streamInfo.getUploaderName();
        this.uploaderUrl = streamInfo.getUploaderUrl();
        this.uploaderAvatarUrl = getBestImageQuality(streamInfo.getUploaderAvatars());

        this.category = streamInfo.getCategory();
        this.ageLimit = streamInfo.getAgeLimit();

        if (streamInfo.getStreamType() != null) {
            this.streamType = streamInfo.getStreamType().toString();
        }

        this.isShortFormContent = (duration > 0 && duration <= 60);
        this.hasSubtitles = safeGetBoolean(() -> streamInfo.getSubtitles() != null &&
                !streamInfo.getSubtitles().isEmpty());

        List<String> tagList = streamInfo.getTags();
        if (tagList != null && !tagList.isEmpty()) {
            this.tags = String.join(",", tagList);
        }

        this.thumbnailUrl = getBestImageQuality(streamInfo.getThumbnails());
    }

    /**
     * Safely executes a boolean supplier and handles any potential exceptions.
     * <p>
     * This method is used to wrap calls to external library methods that might throw
     * exceptions during property retrieval. If an exception occurs, it is logged
     * and a default value of {@code false} is returned.
     * </p>
     *
     * @param supplier A functional interface implementation that returns a boolean.
     * @return The boolean value returned by the supplier, or {@code false} if an exception occurs.
     */
    private boolean safeGetBoolean(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Exception error) {
            logger.error("Error retrieving boolean property", error);
            return false;
        }
    }

    /**
     * Represents a supplier of {@code boolean}-valued results.
     * <p>
     * This is a functional interface used to safely wrap calls to extractor methods
     * that might throw exceptions during data population.
     * </p>
     */
    private interface BooleanSupplier {
        /**
         * Gets a result.
         *
         * @return a result
         */
        boolean getAsBoolean();
    }

    /**
     * Determines the highest quality image URL from a list of images based on surface
     * area (width * height).
     * <p>
     * The method iterates through the provided list to find the image with the largest
     * dimensions. If no image with a valid area is found, it falls back to the last image
     * in the list.
     * </p>
     *
     * @param images a list of {@link Image} objects to evaluate
     * @return the URL of the largest image found, the URL of the last image in the list as
     * a fallback, or {@code null} if the list is null or empty
     */
    private String getBestImageQuality(List<Image> images) {
        if (images == null || images.isEmpty()) return null;

        Image best = null;
        int maxArea = -1;

        for (Image image : images) {
            int area = image.getWidth() * image.getHeight();
            if (area > maxArea) {
                maxArea = area;
                best = image;
            }
        }

        return (best != null && maxArea > 0) ?
                best.getUrl() : images.get(images.size() - 1).getUrl();
    }

    /**
     * Checks whether the cached stream information has expired.
     * <p>
     * The expiration is determined by comparing the current system time against
     * the {@code cacheExpiryTime} timestamp.
     * </p>
     *
     * @return {@code true} if the cache is expired and should be refreshed;
     * {@code false} otherwise.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > cacheExpiryTime;
    }

    /**
     * Refreshes the cached stream metadata by fetching fresh data from the remote source
     * if the current information has expired.
     * <p>
     * This method uses {@link YtSteamExtractor} to fetch updated {@link StreamInfo} and
     * repopulates the current instance if successful.
     * </p>
     */
    public void updateStreamDetails() {
        try {
            if (url != null && !url.isEmpty() && isExpired()) {
                StreamInfo steamInfo = YtSteamExtractor.getStreamInfo(url);
                if (steamInfo != null) {
                    populateFromStreamInfo(steamInfo);
                }
            }
        } catch (Exception error) {
            logger.error("Failed updating stream info", error);
        }
    }
}
