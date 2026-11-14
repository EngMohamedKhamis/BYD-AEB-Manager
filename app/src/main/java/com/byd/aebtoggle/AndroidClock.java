package com.byd.aebtoggle;

import android.os.SystemClock;

import com.byd.aebtoggle.core.Clock;

/** Monotonic time for the engine: elapsedRealtime, as AebService v18.22 used for the discovery window. */
final class AndroidClock implements Clock {
    @Override
    public long nowMs() {
        return SystemClock.elapsedRealtime();
    }
}
