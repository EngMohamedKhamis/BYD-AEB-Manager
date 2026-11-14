package com.byd.aebtoggle.core;

import java.util.List;
import java.util.TimeZone;

final class EventLogTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private EventLogTest() {
    }

    static void run() throws Exception {
        T.section("EventLog: basics");
        EventLog log = new EventLog(300);
        T.eq("empty dump", "", log.dump(UTC));
        log.add(0, "service started");
        log.add(3_723_456, "-> STATE: CONNECTING");
        List<String> l = log.lines(UTC);
        T.eq("two entries", Integer.valueOf(2), Integer.valueOf(l.size()));
        T.eq("format of the first line (UTC)", "00:00:00.000  service started", l.get(0));
        T.eq("format of the second line (UTC)", "01:02:03.456  -> STATE: CONNECTING", l.get(1));
        T.eq("dump joins with newline", "00:00:00.000  service started\n01:02:03.456  -> STATE: CONNECTING", log.dump(UTC));
        T.throwsA("capacity 0 is refused", IllegalArgumentException.class, () -> new EventLog(0));

        T.section("EventLog: repeats are folded");
        EventLog r = new EventLog(10);
        r.add(1000, "read: AEB OFF");
        r.add(11_000, "read: AEB OFF");
        r.add(21_000, "read: AEB OFF");
        T.eq("three identical reads = one entry", Integer.valueOf(1), Integer.valueOf(r.size()));
        T.eq("folded line shows count and last time", "00:00:01.000  read: AEB OFF  (x3, last 00:00:21.000)", r.lines(UTC).get(0));
        r.add(31_000, "read: AEB ON");
        r.add(41_000, "read: AEB OFF");
        T.eq("a different line in between starts new entries", Integer.valueOf(3), Integer.valueOf(r.size()));
        T.eq("non-adjacent repeat is not folded", "00:00:41.000  read: AEB OFF", r.lines(UTC).get(2));
        r.add(42_000, "read:   AEB\nOFF ");
        T.eq("whitespace-normalised text folds too", "00:00:41.000  read: AEB OFF  (x2, last 00:00:42.000)", r.lines(UTC).get(2));

        T.section("EventLog: ring buffer");
        EventLog ring = new EventLog(300);
        for (int i = 1; i <= 305; i++) ring.add(i, "event " + i);
        T.eq("capacity is never exceeded", Integer.valueOf(300), Integer.valueOf(ring.size()));
        List<String> rl = ring.lines(UTC);
        T.isTrue("oldest 5 dropped: first is event 6", rl.get(0).endsWith("  event 6"));
        T.isTrue("newest kept: last is event 305", rl.get(299).endsWith("  event 305"));
        ring.clear();
        T.eq("clear empties it", Integer.valueOf(0), Integer.valueOf(ring.size()));
        EventLog one = new EventLog(1);
        one.add(1, "a");
        one.add(2, "a");
        one.add(3, "b");
        T.eq("capacity 1 keeps only the newest", "00:00:00.003  b", one.dump(UTC));

        T.section("EventLog: oneLine");
        T.eq("null", "(none)", EventLog.oneLine(null, 120));
        T.eq("newlines and runs of spaces collapse", "Result: Parcel(00000000 '....')",
                EventLog.oneLine("  Result: Parcel(00000000\n   '....')\n", 120));
        T.eq("exactly max is kept", "abcde", EventLog.oneLine("abcde", 5));
        T.eq("longer than max is cut with ...", "ab...", EventLog.oneLine("abcdef", 5));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) big.append('x');
        T.eq("add() caps each entry at MAX_TEXT (plus the 14-character time prefix)",
                Integer.valueOf(EventLog.MAX_TEXT + 14), Integer.valueOf(capLength(big.toString())));

        T.section("EventLog: thread safety");
        final EventLog shared = new EventLog(5000);
        Thread[] threads = new Thread[4];
        for (int t = 0; t < threads.length; t++) {
            final int id = t;
            threads[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000; i++) shared.add(i, "t" + id + " #" + i);
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        T.eq("4 threads x 1000 distinct lines are all kept", Integer.valueOf(4000), Integer.valueOf(shared.size()));
    }

    /** Length of the single line produced for an over-long text ("HH:mm:ss.SSS  " is 14 characters). */
    private static int capLength(String text) {
        EventLog log = new EventLog(2);
        log.add(0, text);
        return log.lines(UTC).get(0).length();
    }
}
