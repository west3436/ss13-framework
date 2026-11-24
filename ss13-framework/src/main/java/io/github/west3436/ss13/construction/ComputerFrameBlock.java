package io.github.west3436.ss13.construction;

import com.mojang.serialization.MapCodec;
import io.github.west3436.ss13.api.construction.FrameType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * {@code ss13_framework:computer_frame}: anchor it with a wrench, insert a computer board, secure
 * it with a screwdriver, wire it with a cable coil, add a console screen, then close it with a
 * screwdriver.
 */
public final class ComputerFrameBlock extends FrameBlock {
    public static final MapCodec<ComputerFrameBlock> CODEC = simpleCodec(ComputerFrameBlock::new);

    /** Empty, board in, board secured, wired, then screen on. */
    public static final EnumProperty<FrameStage> STAGE = EnumProperty.create(
            "stage",
            FrameStage.class,
            FrameStage.EMPTY,
            FrameStage.BOARD,
            FrameStage.SECURED,
            FrameStage.WIRED,
            FrameStage.SCREEN);

    public ComputerFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(STAGE, FrameStage.EMPTY)
                .setValue(ANCHORED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ComputerFrameBlock> codec() {
        return CODEC;
    }

    @Override
    public FrameType type() {
        return FrameType.COMPUTER;
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
