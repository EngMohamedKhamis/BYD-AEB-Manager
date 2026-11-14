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
        // boot: the decision sees mode 1, the write task sees mode 0 -> cancelled
        Rig r = new Rig(Mode.ONCE_ON_START);
        r.settings.script.add(Integer.valueOf(Mode.ONCE_ON_START));
        r.settings.script.add(Integer.valueOf(Mode.NO_ACTION));
        r.start().run(30000);
        T.eq("no write", L(), r.link.writeTimes);
        T.isTrue("logged as cancelled", r.log.contains("write cancelled"));
        T.eq("STEADY", Engine.State.STEADY, r.engine.state());
        // current behaviour (H05): the once-marker was consumed before dispatch, so it stays consumed
        r.link.scriptedReads.add(ReadResult.failed(LinkStatus.TIMEOUT));
        r.link.aebOn = true;
        r.run(80000);
        T.eq("a later reconnect in mode 1 does not write (marker consumed)", L(), r.link.writeTimes);

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
}
