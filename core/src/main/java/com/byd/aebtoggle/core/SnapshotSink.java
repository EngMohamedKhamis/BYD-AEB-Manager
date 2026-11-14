package com.byd.aebtoggle.core;

/** Receives every published snapshot (called on the engine thread, and once from stop()). */
public interface SnapshotSink {
    void publish(Snapshot snapshot);
}
