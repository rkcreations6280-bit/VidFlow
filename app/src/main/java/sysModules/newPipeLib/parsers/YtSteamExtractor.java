package sysModules.newPipeLib.parsers;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.newPipeLib.cache.YtStreamInfo;
import sysModules.newPipeLib.cache.YtStreamInfoRepo;

public final class YtSteamExtractor {
    private static final LoggerUtils logger = LoggerUtils.from(YtSteamExtractor.class);

    private YtSteamExtractor() {}

    public static String getThumbnail(String youtubeVideoUrl) {
        YtStreamInfo cached = YtStreamInfoRepo.findStreamInfo(youtubeVideoUrl);
        if (cached != null) return cached.thumbnailUrl;

        StreamInfo info = getStreamInfo(youtubeVideoUrl);
        return (info != null && !info.getThumbnails().isEmpty())
                ? info.getThumbnails().get(0).getUrl() : null;
    }

    public static String getTitle(String youtubeVideoUrl) {
        YtStreamInfo cached = YtStreamInfoRepo.findStreamInfo(youtubeVideoUrl);
        if (cached != null) return cached.name;

        StreamInfo info = getStreamInfo(youtubeVideoUrl);
        return info != null ? info.getName() : null;
    }

    public static StreamInfo getStreamInfo(String youtubeVideoUrl) {
        return getStreamInfo(youtubeVideoUrl, null);
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
    public static String getBestImageQuality(List<Image> images) {
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
    
    public static StreamInfoItem getStreamItemFromUrl(String url) throws Exception {
        StreamInfo info = StreamInfo.getInfo(url);
        StreamInfoItem item = new StreamInfoItem(
                info.getServiceId(),
                info.getUrl(),
                info.getName(),
                info.getStreamType()
        );

        item.setUploaderName(info.getUploaderName());
        item.setUploaderUrl(info.getUploaderUrl());
        info.getTags();

        if (!info.getThumbnails().isEmpty()) {
            item.setThumbnails(info.getThumbnails());
        }

        return item;
    }

    public static StreamInfo getStreamInfo(String videoUrl, ErrorCallback onErrorFound) {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            logger.error("Empty or null URL provided.");
            return null;
        }

        StreamInfo info = getStreamInfoWithRetry(videoUrl, onErrorFound);
        if (info != null) YtStreamInfoRepo.save(new YtStreamInfo(info));
        return info;
    }

    private static StreamInfo getStreamInfoWithRetry(String videoUrl, ErrorCallback onErrorFound) {
        logger.debug("Fetching stream info from network: " + videoUrl);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                if (hasValidStreams(info)) return info;
                logger.warning("Attempt " + attempt + ": No valid streams found.");
            } catch (Exception error) {
                handleException(error, attempt, onErrorFound);
                if (attempt < 3) {
                    applyBackoff(error);
                } else if (onErrorFound != null) {
                    onErrorFound.onError(error);
                }
            }
        }
        return null;
    }

    private static boolean hasValidStreams(StreamInfo info) {
        return (info.getVideoOnlyStreams() != null && !info.getVideoOnlyStreams().isEmpty()) ||
                (info.getVideoStreams() != null && !info.getVideoStreams().isEmpty());
    }

    private static void handleException(Exception error, int attempt, ErrorCallback callback) {
        String msg = error.getMessage() != null ? error.getMessage() : "";
        boolean isBot = msg.toLowerCase().contains("page needs to be reloaded");

        String logPrefix = isBot ? "YouTube Anti-Bot (Reload Required)" : "Network Error";
        logger.error(logPrefix + " on attempt " + attempt + "/3: " + msg, error);
    }

    private static void applyBackoff(Exception error) {
        String msg = error.getMessage() != null ? error.getMessage() : "";
        boolean isBot = msg.toLowerCase().contains("page needs to be reloaded");

        long waitTime = isBot ? 1500L : 500L;
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public interface ErrorCallback {
        void onError(Exception exception);
    }
}