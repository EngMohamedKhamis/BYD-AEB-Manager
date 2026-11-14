package com.byd.aebtoggle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.byd.aebtoggle.core.Engine;
import com.byd.aebtoggle.core.Mode;

/**
 * AEB Service — v19.0 (refactor S1/S2)
 *
 * Thin Android shell around com.byd.aebtoggle.core.Engine, the tested port of the 9-state actor
 * that lived in this class up to v18.22 (same states, delays and counters; pinned by the core
 * self-test, see REFACTOR_PLAN.md appendix C). This class only owns the foreground notification,
 * the HUD (HudController, S6) and the engine thread. State for the screen and the HUD goes through
 * StateStore (S2); there are no public static fields any more.
 *
 * States (in Engine):
 *   CONNECTING            → Establish ADB. No commands sent.
 *   DISCOVERING            → Stability check: 3 consecutive reads over ≥5s.
 *   ENFORCING_BOOT_EARLY   → First-time disable at boot.
 *   VERIFYING_BOOT         → Progressive confirmation: 1s, 3s, 6s.
 *   BACKOFF_SETTLING       → Cool-down after boot verification.
 *   STEADY                 → Low-frequency watchdog (10s). Debounced re-enablement.
 *   RECOVERY_ENFORCE       → Runtime re-disable.
 *   VERIFYING_RUNTIME      → Progressive confirmation: 2s, 5s.
 *   DEGRADED_RECOVERY      → Exponential backoff on repeated failures.
 */
public class AebService extends Service {

    private static final String TAG = "AebService";
    private static final String CHANNEL_ID = "AebServiceChannel";

    private HandlerThread engineThread;
    private Engine engine;
    private HudController hud;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    private Notification createNotification() {
        createNotificationChannel();
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, createNotification());
        hud = new HudController(this);
        StateStore.event("service started (v" + BuildConfig.VERSION_NAME + ", mode "
                + Mode.label(new AebSettings(this).mode()) + ")");
        engineThread = new HandlerThread("AebEngine");
        engineThread.start();
        Handler handler = new Handler(engineThread.getLooper());
        engine = new Engine(new AdbVehicleLink(), new AndroidClock(), new HandlerScheduler(handler),
                new AebSettings(this), StateStore::publish, new EngineLogAdapter(TAG));
        StateStore.attach(engine, handler); // "Disable AEB Now" runs on this engine's thread
        // H04: every state transition runs on the single engine thread, including the first one.
        handler.post(engine::start);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        boolean showFloating = new AebSettings(this).showFloating();
        boolean hasOverlayPermission = android.provider.Settings.canDrawOverlays(this);

        // S6: the HUD is part of this service (no second service to start or stop)
        if (showFloating && hasOverlayPermission) {
            hud.show();
        } else {
            hud.hide();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.stop(); // must be first: publishes STOPPED, drops results of a still-running read
            StateStore.detach(engine);
        }
        StateStore.event("service stopped");
        if (engineThread != null) engineThread.quit();
        if (hud != null) hud.hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel( // minSdk 31: no version check needed
                CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
