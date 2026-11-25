package io.github.west3436.ss13.construction;

import io.github.west3436.ss13.api.construction.FrameType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A frame's block: the machine frame ({@link FrameBlock}) or the computer frame ({@link
 * ComputerFrameBlock}, a 2 x 2 console). Its state has {@link FrameBlock#ANCHORED}, {@link
 * FrameBlock#FACING} and its {@link #stage()}; its block entity, a {@link FrameBlockEntity}, is at
 * its position, or at the console's lower left block for the computer frame.
 */
public interface Frame {
    /**
     * The frame's type.
     *
     * @return machine or computer
     */
    FrameType type();

    /**
     * Its {@code stage} property, whose values are the stages this type of frame goes through.
     *
     * @return the property
     */
    EnumProperty<FrameStage> stage();

    /**
     * This frame in a stage, anchored and facing a way: what a machine becomes when it is taken
     * apart. For the computer frame it is the console's lower left block.
     *
     * @param stage the stage
     * @param facing the way it faces
     * @return the state
     */
    BlockState state(FrameStage stage, Direction facing);
}
