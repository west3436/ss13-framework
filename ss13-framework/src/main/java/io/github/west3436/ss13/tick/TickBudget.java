package io.github.west3436.ss13.tick;

import org.jetbrains.annotations.Nullable;

/**
 * What may still be spent in one server tick: work units and wall time. A job's own budget sits
 * within a shared one, which every job in the tick spends too, so many jobs at once cost no more a
 * tick than the shared budget allows. Expedition's ship mover is the first user: each move has a
 * budget of its own within the server's.
 *
 * <p>What a work unit is worth is the user's to decide; Expedition counts a plain block carried as
 * one and a block entity as 32. Make a budget at the start of each tick, spend from it, and stop
 * once {@link #spent} says so.
 *
 * <p>The clock is read at most every 32 calls of {@link #spent}, or once 64 work units have been
 * spent since it was last read, whichever comes first, so asking is cheap and a costly step can't
 * run far past the time.
 *
 * <p>Thread: one thread, usually the server's. A budget isn't synchronized.
 */
public final class TickBudget {
    /** How many steps go between two reads of the clock, at most. */
    private static final int CLOCK_EVERY = 32;

    /**
     * How many work units go between two reads of the clock, at most: about 15 µs of Expedition's
     * carrying, or two positions of settling.
     */
    private static final int CLOCK_UNITS = 64;

    private final @Nullable TickBudget outer;

    private int work;
    private long nanos;
    private int steps;
    private int sinceClock;

    /**
     * A budget.
     *
     * @param work work units
     * @param nanos wall time, in nanoseconds
     * @param outer a budget this one sits within, which is spent along with it, or null
     */
    public TickBudget(int work, long nanos, @Nullable TickBudget outer) {
        this.work = work;
        this.nanos = nanos;
        this.outer = outer;
    }

    /**
     * A budget that never runs out, for finishing every job when the server stops.
     *
     * @return the budget
     */
    public static TickBudget unlimited() {
        return new TickBudget(Integer.MAX_VALUE, Long.MAX_VALUE, null);
    }

    /**
     * Spends work units, here and in the outer budget.
     *
     * @param units how many
     */
    public void spend(int units) {
        work = units >= work ? 0 : work - units;
        sinceClock += units;
        if (outer != null) {
            outer.spend(units);
        }
    }

    /**
     * Spends wall time a job has used, here and in the outer budget. A job that measures its own
     * time spends it this way, so that a later job in the same tick finds less left.
     *
     * @param spent nanoseconds
     */
    public void spendNanos(long spent) {
        nanos = spent >= nanos ? 0 : nanos - spent;
        if (outer != null) {
            outer.spendNanos(spent);
        }
    }

    /**
     * Whether this budget or the outer one has run out.
     *
     * @param since when the job asking began this tick's work, from {@link System#nanoTime()};
     *     time since then counts as spent
     * @return true if no work or no time is left
     */
    public boolean spent(long since) {
        if (work <= 0 || nanos <= 0) {
            return true;
        }
        if (outer != null && outer.spent(since)) {
            return true;
        }
        if (nanos == Long.MAX_VALUE || (++steps < CLOCK_EVERY && sinceClock < CLOCK_UNITS)) {
            return false;
        }
        steps = 0;
        sinceClock = 0;
        return System.nanoTime() - since >= nanos;
    }
}
