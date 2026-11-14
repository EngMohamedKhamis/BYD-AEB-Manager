package com.byd.aebtoggle.core;

import java.util.PriorityQueue;

final class FakeScheduler implements Scheduler {
    private static final class Item implements Comparable<Item> {
        final long due;
        final long order;
        final Runnable task;

        Item(long due, long order, Runnable task) {
            this.due = due;
            this.order = order;
            this.task = task;
        }

        @Override
        public int compareTo(Item o) {
            if (due != o.due) return due < o.due ? -1 : 1;
            return order < o.order ? -1 : (order == o.order ? 0 : 1);
        }
    }

    private final FakeClock clock;
    private final PriorityQueue<Item> queue = new PriorityQueue<Item>();
    private long order = 0;
    /** true = cancelAll() does nothing, to prove the engine's own generation guard is enough. */
    boolean ignoreCancel = false;

    FakeScheduler(FakeClock clock) {
        this.clock = clock;
    }

    @Override
    public void schedule(long delayMs, Runnable task) {
        queue.add(new Item(clock.now + Math.max(0L, delayMs), order++, task));
    }

    @Override
    public void cancelAll() {
        if (!ignoreCancel) queue.clear();
    }

    /** Runs every task due up to and including untilMs; the clock moves to each task's due time. */
    void runUntil(long untilMs) {
        int guard = 0;
        while (!queue.isEmpty() && queue.peek().due <= untilMs) {
            if (++guard > 1_000_000) throw new IllegalStateException("scheduler loop: too many tasks");
            Item item = queue.poll();
            if (item.due > clock.now) clock.now = item.due;
            item.task.run();
        }
        if (untilMs > clock.now) clock.now = untilMs;
    }

    int pending() {
        return queue.size();
    }
}
