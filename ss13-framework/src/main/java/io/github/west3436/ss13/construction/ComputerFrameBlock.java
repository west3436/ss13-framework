package io.github.west3436.ss13.construction;

import com.mojang.serialization.MapCodec;
import io.github.west3436.ss13.api.console.ConsoleBlock;
import io.github.west3436.ss13.api.construction.FrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code ss13_framework:computer_frame}, a console 2 blocks wide, 2 tall and 1 deep, as the
 * computers built in it are ({@link ConsoleBlock}): anchor it with a wrench, insert a computer
 * board, secure it with a screwdriver, wire it with a cable coil, add a console screen, then close
 * it with a screwdriver. Each step works on any of its four blocks and acts on the lower left
 * one's {@link FrameBlockEntity}; all four always show the same stage and anchoring.
 *
 * <p>A one-block computer frame saved before 0.3.1 loads as the console's lower left block alone.
 * It works as before; the first tool used on it adds the other three blocks if their spaces are
 * free.
 */
public final class ComputerFrameBlock extends ConsoleBlock implements Frame {
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
        registerDefaultState(
                defaultBlockState().setValue(STAGE, FrameStage.EMPTY).setValue(FrameBlock.ANCHORED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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
    public BlockState state(FrameStage stage, Direction facing) {
        return defaultBlockState()
                .setValue(STAGE, stage)
                .setValue(FrameBlock.ANCHORED, true)
                .setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAGE, FrameBlock.ANCHORED);
    }

    @Override
    protected BlockEntity newConsoleEntity(BlockPos pos, BlockState state) {
        return new FrameBlockEntity(pos, state);
    }

    /** The four blocks show one stage and anchoring. */
    @Override
    protected BlockState matchPartner(BlockState state, BlockState partner) {
        return state.setValue(STAGE, partner.getValue(STAGE))
                .setValue(FrameBlock.ANCHORED, partner.getValue(FrameBlock.ANCHORED));
    }

    @Override
    protected ItemInteractionResult useItemOnConsole(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos origin,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        Construction.useOnFrame(level, origin, state, player, stack);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useConsole(
            BlockState state, Level level, BlockPos origin, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Construction.describeFrame(level, origin, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        FrameBlockEntity.dropOnRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }
}
