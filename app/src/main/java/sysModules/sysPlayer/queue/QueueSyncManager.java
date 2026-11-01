package sysModules.sysPlayer.queue;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.model.QueueEvent;

public class QueueSyncManager implements QueueListener {
    private static final LoggerUtils logger = LoggerUtils.from(QueueSyncManager.class);

    private final MediaEngine engine;
    private final Handler mainHandler;
    @Nullable private PlayQueue queue;
    private boolean loading;

    public QueueSyncManager(@NonNull MediaEngine engine) {
        this.engine = engine;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void bind(@NonNull PlayQueue queue) {
        unbind();
        this.queue = queue;
        queue.addListener(this);
        queue.init();
        loadCurrent(0);
    }

    public void unbind() {
        if (queue != null) {
            queue.removeListener(this);
            queue.dispose();
            queue = null;
        }
        loading = false;
    }

    @Nullable
    public PlayQueue getQueue() {
        return queue;
    }

    @Override
    public void onQueueChanged(@NonNull QueueEvent event) {
        switch (event.getType()) {
            case SELECT:
                loadCurrent(0);
                break;
            case ERROR:
                QueueEvent.ErrorEvent ee = (QueueEvent.ErrorEvent) event;
                logger.error("Queue error at index " + ee.getErrorIndex());
                if (queue != null) {
                    PlayQueueItem item = queue.getItem();
                    if (item != null && item.getError() != null) {
                        engine.onPlayerError(new Exception("Stream load failed", item.getError()));
                    }
                }
                break;
            case RECOVERY:
                QueueEvent.RecoveryEvent re = (QueueEvent.RecoveryEvent) event;
                engine.seekTo(re.getPosition());
                break;
        }
    }

    public void loadCurrent(long seekToPosition) {
        if (queue == null || loading) return;
        PlayQueueItem item = queue.getItem();
        if (item == null) return;

        loading = true;
        long seek = seekToPosition > 0 ? seekToPosition : item.getRecoveryPosition();
        if (seek == PlayQueueItem.RECOVERY_UNSET) seek = 0;

        if (item.isLocalItem()) {
            handleLocalItem(item, seek);
        } else {
            fetchAndLoad(item, seek);
        }
    }

    private void handleLocalItem(@NonNull PlayQueueItem item, long seek) {
        mainHandler.post(() -> {
            if (queue == null || queue.isDisposed()) return;
            loading = false;
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(Uri.parse(item.getUrl()))
                    .setMediaId(item.getUrl())
                    .build();
            engine.load(mediaItem, seek);
        });
    }

    private void fetchAndLoad(@NonNull PlayQueueItem item, long seek) {
        item.fetchStreamInfo(new PlayQueueItem.StreamCallback() {
            @Override
            public void onSuccess(StreamInfo info) {
                mainHandler.post(() -> {
                    if (queue == null || queue.isDisposed()) return;
                    loading = false;
                    engine.load(info, seek);
                });
            }

            @Override
            public void onError(Throwable error) {
                mainHandler.post(() -> {
                    loading = false;
                    logger.error("Failed to load stream: " + item.getUrl(), error);
                    item.setError(error);
                    if (queue != null) {
                        queue.error();
                        engine.onPlayerError(new Exception("Failed to load stream " + item.getTitle(), error));
                    }
                });
            }
        });
    }
}
