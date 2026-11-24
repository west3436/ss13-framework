package io.github.west3436.ss13.tool;

import io.github.west3436.ss13.api.tool.Linkable;
import io.github.west3436.ss13.registry.FrameworkComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The multitool: it stores a link source in its buffer and links targets to it, through the
 * blocks' {@link Linkable} capabilities, as {@link Linkable} describes. It acts before the block's
 * own use, but only where it has something to do; anywhere else the block gets the click, and once
 * the block's use has passed, or on a sneak-click, which skips it, the multitool says why it did
 * nothing. Sneak-use on nothing clears the buffer.
 *
 * <p>The buffer is the {@link FrameworkComponents#LINK_SOURCE} component. A ZAS³ multitool saved
 * with a controller selected keeps it: its old custom data is read as the buffer until a new source
 * is stored.
 */
public class MultitoolItem extends Item {
    /** The keys of a selection ZAS³'s multitool kept in its custom data, read as a buffer. */
    private static final String LEGACY_CONTROLLER = "Controller";

    private static final String LEGACY_DIMENSION = "Dimension";
    private static final String LEGACY_VERSION = "FormatVersion";

    public MultitoolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Ahead of the block's own use: acts only where it has something to do. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return use(context, true);
    }

    /** Once the block's own use has passed, or a sneak-click skipped it: acts, or says why it can't. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return use(context, false);
    }

    /**
     * A use on a block: stores a source, or links a target to the stored source. It decides from the
     * stack and the blocks, which the client knows too.
     *
     * @param first whether the block's own use is still to come; if so, it passes where it has
     *     nothing to do, so the block gets the click
     */
    private static InteractionResult use(UseOnContext context, boolean first) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Linkable here = level.getCapability(Linkable.BLOCK, pos);
        if (here == null) {
            return InteractionResult.PASS;
        }
        Optional<BlockPos> stored = source(stack, level);
        Optional<BlockPos> asSource = here.source(context);
        if (asSource.isPresent()) {
            // The source already stored has nothing to do, so its own use, such as its window, gets the click.
            if (first && stored.equals(asSource)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                store(stack, server, asSource.get());
                tell(context.getPlayer(), here.selected(server, asSource.get()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        Optional<BlockPos> target = here.target(context);
        if (target.isEmpty()) {
            return InteractionResult.PASS;
        }
        Linkable source = stored.filter(level::isLoaded)
                .map(at -> level.getCapability(Linkable.BLOCK, at))
                .orElse(null);
        if (source == null) {
            return nothingToDo(
                    context,
                    first,
                    stored.isEmpty()
                            ? Component.translatable("item.ss13_framework.multitool.no_source")
                            : Component.translatable(
                                    "item.ss13_framework.multitool.source_gone",
                                    stored.get().toShortString()));
        }
        BlockPos at = stored.get();
        if (!source.accepts(level, at, target.get())) {
            if (first) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                tell(context.getPlayer(), source.refusal(server, at, target.get()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel server) {
            tell(context.getPlayer(), source.link(server, at, target.get(), context.getPlayer()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A use with nothing to do: before the block's own use it passes, so the block gets the click;
     * after, it says why.
     */
    private static InteractionResult nothingToDo(UseOnContext context, boolean first, Component why) {
        if (first) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            tell(context.getPlayer(), why);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Sneak-use on nothing clears the buffer. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stored(stack).isPresent()) {
            if (!level.isClientSide) {
                clear(stack);
                tell(player, Component.translatable("item.ss13_framework.multitool.cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        stored(stack)
                .ifPresent(at -> lines.add(Component.translatable(
                                "item.ss13_framework.multitool.buffer",
                                at.pos().toShortString(),
                                at.dimension().location().toString())
                        .withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Stores a source in a multitool's buffer, in place of any it held.
     *
     * @param stack the multitool
     * @param level the source's level
     * @param source the source's position
     */
    public static void store(ItemStack stack, Level level, BlockPos source) {
        clearLegacy(stack);
        stack.set(FrameworkComponents.LINK_SOURCE.get(), GlobalPos.of(level.dimension(), source.immutable()));
    }

    /**
     * Empties a multitool's buffer.
     *
     * @param stack the multitool
     */
    public static void clear(ItemStack stack) {
        clearLegacy(stack);
        stack.remove(FrameworkComponents.LINK_SOURCE.get());
    }

    /**
     * The source a multitool holds, if it is in a level.
     *
     * @param stack the multitool
     * @param level the level
     * @return the source's position, or empty if it holds none or one in another dimension
     */
    public static Optional<BlockPos> source(ItemStack stack, Level level) {
        return stored(stack).filter(at -> at.dimension() == level.dimension()).map(GlobalPos::pos);
    }

    /**
     * The source a multitool holds, wherever it is.
     *
     * @param stack the multitool
     * @return the source, or empty if it holds none
     */
    public static Optional<GlobalPos> stored(ItemStack stack) {
        GlobalPos at = stack.get(FrameworkComponents.LINK_SOURCE.get());
        return at != null ? Optional.of(at) : Optional.ofNullable(legacy(stack));
    }

    /** A selection ZAS³'s multitool saved in its custom data, if it has one. */
    private static @Nullable GlobalPos legacy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(LEGACY_CONTROLLER)) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(LEGACY_DIMENSION));
        return dimension == null
                ? null
                : GlobalPos.of(
                        ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(tag.getLong(LEGACY_CONTROLLER)));
    }

    /** Drops ZAS³'s old selection, keeping any other custom data. */
    private static void clearLegacy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(LEGACY_CONTROLLER)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(LEGACY_CONTROLLER);
            tag.remove(LEGACY_DIMENSION);
            tag.remove(LEGACY_VERSION);
        });
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
