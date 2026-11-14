package com.byd.aebtoggle.core;

/** Logging without android.util.Log, so the engine can run on a plain JVM. */
public interface EngineLog {
    void d(String message);

    void w(String message);

    void e(String message, Throwable error);
}
