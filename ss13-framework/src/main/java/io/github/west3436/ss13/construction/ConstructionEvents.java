package io.github.west3436.ss13.construction;

import io.github.west3436.ss13.api.construction.FrameType;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.api.construction.MachineBoard;
import io.github.west3436.ss13.api.construction.MachineBoards;
import io.github.west3436.ss13.api.construction.PartHolder;
import io.github.west3436.ss13.api.construction.PartRequirement;
import io.github.west3436.ss13.api.construction.PartTags;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Hooks construction into the game: tools and parts used on built machines, machines placed from
 * and mined into their items, and the tooltips of boards, parts and machines that carry parts.
 */
public final class ConstructionEvents {
    private static final String TOOLTIP = "tooltip.ss13_framework.construction.";

    private ConstructionEvents() {}

    /**
     * Registers the listeners.
     *
     * @param gameBus the game's event bus
     */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ConstructionEvents::onUseBlock);
        gameBus.addListener(ConstructionEvents::onPlace);
        gameBus.addListener(ConstructionEvents::onDrops);
        gameBus.addListener(ConstructionEvents::onTooltip);
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        boolean taken = level.isClientSide
                ? Construction.isMachineTool(level, pos, stack)
                : Construction.useOnMachine(level, pos, event.getEntity(), stack);
        if (taken) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level
                && !level.isClientSide
                && event.getEntity() instanceof LivingEntity placer) {
            Construction.placed(level, event.getPos(), placer);
        }
    }

    /**
     * Keeps a mined machine's board and parts on its item, unless they are just the stock ones, or
     * drops them beside it if its loot has no item of its own.
     */
    private static void onDrops(BlockDropsEvent event) {
        if (!(event.getBlockEntity() instanceof PartHolder holder)) {
            return;
        }
        InstalledParts parts = holder.installedParts();
        BlockState state = event.getState();
        ResourceLocation machine = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Optional<ResourceLocation> boardId = MachineBoards.boardFor(machine);
        if (parts.isEmpty() || boardId.isEmpty()) {
            return;
        }
        Item item = state.getBlock().asItem();
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(item)) {
                if (!sameItems(
                        parts,
                        Construction.stock(
                                boardId.get(), MachineBoards.get(boardId.get()).orElseThrow()))) {
                    ItemStack stack = drop.getItem().copy();
                    stack.set(FrameworkBlocks.INSTALLED_PARTS.get(), parts);
                    drop.setItem(stack);
                }
                return;
            }
        }
        BlockPos pos = event.getPos();
        List<ItemStack> loose = new ArrayList<>(parts.parts());
        if (!parts.board().isEmpty()) {
            loose.add(0, parts.board());
        }
        for (ItemStack stack : loose) {
            event.getDrops()
                    .add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
        }
    }

    /** Whether two sets of board and parts hold the same items, in any order. */
    static boolean sameItems(InstalledParts a, InstalledParts b) {
        if (!ItemStack.isSameItemSameComponents(a.board(), b.board())
                || a.parts().size() != b.parts().size()) {
            return false;
        }
        List<ItemStack> left = new ArrayList<>(b.parts());
        for (ItemStack part : a.parts()) {
            int match = -1;
            for (int i = 0; i < left.size() && match < 0; i++) {
                if (ItemStack.isSameItemSameComponents(part, left.get(i))) {
                    match = i;
                }
            }
            if (match < 0) {
                return false;
            }
            left.remove(match);
        }
        return true;
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        MachineBoards.of(stack).ifPresent(board -> describeBoard(board, lines));
        InstalledParts carried = stack.get(FrameworkBlocks.INSTALLED_PARTS.get());
        if (carried != null && !carried.parts().isEmpty()) {
            List<Component> names = new ArrayList<>();
            carried.parts().forEach(p -> names.add(p.getHoverName()));
            lines.add(
                    Component.translatable(TOOLTIP + "parts", ComponentUtils.formatList(names, Component.literal(", ")))
                            .withStyle(ChatFormatting.GRAY));
        }
        int tier = PartTags.tierOf(stack);
        if (tier > 0) {
            lines.add(Component.translatable(TOOLTIP + "tier", tier).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void describeBoard(MachineBoard board, List<Component> lines) {
        Component machine = BuiltInRegistries.BLOCK
                .getOptional(board.machine())
                .<Component>map(b -> b.getName())
                .orElse(Component.literal(board.machine().toString()));
        lines.add(Component.translatable(
                        TOOLTIP + "builds." + board.frame().name().toLowerCase(Locale.ROOT), machine)
                .withStyle(ChatFormatting.GRAY));
        if (board.frame() == FrameType.MACHINE && !board.parts().isEmpty()) {
            Map<PartRequirement, Integer> all = new LinkedHashMap<>();
            board.parts().forEach(r -> all.put(r, r.count()));
            lines.add(Component.translatable(TOOLTIP + "needs", Construction.list(all))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
