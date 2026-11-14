package com.byd.aebtoggle;

import com.byd.aebtoggle.core.Aeb;
import com.byd.aebtoggle.core.AdbShell;
import com.byd.aebtoggle.core.EventLog;
import com.byd.aebtoggle.core.LinkStatus;
import com.byd.aebtoggle.core.ParcelParser;
import com.byd.aebtoggle.core.ProvisioningReport;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdbHelper {

    public enum AdbStatus { SUCCESS, AUTH_FAIL, TIMEOUT, CLOSED, ERROR }
    public static class AdbResponse {
        public AdbStatus status;
        public String output;
        public AdbResponse(AdbStatus s, String o) { this.status = s; this.output = o; }
    }

    public static boolean grantSelfPermissions() {
        return grantSelfPermissions(new ProvisioningReport());
    }

    /** S6: same commands, order and fast-fail rules as v18.22; each result is also put into report. */
    public static boolean grantSelfPermissions(ProvisioningReport report) {
        String pkg = BuildConfig.APPLICATION_ID;
        String[] cmds = {
            // Core permissions
            "pm grant " + pkg + " android.permission.SYSTEM_ALERT_WINDOW",
            "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow",
            "appops set " + pkg + " android:system_alert_window allow",
            // Background keep-alive — prevents "Close All" from killing the service
            "appops set " + pkg + " RUN_IN_BACKGROUND allow",
            "appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow",
            "appops set " + pkg + " START_FOREGROUND allow",
            "appops set " + pkg + " REQUEST_IGNORE_BATTERY_OPTIMIZATIONS allow",
            // Whitelist in Doze + standby bucket
            "cmd deviceidle whitelist +" + pkg,
            "dumpsys deviceidle whitelist +" + pkg,
            "am set-standby-bucket " + pkg + " active"
        };
        boolean adbAuthorized = false;
        for (int i = 0; i < cmds.length; i++) {
            AdbResponse res = executeRemoteShellWithResult(cmds[i]);
            report.record(ProvisioningReport.forCommandIndex(i), toLink(res.status));
            if (res.status == AdbStatus.SUCCESS) {
                adbAuthorized = true;
            } else if (res.status == AdbStatus.AUTH_FAIL) {
                return false; // Fast fail if we are definitely unauthorized
            } else if (i == 0 && (res.status == AdbStatus.TIMEOUT || res.status == AdbStatus.ERROR)) {
                return false; // Fast fail: port 5555 unreachable, no point trying 12 more commands
            }
        }

        if (!adbAuthorized) {
            return false;
        }

        // H07: enable our accessibility service WITHOUT clobbering the other enabled services.
        // Rule: only write when the current list was read completely and looks well-formed.
        try {
            AdbResponse currentAccRes = executeRemoteShellWithResult("settings get secure enabled_accessibility_services");
            if (currentAccRes == null || currentAccRes.status != AdbStatus.SUCCESS || currentAccRes.output == null) {
                report.record(ProvisioningReport.Capability.ACCESSIBILITY,
                        currentAccRes == null ? LinkStatus.ERROR : toLink(currentAccRes.status));
                android.util.Log.w("AEB_Toggle", "Accessibility list read failed ("
                        + (currentAccRes == null ? "null" : currentAccRes.status.name())
                        + ") - leaving accessibility settings untouched");
            } else {
                String currentAcc = currentAccRes.output.trim();
                String ourService = pkg + "/" + AebAccessibilityService.class.getName();
                if (!isSafeAccessibilityList(currentAcc)) {
                    report.leftUntouched(ProvisioningReport.Capability.ACCESSIBILITY, "unexpected list format, left untouched");
                    android.util.Log.w("AEB_Toggle", "Unexpected accessibility list format - leaving accessibility settings untouched");
                } else {
                    boolean listIsEmpty = currentAcc.isEmpty() || currentAcc.equals("null");
                    boolean alreadyEnabled = false;
                    if (!listIsEmpty) {
                        for (String entry : currentAcc.split(":")) {
                            if (entry.equals(ourService)) { alreadyEnabled = true; break; }
                        }
                    }
                    if (!alreadyEnabled) {
                        String newAcc = listIsEmpty ? ourService : currentAcc + ":" + ourService;
                        // Single quotes: the value was validated above and must reach "settings" as ONE argument.
                        AdbResponse w = executeRemoteShellWithResult("settings put secure enabled_accessibility_services '" + newAcc + "'");
                        report.record(ProvisioningReport.Capability.ACCESSIBILITY, toLink(w.status));
                    }
                    // Ensure the master accessibility toggle is ON
                    AdbResponse m = executeRemoteShellWithResult("settings put secure accessibility_enabled 1");
                    report.record(ProvisioningReport.Capability.ACCESSIBILITY, toLink(m.status));
                }
            }
        } catch (Exception e) {
            report.record(ProvisioningReport.Capability.ACCESSIBILITY, LinkStatus.ERROR);
            e.printStackTrace();
        }

        return adbAuthorized;
    }

    private static LinkStatus toLink(AdbStatus s) {
        return s == null ? LinkStatus.ERROR : LinkStatus.valueOf(s.name());
    }

    /** True for an empty list, the literal "null", or a colon separated list of component names. */
    private static boolean isSafeAccessibilityList(String s) {
        return s != null && s.matches("[A-Za-z0-9_.$/:\\-]*");
    }

    public static AdbResponse getAebState() {
        String cmd = "service call autoservice 5 i32 1038 i32 852492332";
        AdbResponse response = executeRemoteShellWithResult(cmd);
        lastReadOutput = response.output; // S4: raw reply, for the event log (AdbVehicleLink)
        if (response.status == AdbStatus.SUCCESS && response.output != null) {
            Aeb parsed = ParcelParser.parseRead(response.output);
            if (parsed == Aeb.ON)  return new AdbResponse(AdbStatus.SUCCESS, "00000001");
            if (parsed == Aeb.OFF) return new AdbResponse(AdbStatus.SUCCESS, "00000000");
        }
        return response;
    }

    // === v18.4: Typed verification for 9-state engine ===

    public enum VerificationResult {
        DISABLED_CONFIRMED,   // AEB is OFF
        ENABLED_DETECTED,     // AEB is ON
        TRANSPORT_FAILURE     // ADB error, timeout, or unparseable response
    }

    public static volatile AdbStatus lastRawStatus = AdbStatus.CLOSED;
    /** S4: raw text of the last read reply (null when the call produced none). */
    public static volatile String lastReadOutput = null;
    /** S4: CNXN banner of the last connection; logged once per process and when it changes. */
    private static volatile String lastBanner = null;

    /**
     * Typed query that wraps getAebState() into a deterministic 3-value result.
     * Used by the Actor loop to make clean state transitions without string parsing.
     */
    public static VerificationResult queryAebStatus() {
        AdbResponse r = getAebState();
        lastRawStatus = r.status;
        if (r.status != AdbStatus.SUCCESS) return VerificationResult.TRANSPORT_FAILURE;
        if ("00000001".equals(r.output)) return VerificationResult.ENABLED_DETECTED;
        if ("00000000".equals(r.output)) return VerificationResult.DISABLED_CONFIRMED;
        return VerificationResult.TRANSPORT_FAILURE;
    }

    public static AdbResponse setAebState(boolean on) {
        int val = on ? 1 : 0;
        String cmd1 = "service call autoservice 6 i32 1038 i32 852492332 i32 " + val;
        String cmd2 = "service call autoservice 6 i32 1038 i32 944767020 i32 " + val;
        
        AdbResponse res1 = executeRemoteShellWithResult(cmd1);
        if (res1.status != AdbStatus.SUCCESS) {
            return res1;
        }
        AdbResponse res2 = executeRemoteShellWithResult(cmd2);
        // S4: the second command's reply is logged too (never checked; see REFACTOR_PLAN S4)
        StateStore.event("write cmd 2: " + res2.status + "; reply: " + EventLog.oneLine(res2.output, 120));
        if (res2.status != AdbStatus.SUCCESS) {
            return new AdbResponse(AdbStatus.ERROR, "First write OK, second write failed: " + res2.status);
        }
        return res1;
    }

    // =========================================================================
    // S4: transport. The protocol itself is core AdbShell (tested without a car); this part owns
    // the socket and the overall deadline of one call.
    // =========================================================================

    /** Longest one call may take (connect + handshake + shell). Offering the key adds its 15 s wait. */
    private static final long CALL_DEADLINE_MS = 8000;

    /** Closes the socket of a call that runs past its deadline (a blocked read then fails at once). */
    private static final ScheduledThreadPoolExecutor WATCHDOG = newWatchdog();

    private static ScheduledThreadPoolExecutor newWatchdog() {
        ScheduledThreadPoolExecutor w = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "AdbWatchdog");
            t.setDaemon(true);
            return t;
        });
        w.setRemoveOnCancelPolicy(true);
        return w;
    }

    private static final AdbShell.Auth AUTH = new AdbShell.Auth() {
        @Override
        public byte[] sign(byte[] token) throws Exception {
            return AdbKeyStore.get().sign(token);
        }

        @Override
        public byte[] publicKeyPayload() {
            return AdbKeyStore.get().getPublicKeyPayload();
        }
    };

    public static AdbResponse executeRemoteShellWithResult(String command) {
        final Socket socket = new Socket();
        final AtomicBoolean expired = new AtomicBoolean(false);
        final Runnable expire = () -> {
            expired.set(true);
            try { socket.close(); } catch (Exception ignored) {}
        };
        final Object lock = new Object();
        @SuppressWarnings("unchecked")
        final ScheduledFuture<?>[] deadline = new ScheduledFuture<?>[]{
                WATCHDOG.schedule(expire, CALL_DEADLINE_MS, TimeUnit.MILLISECONDS)};
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 2000);
            AdbShell.Result r = AdbShell.run(new AdbShell.Channel() {
                @Override
                public InputStream in() throws java.io.IOException {
                    return socket.getInputStream();
                }

                @Override
                public OutputStream out() throws java.io.IOException {
                    return socket.getOutputStream();
                }

                @Override
                public void setReadTimeoutMs(int ms) throws java.io.IOException {
                    socket.setSoTimeout(ms);
                }

                @Override
                public void extendDeadlineMs(int ms) {
                    synchronized (lock) {
                        if (deadline[0].cancel(false)) {
                            deadline[0] = WATCHDOG.schedule(expire, ms + CALL_DEADLINE_MS, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }, command, AUTH);
            noteBanner(r.banner);
            LinkStatus status = (expired.get() && r.status != LinkStatus.SUCCESS) ? LinkStatus.TIMEOUT : r.status;
            return new AdbResponse(AdbStatus.valueOf(status.name()), r.output);
        } catch (java.net.SocketTimeoutException e) {
            return new AdbResponse(AdbStatus.TIMEOUT, null);
        } catch (Exception e) {
            return expired.get() ? new AdbResponse(AdbStatus.TIMEOUT, null) : new AdbResponse(AdbStatus.ERROR, e.getMessage());
        } finally {
            synchronized (lock) {
                deadline[0].cancel(false);
            }
            try { socket.close(); } catch (Exception e) {}
        }
    }

    /** S4: the car's CNXN banner (model, features) goes to the event log once; it is not checked. */
    private static void noteBanner(String banner) {
        if (banner == null || banner.equals(lastBanner)) return;
        lastBanner = banner;
        StateStore.event("adb connected; shell_v2 " + (AdbShell.hasShellV2(banner) ? "yes" : "no")
                + "; banner: " + EventLog.oneLine(banner, 200));
    }
}
