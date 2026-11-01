package sysModules.sysPlayer.model;

import java.io.Serializable;

public interface QueueEvent extends Serializable {
    Type getType();

    enum Type { INIT, SELECT, APPEND, REMOVE, MOVE, REORDER, RECOVERY, ERROR }

    final class InitEvent implements QueueEvent {
        @Override public Type getType() { return Type.INIT; }
        @Override public boolean equals(Object o) { return o instanceof InitEvent; }
        @Override public int hashCode() { return Type.INIT.hashCode(); }
    }

    final class SelectEvent implements QueueEvent {
        private final int oldIndex;
        private final int newIndex;
        public SelectEvent(int oldIndex, int newIndex) {
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
        }
        public int getOldIndex() { return oldIndex; }
        public int getNewIndex() { return newIndex; }
        @Override public Type getType() { return Type.SELECT; }
    }

    final class AppendEvent implements QueueEvent {
        private final int amount;
        public AppendEvent(int amount) { this.amount = amount; }
        public int getAmount() { return amount; }
        @Override public Type getType() { return Type.APPEND; }
    }

    final class RemoveEvent implements QueueEvent {
        private final int removeIndex;
        private final int queueIndex;
        public RemoveEvent(int removeIndex, int queueIndex) {
            this.removeIndex = removeIndex;
            this.queueIndex = queueIndex;
        }
        public int getRemoveIndex() { return removeIndex; }
        public int getQueueIndex() { return queueIndex; }
        @Override public Type getType() { return Type.REMOVE; }
    }

    final class MoveEvent implements QueueEvent {
        private final int fromIndex;
        private final int toIndex;
        public MoveEvent(int fromIndex, int toIndex) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }
        public int getFromIndex() { return fromIndex; }
        public int getToIndex() { return toIndex; }
        @Override public Type getType() { return Type.MOVE; }
    }

    final class ReorderEvent implements QueueEvent {
        private final int fromSelectedIndex;
        private final int toSelectedIndex;
        public ReorderEvent(int fromSelectedIndex, int toSelectedIndex) {
            this.fromSelectedIndex = fromSelectedIndex;
            this.toSelectedIndex = toSelectedIndex;
        }
        public int getFromSelectedIndex() { return fromSelectedIndex; }
        public int getToSelectedIndex() { return toSelectedIndex; }
        @Override public Type getType() { return Type.REORDER; }
    }

    final class RecoveryEvent implements QueueEvent {
        private final int index;
        private final long position;
        public RecoveryEvent(int index, long position) {
            this.index = index;
            this.position = position;
        }
        public int getIndex() { return index; }
        public long getPosition() { return position; }
        @Override public Type getType() { return Type.RECOVERY; }
    }

    final class ErrorEvent implements QueueEvent {
        private final int errorIndex;
        private final int queueIndex;
        public ErrorEvent(int errorIndex, int queueIndex) {
            this.errorIndex = errorIndex;
            this.queueIndex = queueIndex;
        }
        public int getErrorIndex() { return errorIndex; }
        public int getQueueIndex() { return queueIndex; }
        @Override public Type getType() { return Type.ERROR; }
    }
}
