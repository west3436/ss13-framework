package io.github.west3436.ss13.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What a comparator reads from a device, on one scale for every mod's devices.
 *
 * <ul>
 *   <li><b>A container</b> reads how full it is: 0 only when it holds nothing, otherwise ⌈15 ×
 *       share⌉, so anything at all reads at least 1 and 15 means more than 14/15 full. What full
 *       means is the container's own: its capacity, or the most it can hold before it fails, so 15
 *       is about to fail.
 *   <li><b>A reader</b>, such as a gauge or a sensor, reads where its dial points: ⌊15 × along +
 *       ½⌋, 0 at rest and 15 at the top of its scale.
 * </ul>
 *
 * <p>A block whose signal comes from what its step reads tells its comparators when that changes,
 * with an {@link Output}.
 */
public final class Comparators {
    /** The strongest signal. */
    public static final int MAX = 15;

    private Comparators() {}

    /**
     * A container's signal for how full it is.
     *
     * @param share what it holds over what it holds when full; above 1 is full
     * @return 0 for nothing (or no number), otherwise 1 to 15
     */
    public static int fill(double share) {
        if (!(share > 0)) {
            return 0;
        }
        return (int) Math.max(1, Math.min(MAX, Math.ceil(MAX * share - 1e-9)));
    }

    /**
     * A reader's signal: where its dial points.
     *
     * @param along 0 at rest to 1 at the top; outside that is clamped
     */
    public static int dial(double along) {
        if (!(along > 0)) {
            return 0;
        }
        return (int) Math.floor(MAX * Math.min(1, along) + 0.5);
    }

    /**
     * The last signal a block told its comparators of, so it tells them again only when it changes.
     * Not saved: after a load the first step tells them, which changes nothing for a comparator that
     * already reads the same.
     */
    public static final class Output {
        private int last = -1;

        /**
         * Tells the comparators around a block if its signal is not the one they were last told of.
         *
         * @param level the block's level, server side
         * @param pos the block
         * @param block the block, as the comparators look for it
         * @param signal its signal now
         */
        public void update(Level level, BlockPos pos, Block block, int signal) {
            if (changes(signal)) {
                level.updateNeighbourForOutputSignal(pos, block);
            }
        }

        /**
         * Whether a signal is not the one last told of, keeping it as told: for a block whose
         * comparators sit at more than one position.
         */
        public boolean changes(int signal) {
            if (signal == last) {
                return false;
            }
            last = signal;
            return true;
        }
    }
}
