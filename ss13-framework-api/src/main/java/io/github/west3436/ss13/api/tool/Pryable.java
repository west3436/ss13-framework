package io.github.west3436.ss13.api.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * A block the crowbar pries, such as a door or a firelock it forces open or shut, offered as the
 * block capability {@link #BLOCK} by the mod that owns the block.
 *
 * <p>The framework's crowbar acts after the block's own use, as an item's use on a block does: a
 * block that should let the crowbar through answers its own {@code useItemOn} with {@code
 * PASS_TO_DEFAULT_BLOCK_INTERACTION} or {@code SKIP_DEFAULT_BLOCK_INTERACTION} for a stack in
 * {@link ToolTags#CROWBARS}. Then, where {@link #canPry} says so, it calls {@link #pry} on the
 * server. A block with no {@code Pryable} doesn't take the crowbar.
 */
public interface Pryable {
    /** {@code ss13_framework:pryable}: the capability, with no context. */
    BlockCapability<Pryable, @Nullable Void> BLOCK = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "pryable"), Pryable.class);

    /**
     * Whether the crowbar has something to pry here, decided from what the client knows too, so
     * both sides agree whether the click is taken. True by default.
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param use the crowbar's use on the block
     * @return true to take the click and pry; false to leave it, as if the block had no capability
     */
    default boolean canPry(UseOnContext use) {
        return true;
    }

    /**
     * Pries the block: whatever the crowbar does to it, sounds and messages included.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block clicked
     * @param use the crowbar's use on the block
     */
    void pry(ServerLevel level, BlockPos pos, UseOnContext use);
}
