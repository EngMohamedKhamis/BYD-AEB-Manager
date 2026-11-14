package com.byd.aebtoggle.core;

import java.util.ArrayDeque;

final class ScriptedSettings implements SettingsView {
    int mode = Mode.ONCE_ON_START;
    boolean bootEnforceConsumed = false;
    /** Values returned first (one per call) before falling back to mode. */
    final ArrayDeque<Integer> script = new ArrayDeque<Integer>();

    @Override
    public int mode() {
        if (!script.isEmpty()) return script.poll().intValue();
        return mode;
    }

    @Override
    public boolean isBootEnforceConsumed() {
        return bootEnforceConsumed;
    }

    @Override
    public void setBootEnforceConsumed(boolean consumed) {
        this.bootEnforceConsumed = consumed;
    }
}
