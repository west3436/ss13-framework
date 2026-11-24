package io.github.west3436.ss13.device;

/**
 * How hard a device is working: the amount of work it reported in its last device tick, read
 * against a scale of four levels, with hysteresis so the level doesn't flicker.
 *
 * <p>The amount is in whatever a device's work is best counted in, on this one scale, so that the
 * levels mean the same for every kind. A device that moves gas, as ZAS³'s do, reports the moles it
 * moved; a device that merely runs reports {@link #RUNNING} while it does.
 *
 * <table>
 *   <caption>The scale, in amount per device tick (a second)</caption>
 *   <tr><th>Level</th><th>From</th><th>Falls below</th></tr>
 *   <tr><td>{@link #IDLE}</td><td>0</td><td></td></tr>
 *   <tr><td>{@link #TRICKLE}</td><td>0.05</td><td>0.025</td></tr>
 *   <tr><td>{@link #WORKING}</td><td>1</td><td>0.5</td></tr>
 *   <tr><td>{@link #HARD}</td><td>5</td><td>2.5</td></tr>
 * </table>
 *
 * <p>A level rises at once. It falls only once {@value #HOLD_STEPS} steps running have read below its
 * falling bound, so a device that works every other step holds steady. A device that was switched
 * off, or whose work no longer shows, says so ({@link #stopNow}) and drops at once.
 *
 * <p>Platform-free. Server thread only.
 */
public final class DeviceActivity {
    /** Not working, or next to nothing. No loop and no particles. */
    public static final int IDLE = 0;

    /** Working a little: a quiet loop. */
    public static final int TRICKLE = 1;

    /** Working a fair amount. */
    public static final int WORKING = 2;

    /** Working hard. */
    public static final int HARD = 3;

    /** The amount per device tick at which each level starts. */
    private static final double[] RISES = {0, 0.05, 1, 5};

    /** What a device that merely runs reports while it does: enough to read as {@link #WORKING}. */
    public static final double RUNNING = RISES[WORKING];

    /** A level falls below this fraction of where it starts. */
    private static final double FALL_RATIO = 0.5;

    /** How many steps running must read low before a level falls. */
    public static final int HOLD_STEPS = 2;

    private double amount;
    private int level = IDLE;
    private int lowSteps;
    private boolean stopped;

    /** Adds to the work done in this step. Anything not above 0 counts for nothing. */
    public void add(double amount) {
        if (amount > 0) {
            this.amount += amount;
        }
    }

    /**
     * The device has stopped, as when switched off or when its work falls below what shows: this
     * step's level is {@link #IDLE}, at once.
     */
    public void stopNow() {
        stopped = true;
    }

    /**
     * Ends a step.
     *
     * @return the level after it
     */
    public int finishStep() {
        double done = amount;
        amount = 0;
        if (stopped) {
            stopped = false;
            level = IDLE;
            lowSteps = 0;
            return level;
        }
        int rising = levelOf(done);
        if (rising >= level || done >= falls(level)) {
            level = Math.max(level, rising);
            lowSteps = 0;
        } else if (++lowSteps >= HOLD_STEPS) {
            lowSteps = 0;
            while (level > IDLE && done < falls(level)) {
                level--;
            }
        }
        return level;
    }

    /** The level now. */
    public int level() {
        return level;
    }

    /**
     * How loud a device's loop plays at a level, before its distance.
     *
     * @return 0 to 1, and 0 only when idle
     */
    public static float volume(int level) {
        return switch (level) {
            case TRICKLE -> 0.35f;
            case WORKING -> 0.6f;
            case HARD -> 1f;
            default -> 0f;
        };
    }

    /** The level that an amount in a step rises to. */
    private static int levelOf(double amount) {
        int k = IDLE;
        while (k < HARD && amount >= RISES[k + 1]) {
            k++;
        }
        return k;
    }

    private static double falls(int level) {
        return RISES[level] * FALL_RATIO;
    }
}
