package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.tool.Linkable;
import io.github.west3436.ss13.api.tool.Pryable;
import io.github.west3436.ss13.api.tool.Wrenchable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A test mod's tool behaviour, registered on vanilla blocks the way a mod registers it on its own,
 * so the GameTests and a person trying the tools in a dev run can see each capability work:
 *
 * <ul>
 *   <li>An <b>observer</b> is {@link Wrenchable}: the wrench turns it, and removes it on a
 *       sneak-click unless it has a redstone signal, which stands in for ZAS³'s 2 atm lock.
 *   <li>An <b>iron door</b> is {@link Pryable}: the crowbar forces it open or shut.
 *   <li>A <b>lectern</b> is a {@link Linkable} source, and a <b>redstone lamp</b> a target it
 *       links. A <b>target block</b> is a target too, which the lectern refuses. The lectern keeps
 *       its links in {@link #LINKS}, and they follow it when it moves.
 * </ul>
 *
 * <p>They load with the gametest source set, which every dev run has, never with the mod jar.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID)
public final class TestTools {
    /** Each test source's links, by where it is; read and written on the server thread. */
    public static final Map<GlobalPos, Set<BlockPos>> LINKS = new HashMap<>();

    private TestTools() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Wrenchable.BLOCK, (level, pos, state, be, context) -> OBSERVER, Blocks.OBSERVER);
        event.registerBlock(Pryable.BLOCK, (level, pos, state, be, context) -> DOOR, Blocks.IRON_DOOR);
        event.registerBlock(Linkable.BLOCK, (level, pos, state, be, context) -> SOURCE, Blocks.LECTERN);
        event.registerBlock(
                Linkable.BLOCK, (level, pos, state, be, context) -> TARGET, Blocks.REDSTONE_LAMP, Blocks.TARGET);
    }

    /** A turnable observer that won't come off while it has a redstone signal. */
    private static final Wrenchable OBSERVER = new Wrenchable() {
        @Override
        public Optional<Component> refusal(ServerLevel level, BlockPos pos, UseOnContext use) {
            return level.hasNeighborSignal(pos)
                    ? Optional.of(Component.literal("It has a signal: cut it first"))
                    : Optional.empty();
        }
    };

    /** An iron door the crowbar forces. */
    private static final Pryable DOOR = (level, pos, use) -> {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(use.getPlayer(), level, state, pos, !state.getValue(DoorBlock.OPEN));
        }
    };

    /** A source that links redstone lamps, and keeps its links in {@link #LINKS}. */
    private static final Linkable SOURCE = new Linkable() {
        @Override
        public Optional<BlockPos> source(UseOnContext use) {
            return Optional.of(use.getClickedPos());
        }

        @Override
        public boolean accepts(Level level, BlockPos source, BlockPos target) {
            return level.getBlockState(target).is(Blocks.REDSTONE_LAMP);
        }

        @Override
        public Component link(ServerLevel level, BlockPos source, BlockPos target, @Nullable Player player) {
            Set<BlockPos> links = LINKS.computeIfAbsent(GlobalPos.of(level.dimension(), source), k -> new HashSet<>());
            boolean linked = links.add(target.immutable()) || !links.remove(target);
            return Component.literal((linked ? "Linked " : "Unlinked ") + target.toShortString());
        }

        @Override
        public void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {
            // A test source doesn't know where it was, so it looks for links whose source maps to it.
            for (GlobalPos at : Set.copyOf(LINKS.keySet())) {
                if (!pos.equals(rebase.apply(at.pos()))) {
                    continue;
                }
                Set<BlockPos> moved = new HashSet<>();
                for (BlockPos link : LINKS.remove(at)) {
                    BlockPos now = rebase.apply(link);
                    if (now != null) {
                        moved.add(now);
                    }
                }
                LINKS.put(GlobalPos.of(level.dimension(), pos), moved);
            }
        }
    };

    /** A target: every default. */
    private static final Linkable TARGET = new Linkable() {};
}
