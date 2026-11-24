package io.github.west3436.ss13.construction;

import io.github.west3436.ss13.api.construction.FrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A frame a machine or computer is built in, step by step, with tools and parts used on it; see
 * {@link Construction}. It is placed unanchored, facing the player, and keeps what has gone in in
 * its {@link FrameBlockEntity}. Breaking it drops the frame and everything in it.
 */
public abstract class FrameBlock extends Block implements EntityBlock {
    /** Whether a wrench has anchored it; nothing goes in until it has. */
    public static final BooleanProperty ANCHORED = BooleanProperty.create("anchored");

    /** The way its front faces, which the finished machine faces too. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected FrameBlock(Properties properties) {
        super(properties);
    }

    /**
     * The frame's type.
     *
     * @return machine or computer
     */
    public abstract FrameType type();

    /**
     * Its {@code stage} property, whose values are the stages this type of frame goes through.
     *
     * @return the property
     */
    public abstract EnumProperty<FrameStage> stage();

    /**
     * This frame in a stage, anchored and facing a way: what a machine becomes when it is taken
     * apart.
     *
     * @param stage the stage
     * @param facing the way it faces
     * @return the state
     */
    public BlockState state(FrameStage stage, Direction facing) {
        return defaultBlockState()
                .setValue(stage(), stage)
                .setValue(ANCHORED, true)
                .setValue(FACING, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FrameBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        Construction.useOnFrame(level, pos, state, player, stack);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Construction.describeFrame(level, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FrameBlockEntity frame) {
            for (ItemStack drop : frame.drops(state)) {
                popResource(level, pos, drop);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
