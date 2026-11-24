package io.github.west3436.ss13.api.structure;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Taking walls and windows apart, for a tool such as a welder or an RCD: a block's {@link
 * Dismantlable} says how it comes apart, and a block in {@link StructureTags#WALLS} or {@link
 * StructureTags#WINDOWS} with none is removed and drops its loot.
 *
 * <p>Which blocks a tool works on is the tool's call: a welder may take walls alone, and ask {@link
 * StructureTags#REINFORCED_WALLS} to weld longer.
 */
public final class Dismantling {
    private Dismantling() {}

    /**
     * Whether a block can be taken apart: it is {@link Dismantlable}, or a wall or window.
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param level the level
     * @param pos the block
     * @return true if {@link #takeApart} would take it apart, claims aside
     */
    public static boolean canTakeApart(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isWallOrWindow(state) || level.getCapability(Dismantlable.BLOCK, pos, state, null) != null;
    }

    /**
     * Takes a block apart, as {@link #canTakeApart} allows: its {@link Dismantlable} says what is
     * left and what drops, or a wall or window with none is removed and drops its loot, with the
     * tool as the drops' tool. A player in creative gets no drops. For a player, NeoForge's block
     * break event is fired first, so a claim or protection mod can refuse. The block's break
     * particles and sound play.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param player who takes it apart, or null
     * @param tool the tool, such as a welder
     * @return true if it came apart; false if it can't be taken apart or the break was refused
     */
    public static boolean takeApart(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack tool) {
        Objects.requireNonNull(tool, "tool");
        BlockState state = level.getBlockState(pos);
        Dismantlable dismantlable = level.getCapability(Dismantlable.BLOCK, pos, state, null);
        if (dismantlable == null && !isWallOrWindow(state)) {
            return false;
        }
        if (player instanceof ServerPlayer server
                && CommonHooks.fireBlockBreak(level, server.gameMode.getGameModeForPlayer(), server, pos, state)
                        .isCanceled()) {
            return false;
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        if (dismantlable == null) {
            place(level, pos, removed(level, pos, state, player, tool));
        } else {
            dismantlable.apply(level, pos, dismantlable.dismantled(level, pos, state, player, tool), player);
        }
        return true;
    }

    /**
     * What a block with no {@link Dismantlable} leaves: nothing, or the fluid it held, and its loot
     * with the tool as the drops' tool, none for a player in creative.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param state its state
     * @param player who takes it apart, or null
     * @param tool the tool
     * @return the result
     */
    public static Dismantled removed(
            ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player, ItemStack tool) {
        List<ItemStack> drops = player != null && player.isCreative()
                ? List.of()
                : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        return new Dismantled(state.getFluidState().createLegacyBlock(), drops);
    }

    /**
     * Puts a result into the world: sets the block left in place, telling neighbours, and pops each
     * drop out where the block was.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param result the block left and the drops
     */
    public static void place(ServerLevel level, BlockPos pos, Dismantled result) {
        level.setBlock(pos, result.left(), Block.UPDATE_ALL);
        for (ItemStack drop : result.drops()) {
            Block.popResource(level, pos, drop.copy());
        }
    }

    private static boolean isWallOrWindow(BlockState state) {
        return state.is(StructureTags.WALLS) || state.is(StructureTags.WINDOWS);
    }
}
