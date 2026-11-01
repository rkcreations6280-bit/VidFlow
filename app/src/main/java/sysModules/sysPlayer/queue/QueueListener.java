package sysModules.sysPlayer.queue;

import sysModules.sysPlayer.model.QueueEvent;

@FunctionalInterface
public interface QueueListener {
    void onQueueChanged(QueueEvent event);
}
