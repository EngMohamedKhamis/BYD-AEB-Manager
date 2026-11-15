package com.byd.aebtoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        
        // v18.10 Hardening: Only respond to legitimate boot actions
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                        Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                        "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                        "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
                        
        if (!isBoot) {
            Log.w("BYDAebToggle", "Ignoring invalid broadcast action: " + action);
            return;
        }

        Log.i("BYDAebToggle", "Confirmed Boot Broadcast: " + action);
        
        AebSettings settings = new AebSettings(context);
        settings.setBootEnforceConsumed(false); // Reset Once-on-Start consumption on every new vehicle start
        Log.i("BYDAebToggle", "Reset Once-on-Start consumed state for new vehicle ignition");

        boolean startOnBoot = settings.startOnBoot(); // Default to true
        StateStore.event("boot broadcast " + action + ", auto-start " + (startOnBoot ? "on" : "off"));
        
        if (startOnBoot) {
            try {
                Intent serviceIntent = new Intent(context, AebService.class);
                serviceIntent.putExtra(AebService.EXTRA_IGNITION_CYCLE, true);
                context.startForegroundService(serviceIntent); // minSdk 31
            } catch (Exception e) {
                Log.e("BYDAebToggle", "Failed to start AebService on boot: " + e.getMessage());
            }
        }
    }
}
