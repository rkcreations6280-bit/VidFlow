package sysModules.sysPlayer.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;
import java.util.stream.Collectors;

import coreUtils.library.process.LoggerUtils;

abstract class AbstractInfoPlayQueue<T extends ListInfo<? extends InfoItem>>
        extends PlayQueue {
    private static final LoggerUtils logger = LoggerUtils.from(AbstractInfoPlayQueue.class);

    boolean isInitial;
    boolean isComplete;
    final int serviceId;
    final String baseUrl;
    @Nullable Page nextPage;
    private transient Thread fetchThread;

    protected AbstractInfoPlayQueue(@NonNull T info) {
        this(info, 0);
    }

    protected AbstractInfoPlayQueue(@NonNull T info, int index) {
        this(info.getServiceId(), info.getUrl(), info.getNextPage(),
                info.getRelatedItems().stream()
                        .filter(StreamInfoItem.class::isInstance)
                        .map(StreamInfoItem.class::cast)
                        .collect(Collectors.toList()),
                index);
    }

    protected AbstractInfoPlayQueue(int serviceId, String url,
                                     @Nullable Page nextPage,
                                     @NonNull List<StreamInfoItem> streams,
                                     int index) {
        super(index, extractListItems(streams));
        this.baseUrl = url;
        this.nextPage = nextPage;
        this.serviceId = serviceId;
        this.isInitial = streams.isEmpty();
        this.isComplete = !isInitial && !Page.isValid(nextPage);
    }

    protected abstract String getTag();

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    protected void onHeadFetched(@NonNull T result) {
        isInitial = false;
        if (!result.hasNextPage()) isComplete = true;
        nextPage = result.getNextPage();
        append(extractListItems(result.getRelatedItems().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList())));
    }

    protected void onMoreFetched(@NonNull ListExtractor.InfoItemsPage<? extends InfoItem> result) {
        if (!result.hasNextPage()) isComplete = true;
        nextPage = result.getNextPage();
        append(extractListItems(result.getItems().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList())));
    }

    protected void onFetchError(@NonNull Throwable e) {
        logger.error("Error fetching more items, marking as complete", e);
        isComplete = true;
        notifyChange();
    }

    protected void runOnBackground(@NonNull Runnable task) {
        cancelFetch();
        fetchThread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                onFetchError(e);
            }
        });
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    protected void cancelFetch() {
        if (fetchThread != null && fetchThread.isAlive()) {
            fetchThread.interrupt();
            fetchThread = null;
        }
    }

    private void notifyChange() {
        if (!disposed) broadcast(new sysModules.sysPlayer.model.QueueEvent.InitEvent());
    }

    @Override
    public void dispose() {
        super.dispose();
        cancelFetch();
    }

    private static List<PlayQueueItem> extractListItems(@NonNull List<StreamInfoItem> items) {
        return items.stream().map(PlayQueueItem::new).collect(Collectors.toList());
    }
}
