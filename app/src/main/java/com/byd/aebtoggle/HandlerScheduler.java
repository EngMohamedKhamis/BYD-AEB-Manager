package com.byd.aebtoggle;

import android.os.Handler;
import android.os.SystemClock;

import com.byd.aebtoggle.core.Scheduler;

/**
 * Runs the engine's tasks on one HandlerThread. Every task is posted with one private token, so
 * cancelAll() removes only the engine's own tasks. Delays use uptimeMillis, the same monotonic
 * clock (not counting deep sleep) that the old ScheduledExecutorService used through nanoTime.
 */
final class HandlerScheduler implements Scheduler {

    private final Handler handler;
    private final Object token = new Object();

    HandlerScheduler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void schedule(long delayMs, Runnable task) {
        handler.postAtTime(task, token, SystemClock.uptimeMillis() + Math.max(0L, delayMs));
    }

    @Override
    public void cancelAll() {
        handler.removeCallbacksAndMessages(token);
    }
}
