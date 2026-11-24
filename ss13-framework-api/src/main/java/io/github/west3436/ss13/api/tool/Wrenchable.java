package io.github.west3436.ss13.api.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * A block the wrench turns and removes, offered as the block capability {@link #BLOCK} by the mod
 * that owns the block, for its blocks or block entity types.
 *
 * <p>What the framework's wrench does with it, before the block's own use, so a block's window
 * doesn't get the click:
 *
 * <ul>
 *   <li><b>Use</b> turns the block to {@link #turned}, sets that state, tells the game the
 *       block's capabilities changed, and calls {@link #afterTurn}. If nothing turns, the click
 *       fails.
 *   <li><b>Sneak-use</b> removes it: if {@link #refusal} gives a reason, such as a pipe holding too
 *       much pressure, it tells the player and keeps the block; if not, it fires NeoForge's block
 *       break event, so a claim or protection mod can refuse, and then calls {@link #remove}.
 * </ul>
 *
 * <p>A block with no {@code Wrenchable} doesn't take the wrench: the click goes to the block, which
 * can check {@link ToolTags#WRENCHES} itself.
 */
public interface Wrenchable {
    /** {@code ss13_framework:wrenchable}: the capability, with no context. */
    BlockCapability<Wrenchable, @Nullable Void> BLOCK = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "wrenchable"), Wrenchable.class);

    /**
     * The block's state after a turn by this use, decided from what the client knows too, so both
     * sides agree whether it turns. By default the block's own quarter turn clockwise about the
     * vertical ({@link BlockState#rotate}).
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param use the wrench's use on the block, its clicked face included
     * @return the turned state, or the block's state as it is if nothing turns that way
     */
    default BlockState turned(UseOnContext use) {
        BlockState state = use.getLevel().getBlockState(use.getClickedPos());
        return state.rotate(use.getLevel(), use.getClickedPos(), Rotation.CLOCKWISE_90);
    }

    /**
     * Called once the wrench has set the turned state, to do what else a turn takes, such as
     * joining pipes on the new faces or playing a sound. Does nothing by default.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param use the wrench's use on the block
     */
    default void afterTurn(ServerLevel level, BlockPos pos, UseOnContext use) {}

    /**
     * Why the block may not come off now, such as a part holding more than 2 atm over the
     * ambient. None by default.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param use the wrench's use on the block
     * @return what to tell the player, or empty if it may come off
     */
    default Optional<Component> refusal(ServerLevel level, BlockPos pos, UseOnContext use) {
        return Optional.empty();
    }

    /**
     * Removes the block, which may come off and whose break event nobody cancelled. By default as
     * a player mining it would: {@link #removeAsMined}.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param use the wrench's use on the block
     */
    default void remove(ServerLevel level, BlockPos pos, UseOnContext use) {
        removeAsMined(level, pos, use.getPlayer(), use.getItemInHand());
    }

    /**
     * Removes a block as a player mining it would: it drops its loot, with the tool as the drops'
     * tool, except for a player in creative, and then it is destroyed without dropping again.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block
     * @param player who removes it, or null
     * @param tool the tool, for the loot
     */
    static void removeAsMined(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (player == null || !player.isCreative()) {
            Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, tool);
        }
        level.destroyBlock(pos, false, player);
    }
}
