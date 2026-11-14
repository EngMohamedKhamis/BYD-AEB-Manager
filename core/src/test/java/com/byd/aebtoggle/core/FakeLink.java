package com.byd.aebtoggle.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** A simulated car: AEB is on or off; a disable write turns it off unless writesTakeEffect is false. */
final class FakeLink implements VehicleLink {
    private final FakeClock clock;
    boolean aebOn = true;
    boolean writesTakeEffect = true;
    /** Scripted replies, consumed before the simulated car answers. */
    final ArrayDeque<ReadResult> scriptedReads = new ArrayDeque<ReadResult>();
    final ArrayDeque<WriteResult> scriptedWrites = new ArrayDeque<WriteResult>();
    long readMs = 0;
    long writeMs = 0;
    int readThrowsLeft = 0;
    int writeThrowsLeft = 0;
    /** Runs at the start of read(): lets a test act "while the worker is blocked in I/O". */
    Runnable duringRead = null;
    final List<Long> readTimes = new ArrayList<Long>();
    final List<Long> writeTimes = new ArrayList<Long>();

    FakeLink(FakeClock clock) {
        this.clock = clock;
    }

    @Override
    public ReadResult read() {
        readTimes.add(Long.valueOf(clock.now));
        if (duringRead != null) duringRead.run();
        clock.now += readMs;
        if (readThrowsLeft > 0) {
            readThrowsLeft--;
            throw new RuntimeException("simulated read fault");
        }
        if (!scriptedReads.isEmpty()) return scriptedReads.poll();
        return aebOn ? ReadResult.on() : ReadResult.off();
    }

    @Override
    public WriteResult writeDisable() {
        writeTimes.add(Long.valueOf(clock.now));
        clock.now += writeMs;
        if (writeThrowsLeft > 0) {
            writeThrowsLeft--;
            throw new RuntimeException("simulated write fault");
        }
        WriteResult w = scriptedWrites.isEmpty() ? WriteResult.of(LinkStatus.SUCCESS) : scriptedWrites.poll();
        if (w.isSuccess() && writesTakeEffect) aebOn = false;
        return w;
    }
}
