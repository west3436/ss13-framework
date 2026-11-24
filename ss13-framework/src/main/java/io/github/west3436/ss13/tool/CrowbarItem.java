package io.github.west3436.ss13.tool;

import io.github.west3436.ss13.api.tool.Pryable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The crowbar: it pries {@link Pryable} blocks, as {@link Pryable} describes, once the block's own
 * use has passed. On any other block it does nothing.
 */
public class CrowbarItem extends Item {
    public CrowbarItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Pryable pryable = level.getCapability(Pryable.BLOCK, context.getClickedPos());
        if (pryable == null || !pryable.canPry(context)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            pryable.pry(server, context.getClickedPos(), context);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
