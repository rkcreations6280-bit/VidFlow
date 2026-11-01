package sysModules.sysPlayer.queue;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.Collections;
import java.util.List;

import coreUtils.library.process.LoggerUtils;

/**
 * A play queue that fetches streams from a YouTube channel tab (e.g., Streams, Shorts, Live).
 * Uses {@link ChannelTabInfo} from NewPipeExtractor to load the first page and
 * subsequent pages of channel tab content.
 *
 * <p>Two construction patterns:</p>
 * <ul>
 *   <li>Full constructor — with pre-loaded items (e.g., from a visible channel tab fragment)</li>
 *   <li>Simple constructor — starts empty, triggers lazy loading on first {@link #fetch()}</li>
 * </ul>
 */
public final class ChannelTabPlayQueue extends AbstractInfoPlayQueue<ChannelTabInfo> {
    private static final LoggerUtils logger = LoggerUtils.from(ChannelTabPlayQueue.class);

    private final ListLinkHandler linkHandler;

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler linkHandler,
                               final @Nullable org.schabi.newpipe.extractor.Page nextPage,
                               final List<org.schabi.newpipe.extractor.stream.StreamInfoItem> streams,
                               final int index) {
        super(serviceId, linkHandler.getUrl(), nextPage, streams, index);
        this.linkHandler = linkHandler;
    }

    public ChannelTabPlayQueue(final int serviceId,
                               final ListLinkHandler linkHandler) {
        this(serviceId, linkHandler, null, Collections.emptyList(), 0);
    }

    @Override
    protected String getTag() {
        return "ChannelTabPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (isInitial) {
            runOnBackground(() -> {
                try {
                    final ChannelTabInfo info = ChannelTabInfo.getInfo(
                            NewPipe.getService(serviceId), linkHandler);
                    onHeadFetched(info);
                } catch (Exception e) {
                    onFetchError(e);
                }
            });
        } else {
            runOnBackground(() -> {
                try {
                    final ListExtractor.InfoItemsPage<?> page = ChannelTabInfo.getMoreItems(
                            NewPipe.getService(serviceId), linkHandler, nextPage);
                    onMoreFetched(page);
                } catch (Exception e) {
                    onFetchError(e);
                }
            });
        }
    }
}
