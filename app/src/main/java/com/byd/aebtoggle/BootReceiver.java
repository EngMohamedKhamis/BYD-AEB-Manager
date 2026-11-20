package com.byd.aebtoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.byd.aebtoggle.core.SettingsRules;

public class BootReceiver extends BroadcastReceiver {
    private static volatile long lastOfflineBootElapsedMs = -1L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!SettingsRules.isBootBroadcastAction(action)) {
            if (action != null) {
                Log.w("BYDAebToggle", "Ignoring invalid broadcast action: " + action);
            }
            return;
        }

        Log.i("BYDAebToggle", "Confirmed Boot Broadcast: " + action);

        AebSettings settings = new AebSettings(context);
        boolean startOnBoot = settings.startOnBoot(); // Default to true
        StateStore.event("boot broadcast " + action + ", auto-start " + (startOnBoot ? "on" : "off"));

        if (startOnBoot) {
            try {
                Intent serviceIntent = new Intent(context, AebService.class);
                serviceIntent.putExtra(AebService.EXTRA_IGNITION_CYCLE, true);
                context.startForegroundService(serviceIntent); // minSdk 31
            } catch (Exception e) {
                Log.e("BYDAebToggle", "Failed to start AebService on boot: " + e.getMessage());
                if (!StateStore.notifyIgnitionCycle()) {
                    long now = SystemClock.elapsedRealtime();
                    long attachedAt = StateStore.engineAttachedAtElapsedMs();
                    if (SettingsRules.shouldResetOfflineBoot(attachedAt, lastOfflineBootElapsedMs, now)) {
                        lastOfflineBootElapsedMs = now;
                        settings.setBootEnforceConsumed(false);
                    }
                }
            }
        } else {
            long now = SystemClock.elapsedRealtime();
            long attachedAt = StateStore.engineAttachedAtElapsedMs();
            if (SettingsRules.shouldResetOfflineBoot(attachedAt, lastOfflineBootElapsedMs, now)) {
                lastOfflineBootElapsedMs = now;
                if (attachedAt >= 0L) {
                    try {
                        context.stopService(new Intent(context, AebService.class));
                    } catch (Exception ignored) {
                    }
                }
                settings.setBootEnforceConsumed(false);
                Log.i("BYDAebToggle", "Reset Once-on-Start consumed state for new ignition (auto-start off)");
            }
        }
    }
}
