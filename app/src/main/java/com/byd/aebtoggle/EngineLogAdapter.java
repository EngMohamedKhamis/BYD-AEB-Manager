package com.byd.aebtoggle;

import android.util.Log;

import com.byd.aebtoggle.core.EngineLog;

/**
 * Engine log lines go to logcat under the old service tag and (S3) to the in-app event log:
 * state changes, decisions, cancelled writes, safety nets. Errors keep only the exception's type
 * and message in the event log (the full stack trace stays in logcat).
 */
final class EngineLogAdapter implements EngineLog {

    private final String tag;

    EngineLogAdapter(String tag) {
        this.tag = tag;
    }

    @Override
    public void d(String message) {
        Log.d(tag, message);
        StateStore.event(message);
    }

    @Override
    public void w(String message) {
        Log.w(tag, message);
        StateStore.event("WARN " + message);
    }

    @Override
    public void e(String message, Throwable error) {
        Log.e(tag, message, error);
        StateStore.event("ERROR " + message + (error == null ? ""
                : " - " + error.getClass().getSimpleName() + ": " + error.getMessage()));
    }
}
