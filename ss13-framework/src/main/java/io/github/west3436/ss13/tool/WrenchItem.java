package io.github.west3436.ss13.tool;

import io.github.west3436.ss13.api.tool.Wrenchable;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * The wrench: it turns and removes {@link Wrenchable} blocks, as {@link Wrenchable} describes,
 * before the block's own use. On any other block the click goes to the block.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Wrenchable wrenchable = level.getCapability(Wrenchable.BLOCK, pos);
        if (wrenchable == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel server) {
                remove(server, pos, wrenchable, context)
                        .ifPresent(refusal -> player.displayClientMessage(refusal, true));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState state = level.getBlockState(pos);
        BlockState turned = wrenchable.turned(context);
        if (turned == state) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, turned, Block.UPDATE_ALL);
            server.invalidateCapabilities(pos);
            wrenchable.afterTurn(server, pos, context);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Removes a wrenchable block unless it refuses: its {@link Wrenchable#refusal}, then NeoForge's
     * block break event for a player, which a claim or protection mod may cancel, then its {@link
     * Wrenchable#remove}.
     *
     * @return what to tell the player if it refused; empty otherwise
     */
    static Optional<Component> remove(ServerLevel level, BlockPos pos, Wrenchable wrenchable, UseOnContext use) {
        Optional<Component> refusal = wrenchable.refusal(level, pos, use);
        if (refusal.isPresent()) {
            return refusal;
        }
        if (use.getPlayer() instanceof ServerPlayer player
                && CommonHooks.fireBlockBreak(
                                level, player.gameMode.getGameModeForPlayer(), player, pos, level.getBlockState(pos))
                        .isCanceled()) {
            return Optional.empty();
        }
        wrenchable.remove(level, pos, use);
        return Optional.empty();
    }
}
