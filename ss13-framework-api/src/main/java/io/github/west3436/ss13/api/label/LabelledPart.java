package io.github.west3436.ss13.api.label;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that is one of several making up a device, such as an airlock's frame, a door's upper
 * half or a two-block controller. One block of the device keeps its label, and {@link Labels#at}
 * asks the block a player clicked which one that is, so a label reads and is set from any of them.
 *
 * <p>Implemented by the {@link net.minecraft.world.level.block.Block}, not its block entity. A
 * block that stands alone needn't implement it.
 */
@FunctionalInterface
public interface LabelledPart {
    /**
     * The block that keeps the label for this one. It must work from the state alone, as for a
     * door's half, and load nothing.
     *
     * @param state this block's state
     * @param pos where this block is
     * @return the position of the block whose block entity is the {@link Labelled} device; {@code
     *     pos} itself for the block that keeps it
     */
    BlockPos labelledAt(BlockState state, BlockPos pos);
}
