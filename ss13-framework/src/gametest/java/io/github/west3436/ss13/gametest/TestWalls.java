package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.structure.Dismantlable;
import io.github.west3436.ss13.api.structure.Dismantled;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * A test mod's walls and windows, on vanilla blocks, filled in the framework's tags the way a mod
 * fills them, so the GameTests and a person with a welder in a dev run can take them apart:
 *
 * <ul>
 *   <li><b>Bricks</b> are a wall with no {@link Dismantlable}: they come apart into their loot.
 *   <li><b>Chiseled stone bricks</b> are a wall whose {@link Dismantlable} leaves <b>iron bars</b>
 *       and drops a stone brick, as ZAS³'s wall with a pipe in it leaves the pipe.
 *   <li><b>Obsidian</b> is a reinforced wall.
 *   <li><b>Tinted glass</b> is a window with no {@link Dismantlable}.
 * </ul>
 *
 * <p>The tags come from a data pack in the gametest source set, {@code test_data/walls}, which is
 * always on in a dev run and never in the mod jar.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID)
public final class TestWalls {
    private TestWalls() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Dismantlable.BLOCK,
                (level, pos, state, be, context) -> (l, p, s, player, tool) -> new Dismantled(
                        Blocks.IRON_BARS.defaultBlockState(), List.of(new ItemStack(Items.STONE_BRICKS))),
                Blocks.CHISELED_STONE_BRICKS);
    }

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addPackFinders(
                    ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "test_data/walls"),
                    PackType.SERVER_DATA,
                    Component.literal("SS13 Framework test walls"),
                    PackSource.BUILT_IN,
                    true,
                    Pack.Position.TOP);
        }
    }
}
