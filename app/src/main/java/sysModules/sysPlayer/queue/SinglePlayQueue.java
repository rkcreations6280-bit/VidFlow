package sysModules.sysPlayer.queue;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Collections;
import java.util.List;

public final class SinglePlayQueue extends PlayQueue {
    public SinglePlayQueue(@NonNull StreamInfoItem item) {
        super(0, Collections.singletonList(new PlayQueueItem(item)));
    }

    public SinglePlayQueue(@NonNull StreamInfo info) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
    }

    public SinglePlayQueue(@NonNull PlayQueueItem item) {
        super(0, Collections.singletonList(item));
    }

    public SinglePlayQueue(@NonNull StreamInfo info, long startPosition) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
        getItem().setRecoveryPosition(startPosition);
    }

    public SinglePlayQueue(int index, @NonNull List<PlayQueueItem> items) {
        super(index, items);
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {
    }
}
