package com.byd.aebtoggle.core;

import java.util.ArrayList;
import java.util.List;

final class RecordingLog implements EngineLog {
    final List<String> lines = new ArrayList<String>();

    @Override
    public void d(String message) {
        lines.add("D " + message);
    }

    @Override
    public void w(String message) {
        lines.add("W " + message);
    }

    @Override
    public void e(String message, Throwable error) {
        lines.add("E " + message + " :: " + error);
    }

    boolean contains(String part) {
        for (String l : lines) if (l.contains(part)) return true;
        return false;
    }
}
