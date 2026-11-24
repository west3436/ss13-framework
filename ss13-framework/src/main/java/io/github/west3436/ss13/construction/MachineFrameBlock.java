package io.github.west3436.ss13.construction;

import com.mojang.serialization.MapCodec;
import io.github.west3436.ss13.api.construction.FrameType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * {@code ss13_framework:machine_frame}: anchor it with a wrench, wire it with a cable coil, insert
 * a machine board, add the parts the board names, then close it with a screwdriver.
 */
public final class MachineFrameBlock extends FrameBlock {
    public static final MapCodec<MachineFrameBlock> CODEC = simpleCodec(MachineFrameBlock::new);

    /** Empty, wired, then board in. */
    public static final EnumProperty<FrameStage> STAGE =
            EnumProperty.create("stage", FrameStage.class, FrameStage.EMPTY, FrameStage.WIRED, FrameStage.BOARD);

    public MachineFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(STAGE, FrameStage.EMPTY)
                .setValue(ANCHORED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<MachineFrameBlock> codec() {
        return CODEC;
    }

    @Override
    public FrameType type() {
        return FrameType.MACHINE;
    }

    @Override
    public EnumProperty<FrameStage> stage() {
        return STAGE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, ANCHORED, FACING);
    }
}
