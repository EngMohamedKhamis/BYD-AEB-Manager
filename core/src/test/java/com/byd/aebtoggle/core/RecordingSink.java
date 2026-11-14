package com.byd.aebtoggle.core;

import java.util.ArrayList;
import java.util.List;

final class RecordingSink implements SnapshotSink {
    final List<Snapshot> all = new ArrayList<Snapshot>();
    boolean throwOnPublish = false;

    @Override
    public void publish(Snapshot snapshot) {
        all.add(snapshot);
        if (throwOnPublish) throw new IllegalStateException("simulated sink fault");
    }

    Snapshot last() {
        return all.get(all.size() - 1);
    }

    /** The phase texts in order, consecutive duplicates removed. */
    List<String> phaseSequence() {
        List<String> out = new ArrayList<String>();
        for (Snapshot s : all) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(s.phase)) out.add(s.phase);
        }
        return out;
    }

    List<String> phasesStartingWith(String prefix) {
        List<String> out = new ArrayList<String>();
        for (String p : phaseSequence()) if (p.startsWith(prefix)) out.add(p);
        return out;
    }

    boolean saw(Aeb aeb, LinkStatus link) {
        for (Snapshot s : all) if (s.aeb == aeb && s.link == link) return true;
        return false;
    }

    int count(String phase) {
        int n = 0;
        for (Snapshot s : all) if (s.phase.equals(phase)) n++;
        return n;
    }
}
