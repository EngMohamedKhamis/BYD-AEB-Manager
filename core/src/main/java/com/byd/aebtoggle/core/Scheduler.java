package com.byd.aebtoggle.core;

/**
 * Runs tasks on the ONE engine thread after a delay. Android side: a Handler on a HandlerThread,
 * posting with a private token so cancelAll() removes only the engine's own tasks.
 */
public interface Scheduler {
    /** Runs task once, on the engine thread, after delayMs (0 = as soon as possible). */
    void schedule(long delayMs, Runnable task);

    /** Drops every task that has not started yet. May be called from any thread. */
    void cancelAll();
}
