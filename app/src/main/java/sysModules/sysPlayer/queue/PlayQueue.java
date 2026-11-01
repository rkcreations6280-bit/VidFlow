package sysModules.sysPlayer.queue;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import sysModules.sysPlayer.model.QueueEvent;

public abstract class PlayQueue implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final List<PlayQueueItem> streams;
    protected final List<PlayQueueItem> backup;
    protected final List<Integer> history;
    protected transient List<QueueListener> listeners;
    protected transient Handler mainHandler;
    protected volatile int index;
    protected int originalIndex;
    protected volatile boolean shuffled;
    protected volatile boolean disposed;

    protected PlayQueue(int initialIndex, @NonNull List<PlayQueueItem> initialItems) {
        this.streams = new ArrayList<>(initialItems);
        this.backup = new ArrayList<>();
        this.history = new ArrayList<>();
        this.index = clampIndex(initialIndex);
        this.originalIndex = this.index;
        this.shuffled = false;
        this.disposed = true;
        this.listeners = new CopyOnWriteArrayList<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
        if (!streams.isEmpty() && index >= 0 && index < streams.size()) {
            history.add(index);
        }
    }

    public synchronized void init() {
        disposed = false;
        if (listeners == null) listeners = new CopyOnWriteArrayList<>();
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        broadcast(new QueueEvent.InitEvent());
    }

    public synchronized void dispose() {
        disposed = true;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    public boolean isDisposed() { return disposed; }

    public synchronized void addListener(QueueListener l) {
        if (listeners != null && !listeners.contains(l)) listeners.add(l);
    }

    public synchronized void removeListener(QueueListener l) {
        if (listeners != null) listeners.remove(l);
    }

    protected void broadcast(QueueEvent e) {
        if (disposed || listeners == null) return;
        mainHandler.post(() -> {
            if (disposed) return;
            for (QueueListener l : listeners) {
                l.onQueueChanged(e);
            }
        });
    }

    public synchronized void setIndex(int newIndex) {
        int oldIndex = this.index;
        this.index = clampIndex(newIndex);
        if (oldIndex != this.index) {
            history.add(this.index);
            broadcast(new QueueEvent.SelectEvent(oldIndex, this.index));
        }
    }

    public synchronized void offsetIndex(int offset) {
        setIndex(this.index + offset);
    }

    public synchronized int previous() {
        if (history.size() <= 1) return index;
        history.remove(history.size() - 1);
        int prevIndex = history.get(history.size() - 1);
        int oldIndex = this.index;
        this.index = clampIndex(prevIndex);
        if (oldIndex != this.index) {
            broadcast(new QueueEvent.SelectEvent(oldIndex, this.index));
        }
        return this.index;
    }

    public synchronized void append(@NonNull List<PlayQueueItem> items) {
        if (items.isEmpty()) return;
        streams.addAll(items);
        if (shuffled) backup.addAll(items);
        broadcast(new QueueEvent.AppendEvent(items.size()));
        trimAutoQueued();
    }

    public synchronized void enqueueNext(@NonNull PlayQueueItem item, boolean skipIfSame) {
        int insertIndex = index + 1;
        if (skipIfSame && insertIndex < streams.size() && streams.get(insertIndex).isSameItem(item)) {
            return;
        }
        streams.add(insertIndex, item);
        if (shuffled) backup.add(insertIndex, item);
        broadcast(new QueueEvent.AppendEvent(1));
    }

    public synchronized void remove(int removeIndex) {
        int queueIndex = removeIndex;
        if (shuffled && removeIndex < backup.size()) {
            PlayQueueItem removed = streams.remove(removeIndex);
            int backupPos = backup.indexOf(removed);
            if (backupPos >= 0) backup.remove(backupPos);
            queueIndex = backupPos;
        } else {
            streams.remove(removeIndex);
        }
        if (index >= streams.size() && !streams.isEmpty()) {
            index = streams.size() - 1;
        } else if (removeIndex < index) {
            index--;
        } else if (removeIndex == index) {
            if (index >= streams.size()) index = streams.size() - 1;
        }
        broadcast(new QueueEvent.RemoveEvent(removeIndex, queueIndex));
    }

    public synchronized void move(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) return;
        if (fromIndex < 0 || fromIndex >= streams.size()) return;
        if (toIndex < 0 || toIndex >= streams.size()) return;
        PlayQueueItem item = streams.remove(fromIndex);
        streams.add(toIndex, item);
        if (index == fromIndex) {
            index = toIndex;
        } else if (fromIndex < index && toIndex >= index) {
            index--;
        } else if (fromIndex > index && toIndex <= index) {
            index++;
        }
        broadcast(new QueueEvent.MoveEvent(fromIndex, toIndex));
    }

    public synchronized void shuffle() {
        if (streams.size() <= 2) return;
        PlayQueueItem current = getItem();
        if (current == null) return;
        backup.clear();
        backup.addAll(streams);
        List<PlayQueueItem> toShuffle = new ArrayList<>(streams);
        toShuffle.remove(current);
        Collections.shuffle(toShuffle, new Random());
        streams.clear();
        streams.add(current);
        streams.addAll(toShuffle);
        index = 0;
        originalIndex = backup.indexOf(current);
        shuffled = true;
        notifyReorder();
    }

    public synchronized void unshuffle() {
        if (!shuffled || backup.isEmpty()) return;
        PlayQueueItem current = getItem();
        streams.clear();
        streams.addAll(backup);
        backup.clear();
        index = streams.indexOf(current);
        if (index < 0) index = 0;
        originalIndex = index;
        shuffled = false;
        notifyReorder();
    }

    public synchronized void toggleShuffle() {
        if (shuffled) unshuffle(); else shuffle();
    }

    public synchronized void setRecovery(int index, long position) {
        if (index < 0 || index >= streams.size()) return;
        streams.get(index).setRecoveryPosition(position);
        broadcast(new QueueEvent.RecoveryEvent(index, position));
    }

    public synchronized int error() {
        int oldIndex = this.index;
        this.index = clampIndex(this.index + 1);
        if (oldIndex != this.index) {
            history.add(this.index);
            broadcast(new QueueEvent.ErrorEvent(oldIndex, this.index));
        }
        return this.index;
    }

    public synchronized @Nullable PlayQueueItem getItem() {
        if (streams.isEmpty() || index < 0 || index >= streams.size()) return null;
        return streams.get(index);
    }

    public synchronized @Nullable PlayQueueItem getItem(int index) {
        if (streams.isEmpty() || index < 0 || index >= streams.size()) return null;
        return streams.get(index);
    }

    public synchronized int indexOf(@NonNull PlayQueueItem item) {
        return streams.indexOf(item);
    }

    public synchronized @NonNull List<PlayQueueItem> getStreams() {
        return new ArrayList<>(streams);
    }

    public synchronized int getIndex() { return index; }
    public synchronized int size() { return streams.size(); }
    public synchronized boolean isEmpty() { return streams.isEmpty(); }
    public synchronized boolean isShuffled() { return shuffled; }

    public synchronized boolean equalStreamsAndIndex(@Nullable PlayQueue other) {
        if (other == null || other.size() != size() || other.getIndex() != index) return false;
        for (int i = 0; i < streams.size(); i++) {
            if (!streams.get(i).isSameItem(other.streams.get(i))) return false;
        }
        return true;
    }

    private synchronized void trimAutoQueued() {
        if (streams.size() <= 2) return;
        PlayQueueItem last = streams.get(streams.size() - 1);
        PlayQueueItem secondLast = streams.get(streams.size() - 2);
        if (last.isAutoQueued() && secondLast.isAutoQueued()) {
            PlayQueueItem removed = streams.remove(streams.size() - 1);
            if (shuffled) backup.remove(removed);
        }
    }

    private void notifyReorder() {
        int fromSel = shuffled ? 0 : originalIndex;
        int toSel = index;
        broadcast(new QueueEvent.ReorderEvent(fromSel, toSel));
    }

    private int clampIndex(int idx) {
        if (streams.isEmpty()) return 0;
        if (idx < 0) return 0;
        if (idx >= streams.size()) return streams.size() - 1;
        return idx;
    }

    public abstract boolean isComplete();
    public abstract void fetch();
}
