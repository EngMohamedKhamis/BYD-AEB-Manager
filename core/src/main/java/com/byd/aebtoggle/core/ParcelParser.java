package com.byd.aebtoggle.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict parser for the text that "service call autoservice ..." prints.
 *
 * Accepted shape (one line):   Result: Parcel(00000000 00000001   '........')
 *   - exactly ONE line containing "Result: Parcel("
 *   - the words before the quoted text dump are 8 hex digits each
 *   - whatever follows the words must be the quoted dump (starts with a single quote)
 * The text dump may itself contain parentheses; the closing parenthesis is the LAST one on the line.
 *
 * Differences from the old AdbHelper.getAebState() parser (all for malformed or ambiguous text only):
 * a reply with more than two words, two Result lines, or text after the words that is not the quoted
 * dump is UNKNOWN here; the old code accepted the first two words regardless. A bare "00000000" is
 * never a reply. Multi-line parcels (more than 16 bytes) are not supported, as before.
 */
public final class ParcelParser {
    private static final String MARK = "Result: Parcel(";

    private ParcelParser() {
    }

    /** The AEB property reply is exactly [status = 0][value 0 or 1]. Anything else is UNKNOWN. */
    public static Aeb parseRead(String output) {
        long[] w = words(output);
        if (w == null || w.length != 2 || w[0] != 0L) return Aeb.UNKNOWN;
        if (w[1] == 1L) return Aeb.ON;
        if (w[1] == 0L) return Aeb.OFF;
        return Aeb.UNKNOWN;
    }

    /** True when the reply is one well-formed parcel whose first word (the exception code) is 0. */
    public static boolean isStatusOk(String output) {
        long[] w = words(output);
        return w != null && w.length >= 1 && w[0] == 0L;
    }

    /** The leading hex words of the single Result line, or null when the text is not such a line. */
    static long[] words(String output) {
        if (output == null) return null;
        String found = null;
        for (String raw : output.split("\\r\\n|\\n|\\r")) {
            String line = raw.trim();
            if (line.contains(MARK)) {
                if (found != null) return null; // two Result lines: ambiguous
                found = line;
            }
        }
        if (found == null) return null;

        int open = found.indexOf('(');
        int close = found.lastIndexOf(')');
        if (open < 0 || close <= open) return null; // for example a multi-line dump

        String inner = found.substring(open + 1, close).trim();
        if (inner.isEmpty()) return null;
        String[] parts = inner.split("\\s+");

        List<Long> words = new ArrayList<Long>();
        int i = 0;
        while (i < parts.length && parts[i].matches("[0-9A-Fa-f]{8}")) {
            words.add(Long.valueOf(Long.parseLong(parts[i], 16)));
            i++;
        }
        if (i < parts.length && !parts[i].startsWith("'")) return null; // junk after the words
        if (words.isEmpty()) return null;

        long[] out = new long[words.size()];
        for (int k = 0; k < out.length; k++) out[k] = words.get(k).longValue();
        return out;
    }
}
