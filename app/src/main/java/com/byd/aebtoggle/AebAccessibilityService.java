package com.byd.aebtoggle;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AebAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to process events, just stay alive
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        boolean startOnBoot = new AebSettings(this).startOnBoot();
        StateStore.event("accessibility connected, auto-start " + (startOnBoot ? "on" : "off"));
        if (!startOnBoot) {
            Log.i("AEB_Toggle", "Accessibility Service Connected; Start on Boot is off, skipping AebService start.");
            return;
        }
        Log.i("AEB_Toggle", "Accessibility Service Connected. System is now monitoring AEB.");
        try {
            Intent serviceIntent = new Intent(this, AebService.class);
            serviceIntent.putExtra(AebService.EXTRA_BOOT_START, true);
            startForegroundService(serviceIntent); // minSdk 31
        } catch (Exception e) {
            Log.e("AEB_Toggle", "Failed to start AebService from Accessibility: " + e.getMessage());
        }
    }
}
