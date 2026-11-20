package com.byd.aebtoggle.core;

import static com.byd.aebtoggle.core.T.L;

import java.util.Arrays;
import java.util.List;

/**
 * Pins the behaviour of AebService v18.22 (decision D2). Every expected time below was derived by
 * hand from the old code: the virtual clock starts at 0 and reads take 0 ms unless a test says so.
 */
final class EngineTest {
    private EngineTest() {
    }

    static void run() {
        T.section("Engine: boot sequence, mode 1 (Once on Start), AEB ON");
        bootHappyPathMode1();

        T.section("Engine: stale timers are ignored even if the scheduler cannot cancel");
        staleTimersIgnored();

        T.section("Engine: mode 2 (Auto Disable) re-disables after two ON reads");
        mode2RecoveryAfterReenable();

        T.section("Engine: mode 1 fires once per lifetime, mode 2 fires again after a reconnect");
        onceIsConsumedAcrossReconnect();

        T.section("Engine: mode 1 persists consumed across engine/service recreation (C3)");
        oncePersistsAcrossEngineRecreation();

        T.section("Engine: mode 0 and AEB already OFF never write");
        noWriteCases();

        T.section("Engine: CONNECTING keeps trying and reports the transport status");
        connectingRetries();

        T.section("Engine: AUTH_FAIL triggers exponential backoff (W1)");
        authFailBackoff();

        T.section("Engine: a failed read during DISCOVERING restarts the streak");
        discoveringFailureResetsStreak();

        T.section("Engine: verification failure retries 3 times, then DEGRADED with doubling backoff");
        verifyFailureDegrades();

        T.section("Engine: three write transport failures in a row degrade; two do not");
        writeFailures();

        T.section("Engine: the mode is re-read right before every write");
        modeChangedBeforeWrite();

        T.section("Engine: safety nets pause 4/8/16/32/60 s and reset after a good read");
        safetyNet();

        T.section("Engine: stop() ends everything and suppresses late results");
        stopBehaviour();

        T.section("Engine: healthy STEADY reads reset the DEGRADED backoff (M01)");
        steadyResetsBackoff();

        T.section("Engine: STEADY debounce belongs to the current mode");
        debounceFollowsMode();

        T.section("Engine: ticks are spaced from the END of the previous run (fixed delay)");
        fixedDelayAfterSlowReads();

        T.section("Engine: manual Disable Now runs on the engine and changes no state");
        manualDisable();

        T.section("Engine: snapshots");
        snapshots();

        T.section("Engine: a failing sink does not stop the engine");
        failingSink();

        T.section("Engine: onIgnitionCycle resets consumed flag and all error/backoff counters");
        ignitionCycleResetsConsumedAndCounters();

        T.section("Engine: duplicate boot signals within boot session are ignored");
        duplicateBootSignalsIgnored();

        T.section("Engine: onPermissionGranted wakes 5-minute AUTH_FAIL backoff without disturbing healthy states");
        permissionGrantedWakesBackoff();
    }

    // ------------------------------------------------------------------------------------------

    private static void bootHappyPathMode1() {
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(36000);
        // CONNECTING read at 0; DISCOVERING reads at 3000 and 6000 (3rd read, 6 s after the first -> decision, write at 6000);
        // verification reads 1 s, 3 s and 6 s after each previous read (7000, 10000, 16000); SETTLING 10 s; STEADY from 26000 every 10 s.
        T.eq("read times", L(0, 3000, 6000, 7000, 10000, 16000, 26000, 36000), r.link.readTimes);
        T.eq("write times", L(6000), r.link.writeTimes);
        T.eq("phases", Arrays.asList("CONNECTING", "DISCOVERING", "ENFORCING_BOOT_EARLY", "VERIFY_BOOT (1000ms)",
                "VERIFY_BOOT (3000ms)", "VERIFY_BOOT (6000ms)", "SETTLING", "STEADY"), r.sink.phaseSequence());
        T.eq("state", Engine.State.STEADY, r.engine.state());
        Snapshot s = r.engine.snapshot();
        T.eq("aeb", Aeb.OFF, s.aeb);
        T.eq("link", LinkStatus.SUCCESS, s.link);
        T.eq("phase", "STEADY", s.phase);
        T.eq("observedAt", 36000L, s.observedAtMs);
    }

    private static void staleTimersIgnored() {
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.sched.ignoreCancel = true;
        r.start().run(36000);
        T.eq("read times are the same as with a working cancelAll", L(0, 3000, 6000, 7000, 10000, 16000, 26000, 36000), r.link.readTimes);
        T.eq("write times", L(6000), r.link.writeTimes);
        T.eq("state", Engine.State.STEADY, r.engine.state());
    }

