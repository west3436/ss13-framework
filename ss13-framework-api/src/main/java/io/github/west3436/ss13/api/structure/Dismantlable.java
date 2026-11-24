package io.github.west3436.ss13.api.structure;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * How a block comes apart when a tool takes it apart, offered as the block capability {@link
 * #BLOCK} by the mod that owns the block. A tool doesn't ask it directly but through {@link
 * Dismantling#takeApart}, which checks claims first.
 *
 * <p>A block needs one only when it comes apart into something other than its own loot: ZAS³
 * registers it on its walls and floor grille that hold a pipe, so taking the casing away leaves the
 * bare pipe in its network. A block in {@link StructureTags#WALLS} or {@link StructureTags#WINDOWS}
 * with no {@code Dismantlable} is removed and drops its loot.
 */
public interface Dismantlable {
    /** {@code ss13_framework:dismantlable}: the capability, with no context. */
    BlockCapability<Dismantlable, @Nullable Void> BLOCK = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "dismantlable"), Dismantlable.class);

    /**
     * What taking the block apart leaves in its place and drops.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param state its state
     * @param player who takes it apart, or null; a player in creative gets no drops
     * @param tool the tool, such as a welder
     * @return the block left and the drops
     */
    Dismantled dismantled(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player, ItemStack tool);

    /**
     * Puts the result into the world. By default {@link Dismantling#place}: the block left is set in
     * place and the drops pop out. A block whose contents must carry over, such as a pipe that keeps
     * its network, overrides this to move them across.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param result what {@link #dismantled} answered
     * @param player who takes it apart, or null
     */
    default void apply(ServerLevel level, BlockPos pos, Dismantled result, @Nullable Player player) {
        Dismantling.place(level, pos, result);
    }
}
