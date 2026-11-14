package com.byd.aebtoggle.core;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * In-memory event log (refactor S3): a ring buffer of the last {@code capacity} entries.
 * Thread-safe and pure. Nothing is written to a file and nothing leaves the device; the owner
 * reads it in the app and copies it by hand.
 *
 * An entry that repeats the previous one exactly (for example "read: AEB OFF" every 10 s in STEADY)
 * is folded into that entry with a counter and the time of the last repeat, so a quiet hour does not
 * push the interesting lines out of the buffer.
 */
public final class EventLog {

    public static final int DEFAULT_CAPACITY = 300;
    /** Longest text kept per entry (longer text is cut with "..."). */
    public static final int MAX_TEXT = 240;

    private static final class Entry {
        final long firstMs;
        final String text;
        long lastMs;
        int count = 1;

        Entry(long ms, String text) {
            this.firstMs = ms;
            this.lastMs = ms;
            this.text = text;
        }
    }

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<Entry>();

    public EventLog(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
        this.capacity = capacity;
    }

    /** wallMs = wall-clock time of the event (only used for display). */
    public synchronized void add(long wallMs, String text) {
        String t = oneLine(text, MAX_TEXT);
        Entry last = entries.peekLast();
        if (last != null && last.text.equals(t)) {
            last.count++;
            last.lastMs = wallMs;
            return;
        }
        if (entries.size() == capacity) entries.pollFirst();
        entries.addLast(new Entry(wallMs, t));
    }

    /** Number of entries (folded repeats count once). */
    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    /** Oldest first: "HH:mm:ss.SSS  text" or "HH:mm:ss.SSS  text  (x12, last HH:mm:ss.SSS)". */
    public synchronized List<String> lines(TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        f.setTimeZone(zone);
        List<String> out = new ArrayList<String>(entries.size());
        for (Entry e : entries) {
            String line = f.format(new Date(e.firstMs)) + "  " + e.text;
            if (e.count > 1) line += "  (x" + e.count + ", last " + f.format(new Date(e.lastMs)) + ")";
            out.add(line);
        }
        return out;
    }

    /** All lines joined with "\n" ("" when empty). */
    public String dump(TimeZone zone) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines(zone)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Any text as one short line: null = "(none)", runs of whitespace (including newlines) become one
     * space, the ends are trimmed, and text longer than max is cut to max characters ending in "...".
     */
    public static String oneLine(String text, int max) {
        if (text == null) return "(none)";
        String s = text.replaceAll("\\s+", " ").trim();
        if (max >= 3 && s.length() > max) s = s.substring(0, max - 3) + "...";
        return s;
    }
}