    private static void mode2RecoveryAfterReenable() {
        Rig r = new Rig(Mode.AUTO_DISABLE).start();
        r.run(40000);
        T.eq("boot write", L(6000), r.link.writeTimes);
        r.link.aebOn = true; // the driver (or the car) turns AEB back on
        r.run(46000);
        T.eq("first ON read is only counted (still one write)", L(6000), r.link.writeTimes);
        T.eq("still STEADY", Engine.State.STEADY, r.engine.state());
        r.run(56000);
        T.eq("second ON read triggers the runtime write", L(6000, 56000), r.link.writeTimes);
        T.eq("phase", "VERIFY_RUNTIME (2000ms)", r.engine.snapshot().phase);
        r.run(58000);
        T.eq("phase after the first runtime check", "VERIFY_RUNTIME (5000ms)", r.engine.snapshot().phase);
        r.run(63000);
        T.eq("back to STEADY", Engine.State.STEADY, r.engine.state());
        T.eq("reads include both verification reads and the immediate STEADY read",
                L(0, 3000, 6000, 7000, 10000, 16000, 26000, 36000, 46000, 56000, 58000, 63000, 63000), r.link.readTimes);
    }

    private static void onceIsConsumedAcrossReconnect() {
        // Mode 1: after the boot write, a link failure and re-discovery must NOT write again.
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(40000);
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT)); // the STEADY read at 46000 fails
        r.link.aebOn = true; // and AEB is ON again when the link returns
        r.run(80000);
        T.isTrue("the failure was published (UNKNOWN + TIMEOUT)", r.sink.saw(Aeb.UNKNOWN, LinkStatus.TIMEOUT));
        T.eq("no second write in mode 1", L(6000), r.link.writeTimes);
        T.eq("back in STEADY", Engine.State.STEADY, r.engine.state());

        // Mode 2: the same story writes again once re-discovery has a stable ON reading (52000 = 46000 + 6 s).
        Rig r2 = new Rig(Mode.AUTO_DISABLE).start();
        r2.run(40000);
        r2.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r2.link.aebOn = true;
        r2.run(52000);
        T.eq("mode 2 writes again after re-discovery", L(6000, 52000), r2.link.writeTimes);
    }

    private static void oncePersistsAcrossEngineRecreation() {
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(40000);
        T.eq("initial boot write", L(6000), r.link.writeTimes);
        T.isTrue("boot enforce marked consumed in settings", r.settings.isBootEnforceConsumed());

        // Simulate process death / service recreation (START_STICKY) during the same boot session:
        // A new Engine is instantiated with the same persistent settings and link
        r.engine.stop();
        r.link.aebOn = true; // The driver turned AEB back ON mid-drive

        Engine restartedEngine = new Engine(r.link, r.clock, r.sched, r.settings, r.sink, r.log);
        restartedEngine.start();
        r.run(80000);

        T.eq("no second write after service recreation mid-drive", L(6000), r.link.writeTimes);
        T.eq("restarted engine reached STEADY without re-disabling", Engine.State.STEADY, restartedEngine.state());
    }

    private static void noWriteCases() {
        Rig r = new Rig(Mode.NO_ACTION).start();
        r.run(60000);
        T.eq("mode 0, AEB ON: no write", L(), r.link.writeTimes);
        T.eq("mode 0 reaches STEADY", Engine.State.STEADY, r.engine.state());
        T.eq("the AEB reading is still published", Aeb.ON, r.engine.snapshot().aeb);
        r.settings.mode = Mode.ONCE_ON_START; // switching to Once later does not fire in STEADY
        r.run(100000);
        T.eq("mode 1 chosen while STEADY: still no write", L(), r.link.writeTimes);

        Rig off = new Rig(Mode.ONCE_ON_START);
        off.link.aebOn = false;
        off.start().run(26000);
        T.eq("AEB already OFF: no write", L(), off.link.writeTimes);
        T.eq("read times (the 3rd DISCOVERING read and the first STEADY read are both at 6000)",
                L(0, 3000, 6000, 6000, 16000, 26000), off.link.readTimes);
        T.eq("STEADY", Engine.State.STEADY, off.engine.state());
    }

    private static void connectingRetries() {
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.start().run(0);
        T.eq("phase", "CONNECTING", r.engine.snapshot().phase);
        T.eq("aeb unknown", Aeb.UNKNOWN, r.engine.snapshot().aeb);
        T.eq("link TIMEOUT", LinkStatus.TIMEOUT, r.engine.snapshot().link);
        r.run(6000);
        T.eq("link TIMEOUT at 6000", LinkStatus.TIMEOUT, r.engine.snapshot().link);
        T.eq("state", Engine.State.CONNECTING, r.engine.state());
        r.run(9000);
        T.eq("first good read at 9000 starts discovery", Engine.State.DISCOVERING, r.engine.state());
        T.eq("read times", L(0, 3000, 6000, 9000), r.link.readTimes);
        T.eq("link SUCCESS", LinkStatus.SUCCESS, r.engine.snapshot().link);
        T.eq("no write yet", L(), r.link.writeTimes);

        // a reply that arrived but could not be understood: UNKNOWN with transport SUCCESS ("Waiting for vehicle...")
        Rig q = new Rig(Mode.NO_ACTION);
        q.link.scriptedReads.add(ReadResult.failed(LinkStatus.SUCCESS));
        q.start().run(0);
        T.eq("unparseable reply keeps CONNECTING", Engine.State.CONNECTING, q.engine.state());
        T.eq("link stays SUCCESS", LinkStatus.SUCCESS, q.engine.snapshot().link);
        T.eq("aeb unknown", Aeb.UNKNOWN, q.engine.snapshot().aeb);
    }

    private static void authFailBackoff() {
        Rig r = new Rig(Mode.ONCE_ON_START);
        // Script 4 consecutive AUTH_FAIL reads, followed by a good read
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // read 1 at 0 -> next in 3s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // read 2 at 3000 -> next in 15s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // read 3 at 18000 -> next in 60s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // read 4 at 78000 -> next in 300s
        // read 5 at 378000 is a normal good read (aeb ON)
        r.start().run(0);
        T.eq("1st failure at 0", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        r.run(3000);
        T.eq("2nd failure at 3000 (+3s)", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        r.run(18000);
        T.eq("3rd failure at 18000 (+15s)", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        r.run(78000);
        T.eq("4th failure at 78000 (+60s)", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        r.run(378000);
        T.eq("5th read at 378000 (+300s) starts discovery", Engine.State.DISCOVERING, r.engine.state());
        T.eq("auth backoff read times", L(0, 3000, 18000, 78000, 378000), r.link.readTimes);
    }

    private static void discoveringFailureResetsStreak() {
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.link.scriptedReads.add(ReadResult.on());
        r.link.scriptedReads.add(ReadResult.on());
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT)); // the 3rd read, at 6000, fails
        r.start().run(12000);
        // CONNECTING again at 6000 (immediately), new streak starts there: 6000, 9000, 12000 -> decision and write at 12000.
        T.eq("read times", L(0, 3000, 6000, 6000, 9000, 12000), r.link.readTimes);
        T.eq("write at 12000, not at 6000", L(12000), r.link.writeTimes);
    }

    private static void verifyFailureDegrades() {
        // Mode 1, the car ignores the command: 3 attempts, 1 s apart, then DEGRADED (30 s), then CONNECTING, then STEADY without a 4th write (mode 1 is consumed).
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.link.writesTakeEffect = false;
        r.start().run(9000);
        T.eq("write times", L(6000, 7000, 8000), r.link.writeTimes);
        T.eq("phase", "DEGRADED (30s)", r.engine.snapshot().phase);
        r.run(39000);
        T.eq("after 30 s the engine reconnects and starts discovery", Engine.State.DISCOVERING, r.engine.state());
        r.run(60000);
        T.eq("mode 1 does not try again", L(6000, 7000, 8000), r.link.writeTimes);
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());

        // Mode 2: the backoff doubles: 30, 60, 120, 240, 300, 300 seconds.
        Rig d = new Rig(Mode.AUTO_DISABLE);
        d.link.writesTakeEffect = false;
        d.start().run(2_000_000);
        List<String> degraded = d.sink.phasesStartingWith("DEGRADED");
        T.eq("first six DEGRADED phases", Arrays.asList("DEGRADED (30s)", "DEGRADED (60s)", "DEGRADED (120s)",
                "DEGRADED (240s)", "DEGRADED (300s)", "DEGRADED (300s)"), degraded.subList(0, 6));
        T.isTrue("each cycle made exactly 3 write attempts", d.link.writeTimes.size() % 3 == 0);
    }

    private static void writeFailures() {
        Rig r = new Rig(Mode.AUTO_DISABLE);
        for (int i = 0; i < 3; i++) r.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        r.start().run(18000);
        T.eq("3 write attempts", L(6000, 12000, 18000), r.link.writeTimes);
        T.eq("then DEGRADED", "DEGRADED (30s)", r.engine.snapshot().phase);
        T.eq("the write status was published", LinkStatus.ERROR, r.engine.snapshot().link);

        Rig s = new Rig(Mode.AUTO_DISABLE);
        s.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        s.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        s.start().run(60000);
        T.eq("2 failures then success", L(6000, 12000, 18000), s.link.writeTimes);
        T.eq("never degraded", L().size(), s.sink.phasesStartingWith("DEGRADED").size());
        T.eq("ends in STEADY", Engine.State.STEADY, s.engine.state());
    }

    private static void modeChangedBeforeWrite() {
        // boot: DISCOVERING sees mode 1, ENFORCING_BOOT_EARLY sees mode 0 -> write cancelled, NOT marked consumed (1b6e5f4)
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.settings.script.add(Integer.valueOf(Mode.ONCE_ON_START));
        r.settings.script.add(Integer.valueOf(Mode.NO_ACTION));
        r.start().run(30000);
        T.eq("no write", L(), r.link.writeTimes);
        T.isTrue("logged as cancelled", r.log.contains("write cancelled"));
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());
        T.isTrue("once-marker not consumed when write was cancelled", !r.settings.isBootEnforceConsumed());

        // If mode stays No Action across a reconnect, still no write
        r.settings.mode = Mode.NO_ACTION;
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.link.aebOn = true;
        r.run(60000);
        T.eq("reconnect in No Action does not write", L(), r.link.writeTimes);

        // If mode is Once on Start on a subsequent reconnect (since Once on Start never fired), it enforces once and consumes
        // (36000 fail -> 42000 STEADY -> ticks 42000, 52000, 62000 fail -> discovery 62000, 65000, 68000 write)
        r.settings.mode = Mode.ONCE_ON_START;
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.run(100000);
        T.eq("reconnect in mode 1 writes once because prior attempt was cancelled", L(68000), r.link.writeTimes);
        T.isTrue("marker consumed after verified boot write", r.settings.isBootEnforceConsumed());

        // runtime: STEADY sees mode 2 (second ON read), the recovery task sees mode 1 -> cancelled
        Rig q = new Rig(Mode.AUTO_DISABLE).start();
        q.run(40000);
        q.link.aebOn = true;
        q.run(46000); // first ON read
        q.settings.script.add(Integer.valueOf(Mode.AUTO_DISABLE));
        q.settings.script.add(Integer.valueOf(Mode.ONCE_ON_START));
        q.run(56000); // second ON read -> RECOVERY_ENFORCE -> mode now 1 -> cancelled
        T.eq("runtime write cancelled", L(6000), q.link.writeTimes);
        T.isTrue("logged as cancelled", q.log.contains("RECOVERY_ENFORCE: mode is now 1 - write cancelled"));
    }

    private static void safetyNet() {
        Rig r = new Rig(Mode.NO_ACTION);
        r.link.readThrowsLeft = 5;
        r.start().run(120000);
        T.eq("pause grows 4, 8, 16, 32, 60 s", L(0, 4000, 12000, 28000, 60000, 120000), r.link.readTimes);
        T.isTrue("logged", r.log.contains("ACTOR SAFETY NET in tick()"));
        T.eq("reconnected", Engine.State.DISCOVERING, r.engine.state());
        r.link.readThrowsLeft = 1; // a new fault after a good read starts again at 4 s
        r.run(127000);
        T.isTrue("next attempt 4 s after the new fault (123000 + 4000)", r.link.readTimes.contains(Long.valueOf(127000)));

        Rig w = new Rig(Mode.ONCE_ON_START);
        w.link.writeThrowsLeft = 1;
        w.start().run(10000);
        T.eq("the write was attempted once", L(6000), w.link.writeTimes);
        T.isTrue("safety net in the write path", w.log.contains("ACTOR SAFETY NET in tickEnforcingBootEarly()"));
        T.isTrue("reconnect read 4 s later", w.link.readTimes.contains(Long.valueOf(10000)));
    }

    private static void stopBehaviour() {
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(3000);
        r.engine.stop();
        int reads = r.link.readTimes.size();
        r.run(200000);
        T.eq("no link call after stop", reads, r.link.readTimes.size());
        T.eq("nothing left scheduled", 0, r.sched.pending());
        Snapshot last = r.sink.last();
        T.eq("phase", "STOPPED", last.phase);
        T.eq("aeb", Aeb.UNKNOWN, last.aeb);
        T.eq("link", LinkStatus.CLOSED, last.link);
        int published = r.sink.all.size();
        r.engine.stop();
        T.eq("stop() twice publishes nothing more", published, r.sink.all.size());
        r.engine.requestDisableNow();
        T.eq("no manual write after stop", L(), r.link.writeTimes);

        // a worker that is still blocked in I/O when stop() arrives must not publish its result
        Rig late = new Rig(Mode.ONCE_ON_START);
        late.link.duringRead = () -> late.engine.stop();
        late.start().run(0);
        T.eq("exactly one STOPPED snapshot", 1, late.sink.count("STOPPED"));
        T.eq("STOPPED is the last snapshot", "STOPPED", late.sink.last().phase);
        T.eq("only the one read happened", L(0), late.link.readTimes);
        T.eq("nothing scheduled", 0, late.sched.pending());
    }

    private static void steadyResetsBackoff() {
        Rig r = new Rig(Mode.AUTO_DISABLE);
        r.link.writesTakeEffect = false;
        r.start().run(9000);
        T.eq("first DEGRADED is 30 s", "DEGRADED (30s)", r.engine.snapshot().phase);
        r.link.writesTakeEffect = true; // the car obeys from now on
        r.run(120000); // reconnect at 39000, write at 45000, verification, SETTLING, STEADY from 65000 (6 healthy reads by 115000)
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());
        T.eq("no second DEGRADED so far", 1, r.sink.phasesStartingWith("DEGRADED").size());
        r.link.aebOn = true;
        r.link.writesTakeEffect = false;
        r.run(145000); // ON reads at 125000 and 135000, three failed attempts, DEGRADED at 141000
        T.eq("DEGRADED phases", Arrays.asList("DEGRADED (30s)", "DEGRADED (30s)"), r.sink.phasesStartingWith("DEGRADED"));
    }

    private static void debounceFollowsMode() {
        Rig r = new Rig(Mode.AUTO_DISABLE).start();
        r.run(40000);
        r.link.aebOn = true;
        r.run(46000); // ON, count 1
        r.settings.mode = Mode.ONCE_ON_START;
        r.run(56000); // ON, counter reset because the mode is not 2
        r.settings.mode = Mode.AUTO_DISABLE;
        r.run(66000); // ON, count 1 again
        T.eq("no write yet", L(6000), r.link.writeTimes);
        r.run(76000); // ON, count 2
        T.eq("write after two ON reads under the same mode", L(6000, 76000), r.link.writeTimes);
    }

    private static void fixedDelayAfterSlowReads() {
        Rig r = new Rig(Mode.NO_ACTION);
        r.link.aebOn = false;
        r.link.readMs = 2000;
        r.start().run(40000);
        T.eq("starts are spaced from the end of the previous read", L(0, 5000, 10000, 12000, 24000, 36000), r.link.readTimes);
    }

    private static void manualDisable() {
        Rig r = new Rig(Mode.NO_ACTION).start();
        r.run(10000);
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());
        r.engine.requestDisableNow();
        T.eq("one manual write", L(10000), r.link.writeTimes);
        T.eq("transport status published", LinkStatus.SUCCESS, r.engine.snapshot().link);
        T.eq("reading is not changed by the write", Aeb.ON, r.engine.snapshot().aeb);
        T.eq("state unchanged", Engine.State.STEADY, r.engine.state());
        r.run(16000);
        T.eq("the next read shows the truth", Aeb.OFF, r.engine.snapshot().aeb);

        r.link.scriptedWrites.add(WriteResult.of(LinkStatus.AUTH_FAIL));
        r.engine.requestDisableNow();
        T.eq("failed write: status published", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        T.eq("state unchanged", Engine.State.STEADY, r.engine.state());

        r.link.writeThrowsLeft = 1;
        r.engine.requestDisableNow();
        T.eq("exception: ERROR published", LinkStatus.ERROR, r.engine.snapshot().link);
        T.eq("state unchanged", Engine.State.STEADY, r.engine.state());
        T.isTrue("logged", r.log.contains("MANUAL: disable now failed"));
    }

    private static void snapshots() {
        T.eq("initial snapshot", new Snapshot(Aeb.UNKNOWN, LinkStatus.CLOSED, "IDLE", -1, 0), Snapshot.initial());

        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(36000);
        boolean increasing = true;
        for (int i = 1; i < r.sink.all.size(); i++) {
            if (r.sink.all.get(i).sequence != r.sink.all.get(i - 1).sequence + 1) increasing = false;
        }
        T.isTrue("sequence grows by exactly one per snapshot", increasing);

        // a failed read keeps the time of the last good read
        Rig f = new Rig(Mode.NO_ACTION).start();
        f.run(10000); // STEADY, last good read at 6000 (the immediate STEADY read)
        long good = f.engine.snapshot().observedAtMs;
        f.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        f.run(16000);
        Snapshot failedOne = null;
        for (Snapshot x : f.sink.all) {
            if (x.aeb == Aeb.UNKNOWN && x.link == LinkStatus.TIMEOUT) {
                failedOne = x;
                break;
            }
        }
        T.isTrue("the failed read was published", failedOne != null);
        if (failedOne != null) T.eq("observedAt survives a failure", good, failedOne.observedAtMs);

        Snapshot s = new Snapshot(Aeb.OFF, LinkStatus.SUCCESS, "STEADY", 1000, 5);
        T.isTrue("fresh at the same instant", s.isFresh(1000, 0));
        T.isTrue("fresh inside the window", s.isFresh(16000, 15000));
        T.isTrue("stale after the window", !s.isFresh(16001, 15000));
        T.isTrue("a clock that went backwards is not fresh", !s.isFresh(999, 15000));
        T.isTrue("never observed is not fresh", !Snapshot.initial().isFresh(5, 15000));
        T.isTrue("Mode.isValid", Mode.isValid(0) && Mode.isValid(1) && Mode.isValid(2) && !Mode.isValid(3) && !Mode.isValid(-1));
        T.eq("ReadResult.failed(null) is ERROR", LinkStatus.ERROR, ReadResult.failed(null).link);
        T.eq("WriteResult.of(null) is ERROR", LinkStatus.ERROR, WriteResult.of(null).link);
    }

    private static void failingSink() {
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.sink.throwOnPublish = true;
        r.start().run(36000);
        T.eq("write times unchanged", L(6000), r.link.writeTimes);
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());
        T.isTrue("logged", r.log.contains("snapshot sink failed"));
    }

    private static void ignitionCycleResetsConsumedAndCounters() {
        // 1. Once-on-Start resets and enforces again on a new vehicle ignition cycle
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(40000);
        T.eq("trip 1 write", L(6000), r.link.writeTimes);
        T.isTrue("trip 1 consumed", r.settings.isBootEnforceConsumed());
        r.link.aebOn = true;
        r.run(60000);
        T.eq("no second write during trip 1", L(6000), r.link.writeTimes);

        r.engine.onIgnitionCycle();
        T.isTrue("consumed cleared on ignition", !r.settings.isBootEnforceConsumed());
        T.eq("restarts at CONNECTING", Engine.State.CONNECTING, r.engine.state());
        r.run(100000);
        T.eq("trip 2 write after fresh discovery", L(6000, 66000), r.link.writeTimes);
        T.isTrue("trip 2 consumed after verification", r.settings.isBootEnforceConsumed());
        T.eq("trip 2 STEADY", Engine.State.STEADY, r.engine.state());

        // 2. degradedBackoffMs resets to 30 s floor on new ignition
        Rig d = new Rig(Mode.AUTO_DISABLE);
        d.link.writesTakeEffect = false;
        d.start().run(60000); // 1st DEGRADED (30s) at 9000..39000, 2nd DEGRADED (60s) at 48000..108000
        T.eq("degraded backoff grew to 60s on trip 1", "DEGRADED (60s)", d.engine.snapshot().phase);
        d.engine.onIgnitionCycle();
        d.run(70000); // discovery 60000..66000, writes 66000/67000/68000, DEGRADED at 69000
        T.eq("trip 2 DEGRADED starts back at 30s floor", "DEGRADED (30s)", d.engine.snapshot().phase);

        // 3. writeFailures resets to 0 on new ignition (2 failures on trip 1 + 2 failures on trip 2 must not degrade)
        Rig w = new Rig(Mode.AUTO_DISABLE);
        w.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        w.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        w.start().run(14000); // failed writes at 6000 and 12000 -> writeFailures == 2
        w.settings.mode = Mode.NO_ACTION;
        w.run(50000); // sit in STEADY without writing so writeFailures stays 2
        w.settings.mode = Mode.AUTO_DISABLE;
        w.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        w.link.scriptedWrites.add(WriteResult.of(LinkStatus.ERROR));
        w.engine.onIgnitionCycle();
        w.run(95000); // failed writes at 56000 and 62000, 3rd write at 68000 succeeds -> STEADY
        T.eq("trip 2 tolerated 2 write failures without degrading", 0, w.sink.phasesStartingWith("DEGRADED").size());
        T.eq("trip 2 succeeded on 3rd attempt", L(6000, 12000, 56000, 62000, 68000), w.link.writeTimes);
        T.eq("trip 2 reached STEADY", Engine.State.STEADY, w.engine.state());

        // 4. faultCount resets to 0 on new ignition (next fault pauses 4 s, not 60 s)
        Rig f = new Rig(Mode.NO_ACTION);
        f.link.readThrowsLeft = 4; // pauses 4s (4000), 8s (12000), 16s (28000), 32s (60000)
        f.start().run(50000);
        f.link.readThrowsLeft = 1;
        f.engine.onIgnitionCycle();
        f.run(55000); // fault at 50000 -> 4s pause -> reconnect read at 54000
        T.isTrue("fault after ignition paused 4s (read at 54000)", f.link.readTimes.contains(Long.valueOf(54000)));

        // 5. authFailCount resets to 0 on new ignition (next AUTH_FAIL waits 3 s, not 300 s)
        Rig a = new Rig(Mode.NO_ACTION);
        a.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 0 -> +3s
        a.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 3000 -> +15s
        a.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 18000 -> +60s (78000)
        a.start().run(50000);
        a.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // at 50000 -> should wait 3s (53000)
        a.engine.onIgnitionCycle();
        a.run(53000);
        T.eq("auth backoff reset to 3s on ignition", Engine.State.DISCOVERING, a.engine.state());
    }

    private static void duplicateBootSignalsIgnored() {
        // 1. Cold boot started via start() (e.g. AccessibilityService), then BootReceiver fires seconds later
        Rig r = new Rig(Mode.ONCE_ON_START).start();
        r.run(4000); // mid-DISCOVERING (reads at 0, 3000)
        r.engine.onIgnitionCycle(); // duplicate signal 4 s into boot
        r.run(18000); // write at 6000, verified OFF by 16000 -> consumed == true
        T.eq("discovery not restarted by 4s boot broadcast", L(6000), r.link.writeTimes);
        T.isTrue("consumed after verify", r.settings.isBootEnforceConsumed());

        r.link.aebOn = true; // driver re-enables AEB at 18 s
        r.engine.onIgnitionCycle(); // late QUICKBOOT_POWERON 18 s into same boot
        T.isTrue("late duplicate broadcast did not clear consumed", r.settings.isBootEnforceConsumed());
        r.run(40000);
        T.eq("still only one boot write", L(6000), r.link.writeTimes);
        T.isTrue("logged duplicate", r.log.contains("duplicate boot signal"));

        // 2. Back-to-back BOOT_COMPLETED + QUICKBOOT_POWERON on a warm ignition cycle
        r.engine.onIgnitionCycle(); // genuine new trip at t = 40000 (>= 30 s)
        T.isTrue("genuine trip cleared consumed", !r.settings.isBootEnforceConsumed());
        r.run(43500);
        r.engine.onIgnitionCycle(); // duplicate QUICKBOOT_POWERON 3.5 s later
        r.run(58000); // write at 46000, verified OFF at 56000 -> consumed == true
        r.link.aebOn = true;
        r.engine.onIgnitionCycle(); // another duplicate 18 s after trip 2 start
        T.isTrue("trip 2 consumed preserved against duplicate", r.settings.isBootEnforceConsumed());
        r.run(75000);
        T.eq("exactly one write per real trip", L(6000, 46000), r.link.writeTimes);

        // 3. Dead-process warm wake: Accessibility recreated service via onBootStart() with consumed=true, then BootReceiver fires 2 s later
        Rig warm = new Rig(Mode.ONCE_ON_START);
        warm.clock.now = 3_600_000;
        warm.settings.setBootEnforceConsumed(true);
        warm.engine.onBootStart();
        warm.run(3_602_000); // onBootStart() saw consumed=true, so no active unconsumed boot session yet
        warm.engine.onIgnitionCycle(); // first real ignition broadcast of new trip at t = 3602000 -> MUST be accepted
        T.isTrue("warm wake ignition accepted despite recent recreation", !warm.settings.isBootEnforceConsumed());
        warm.run(3_605_000);
        warm.engine.onIgnitionCycle(); // second broadcast 3 s later -> duplicate, ignored
        warm.run(3_635_000);
        T.eq("warm wake wrote at 3602000 + 6000 = 3608000", L(3_608_000), warm.link.writeTimes);
        T.isTrue("warm wake marked consumed", warm.settings.isBootEnforceConsumed());

        // 4. Slow cold boot (>30 s uptime) started via onBootStart() (AccessibilityService) still suppresses trailing BootReceiver
        Rig slowBoot = new Rig(Mode.ONCE_ON_START);
        slowBoot.clock.now = 45_000;
        slowBoot.engine.onBootStart();
        slowBoot.run(52_000); // write at 51000, now in VERIFYING_BOOT
        slowBoot.engine.onIgnitionCycle(); // trailing BOOT_COMPLETED 7 s after Accessibility bind -> ignored
        slowBoot.run(75_000);
        T.eq("slow cold boot wrote only once", L(51_000), slowBoot.link.writeTimes);
        T.isTrue("slow cold boot marked consumed", slowBoot.settings.isBootEnforceConsumed());

        // 5. Mid-uptime UI launch (e.g. MainActivity.syncService at 500 s) must NOT suppress a real ignition broadcast 20 s later
        Rig ui = new Rig(Mode.ONCE_ON_START);
        ui.clock.now = 500_000;
        ui.start().run(520_000); // write at 506000, verified OFF by 516000 -> consumed == true
        T.eq("UI launch trip 1 write", L(506000), ui.link.writeTimes);
        T.isTrue("UI launch trip 1 consumed", ui.settings.isBootEnforceConsumed());
        ui.link.aebOn = true;
        ui.engine.onIgnitionCycle(); // real ignition broadcast 20 s after UI launch -> MUST NOT be suppressed
        T.isTrue("real ignition after mid-uptime UI launch cleared consumed", !ui.settings.isBootEnforceConsumed());
        ui.run(550_000);
        T.eq("real ignition enforced on trip 2", L(506000, 526000), ui.link.writeTimes);

        // 6. After stop(), onIgnitionCycle() and onBootStart() are no-ops
        warm.engine.stop();
        warm.engine.onIgnitionCycle();
        warm.engine.onBootStart();
        T.eq("still STOPPED", "STOPPED", warm.sink.last().phase);
        T.eq("nothing scheduled after stop", 0, warm.sched.pending());
    }

    private static void permissionGrantedWakesBackoff() {
        // 1. Wakes 300 s AUTH_FAIL backoff immediately on Grant Permission success
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 0 -> +3s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 3000 -> +15s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 18000 -> +60s
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.AUTH_FAIL)); // 78000 -> +300s (until 378000)
        r.start().run(80000);
        T.eq("stuck in AUTH_FAIL at 80 s", LinkStatus.AUTH_FAIL, r.engine.snapshot().link);
        T.eq("still CONNECTING", Engine.State.CONNECTING, r.engine.state());

        r.engine.onPermissionGranted();
        r.run(80000); // immediate 0 ms tick
        T.eq("woke immediately at 80 s into DISCOVERING", Engine.State.DISCOVERING, r.engine.state());
        T.eq("link is now SUCCESS without waiting 300 s", LinkStatus.SUCCESS, r.engine.snapshot().link);
        r.run(115000);
        T.eq("completed boot disable at 86000", L(86000), r.link.writeTimes);
        T.eq("reached STEADY", Engine.State.STEADY, r.engine.state());

        // 2. Re-running Grant Permission while already healthy in STEADY does not disrupt STEADY
        int readsBefore = r.link.readTimes.size();
        r.engine.onPermissionGranted();
        T.eq("stayed STEADY", Engine.State.STEADY, r.engine.state());
        T.eq("no immediate extra read in STEADY", readsBefore, r.link.readTimes.size());

        // 3. Wakes DEGRADED_RECOVERY backoff too
        Rig deg = new Rig(Mode.AUTO_DISABLE);
        for (int i = 0; i < 3; i++) deg.link.scriptedWrites.add(WriteResult.of(LinkStatus.AUTH_FAIL));
        deg.start().run(18000);
        T.eq("in DEGRADED_RECOVERY", Engine.State.DEGRADED_RECOVERY, deg.engine.state());
        deg.engine.onPermissionGranted();
        deg.run(18000);
        T.eq("woke from DEGRADED_RECOVERY into DISCOVERING", Engine.State.DISCOVERING, deg.engine.state());

        // 4. After stop(), onPermissionGranted() is a no-op
        deg.engine.stop();
        deg.engine.onPermissionGranted();
        T.eq("still STOPPED", "STOPPED", deg.sink.last().phase);
        T.eq("nothing scheduled after stop", 0, deg.sched.pending());
    }
}
