package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.structure.Dismantling;
import io.github.west3436.ss13.api.structure.StructureTags;
import io.github.west3436.ss13.api.tool.WeldingTool;
import io.github.west3436.ss13.registry.FrameworkItems;
import io.github.west3436.ss13.tool.WelderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Walls and windows: the tags, taking them apart with and without a {@link
 * io.github.west3436.ss13.api.structure.Dismantlable}, and the welder that cuts walls apart, on the
 * test mod's blocks ({@link TestWalls}).
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureTests {
    private StructureTests() {}

    @GameTest(template = "empty")
    public static void the_wall_tag_takes_in_the_reinforced_walls(GameTestHelper h) {
        h.assertTrue(Blocks.OBSIDIAN.defaultBlockState().is(StructureTags.REINFORCED_WALLS), "not reinforced");
        h.assertTrue(Blocks.OBSIDIAN.defaultBlockState().is(StructureTags.WALLS), "a reinforced wall isn't a wall");
        h.assertTrue(Blocks.BRICKS.defaultBlockState().is(StructureTags.WALLS), "the test wall isn't a wall");
        h.assertTrue(!Blocks.BRICKS.defaultBlockState().is(StructureTags.REINFORCED_WALLS), "bricks reinforced");
        h.assertTrue(Blocks.TINTED_GLASS.defaultBlockState().is(StructureTags.WINDOWS), "no test window");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_wall_with_no_provider_comes_apart_into_its_loot(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, Blocks.BRICKS);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        h.assertTrue(Dismantling.canTakeApart(level, h.absolutePos(pos)), "can't take the wall apart");
        h.assertTrue(Dismantling.takeApart(level, h.absolutePos(pos), player, welder()), "didn't take it apart");
        h.assertBlockPresent(Blocks.AIR, pos);
        h.assertItemEntityPresent(Items.BRICKS, pos, 1.0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_wall_whose_provider_leaves_a_block_leaves_it(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, Blocks.CHISELED_STONE_BRICKS);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(Dismantling.takeApart(h.getLevel(), h.absolutePos(pos), player, welder()), "didn't take it apart");
        h.assertBlockPresent(Blocks.IRON_BARS, pos);
        h.assertItemEntityPresent(Items.STONE_BRICKS, pos, 1.0);
        h.assertItemEntityNotPresent(Items.CHISELED_STONE_BRICKS, pos, 1.0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_window_comes_apart_and_a_plain_block_does_not(GameTestHelper h) {
        BlockPos window = new BlockPos(1, 2, 1);
        BlockPos stone = new BlockPos(2, 2, 1);
        h.setBlock(window, Blocks.TINTED_GLASS);
        h.setBlock(stone, Blocks.STONE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        h.assertTrue(Dismantling.takeApart(level, h.absolutePos(window), player, welder()), "window stayed");
        h.assertItemEntityPresent(Items.TINTED_GLASS, window, 1.0);
        h.assertTrue(!Dismantling.canTakeApart(level, h.absolutePos(stone)), "stone can be taken apart");
        h.assertTrue(!Dismantling.takeApart(level, h.absolutePos(stone), player, welder()), "took stone apart");
        h.assertBlockPresent(Blocks.STONE, stone);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_player_in_creative_gets_no_drops(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, Blocks.BRICKS);
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        h.assertTrue(Dismantling.takeApart(h.getLevel(), h.absolutePos(pos), player, welder()), "stayed");
        h.assertBlockPresent(Blocks.AIR, pos);
        h.assertItemEntityNotPresent(Items.BRICKS, pos, 1.0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_lit_welder_cuts_a_wall_apart_burning_fuel(GameTestHelper h) {
        BlockPos wall = new BlockPos(1, 2, 1);
        BlockPos reinforced = new BlockPos(2, 2, 1);
        h.setBlock(wall, Blocks.BRICKS);
        h.setBlock(reinforced, Blocks.OBSIDIAN);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, welder());
        ItemStack welder = player.getMainHandItem();
        WeldingTool tool = welder.getCapability(WeldingTool.ITEM);

        aim(h, player, wall);
        h.assertTrue(!welder.useOn(context(h, player, wall)).consumesAction(), "an unlit welder started cutting");
        welder.use(level, player, InteractionHand.MAIN_HAND);
        h.assertTrue(welder.useOn(context(h, player, wall)).consumesAction(), "a lit welder didn't start cutting");
        h.assertValueEqual(welder.getUseDuration(player), WelderItem.WALL_TICKS, "a wall's time");
        welder.finishUsingItem(level, player);
        h.assertBlockPresent(Blocks.AIR, wall);
        h.assertItemEntityPresent(Items.BRICKS, wall, 1.0);
        h.assertValueEqual(tool.fuel(), WelderItem.CAPACITY - WelderItem.WALL_FUEL, "fuel after a wall");
        h.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "welding a wall didn't hurt the eyes");

        aim(h, player, reinforced);
        h.assertValueEqual(welder.getUseDuration(player), WelderItem.REINFORCED_TICKS, "a reinforced wall's time");
        welder.finishUsingItem(level, player);
        h.assertBlockPresent(Blocks.AIR, reinforced);
        h.assertValueEqual(
                tool.fuel(),
                WelderItem.CAPACITY - WelderItem.WALL_FUEL - WelderItem.REINFORCED_FUEL,
                "fuel after a reinforced wall");
        h.succeed();
    }

    /** A new welder. */
    private static ItemStack welder() {
        return new ItemStack(FrameworkItems.WELDER.get());
    }

    /** Stands a player two blocks south of a block, looking at the middle of its south face. */
    private static void aim(GameTestHelper h, Player player, BlockPos rel) {
        Vec3 at = Vec3.atCenterOf(h.absolutePos(rel));
        player.moveTo(at.x, at.y - player.getEyeHeight(), at.z + 2.0, 180.0F, 0.0F);
    }

    private static UseOnContext context(GameTestHelper h, Player player, BlockPos rel) {
        BlockPos pos = h.absolutePos(rel);
        Vec3 hit = Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5);
        return new UseOnContext(
                player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }
}
