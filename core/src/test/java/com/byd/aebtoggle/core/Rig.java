package com.byd.aebtoggle.core;

/** Everything wired together: engine + fakes + virtual time. */
final class Rig {
    final FakeClock clock = new FakeClock();
    final FakeScheduler sched = new FakeScheduler(clock);
    final FakeLink link = new FakeLink(clock);
    final ScriptedSettings settings = new ScriptedSettings();
    final RecordingSink sink = new RecordingSink();
    final RecordingLog log = new RecordingLog();
    final Engine engine;

    Rig(int mode) {
        settings.mode = mode;
        engine = new Engine(link, clock, sched, settings, sink, log);
    }

    Rig start() {
        engine.start();
        return this;
    }

    Rig run(long untilMs) {
        sched.runUntil(untilMs);
        return this;
    }
}
