package com.byd.aebtoggle.core;

/** Monotonic milliseconds (Android: SystemClock.elapsedRealtime()). Never the wall clock. */
public interface Clock {
    long nowMs();
}
