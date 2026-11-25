package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.console.ConsoleBlock;
import io.github.west3436.ss13.api.console.ConsoleSide;
import io.github.west3436.ss13.construction.ComputerFrameBlock;
import io.github.west3436.ss13.construction.FrameBlock;
import io.github.west3436.ss13.construction.FrameBlockEntity;
import io.github.west3436.ss13.construction.FrameStage;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import io.github.west3436.ss13.registry.FrameworkItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Consoles 2 blocks wide, 2 tall and 1 deep: the API's {@link ConsoleBlock}, through the test
 * console, and the computer frame built on it. Each console here faces south, so it grows east
 * (+x) and up from its lower left block at {@link #LL}.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConsoleTests {
    static final BlockPos LL = new BlockPos(0, 1, 0);
    static final BlockPos LR = new BlockPos(1, 1, 0);
    static final BlockPos UL = new BlockPos(0, 2, 0);
    static final BlockPos UR = new BlockPos(1, 2, 0);

    /** The four blocks, in {@link ConsoleBlock#parts} order. */
    static final List<BlockPos> FOUR = List.of(LL, LR, UL, UR);

    private ConsoleTests() {}

    /** The 2 x 2 frame built and taken apart with each step on a different block. */
    @GameTest(template = "empty")
    public static void computer_frame_is_built_and_taken_apart_from_any_of_its_blocks(GameTestHelper h) {
        placeWhole(h, FrameworkBlocks.COMPUTER_FRAME.get().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);

        ConstructionTests.use(h, player, UR, FrameworkItems.WRENCH.toStack());
        for (BlockPos p : FOUR) {
            h.assertTrue(h.getBlockState(p).getValue(FrameBlock.ANCHORED), "the wrench didn't anchor " + p);
        }
        ConstructionTests.use(h, player, LR, new ItemStack(TestMachines.consoleBoard()));
        assertFrame(h, FrameStage.BOARD, "the board didn't go in from the lower right block");
        h.assertTrue(frame(h).contents().board().is(TestMachines.consoleBoard()), "the board isn't in the origin");
        ConstructionTests.use(h, player, UL, FrameworkItems.SCREWDRIVER.toStack());
        assertFrame(h, FrameStage.SECURED, "the screwdriver didn't secure the board from the upper left block");
        ConstructionTests.use(h, player, UR, FrameworkItems.CABLE_COIL.toStack());
        assertFrame(h, FrameStage.WIRED, "the cable didn't wire the frame from the upper right block");
        ConstructionTests.use(h, player, LR, FrameworkItems.WIRECUTTERS.toStack());
        assertFrame(h, FrameStage.SECURED, "the wirecutters didn't cut the wiring from the lower right block");
        assertDropped(h, FrameworkItems.CABLE_COIL.get(), 1);
        ConstructionTests.use(h, player, LR, FrameworkItems.CABLE_COIL.toStack());
        ConstructionTests.use(h, player, LL, FrameworkItems.CONSOLE_SCREEN.toStack());
        assertFrame(h, FrameStage.SCREEN, "the screen didn't go on");
        ConstructionTests.use(h, player, UR, FrameworkItems.CROWBAR.toStack());
        assertFrame(h, FrameStage.WIRED, "the crowbar didn't pry the screen off from the upper right block");
        assertDropped(h, FrameworkItems.CONSOLE_SCREEN.get(), 1);
        ConstructionTests.use(h, player, UR, FrameworkItems.CONSOLE_SCREEN.toStack());
        ConstructionTests.use(h, player, LR, FrameworkItems.SCREWDRIVER.toStack());

        assertConsole(h, TestMachines.console(), "the frame didn't become the whole test console");
        TestMachines.TestMachine console = h.getBlockEntity(LL);
        h.assertTrue(console.installedParts().board().is(TestMachines.consoleBoard()), "the console lost its board");
        for (BlockPos p : List.of(LR, UL, UR)) {
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(p)) == null, p + " has a block entity of its own");
        }

        ConstructionTests.use(h, player, UL, FrameworkItems.CROWBAR.toStack());
        assertConsole(h, TestMachines.console(), "a crowbar opened the computer");
        ConstructionTests.use(h, player, UR, FrameworkItems.SCREWDRIVER.toStack());
        assertFrame(h, FrameStage.SCREEN, "the screwdriver on the upper right block didn't take the screen off");
        h.assertTrue(frame(h).contents().board().is(TestMachines.consoleBoard()), "the frame lost the board");
        ConstructionTests.use(h, player, UL, FrameworkItems.SCREWDRIVER.toStack());
        assertConsole(h, TestMachines.console(), "the frame didn't close again");
        h.assertTrue(
                ConstructionTests.items(h, LL).stream()
                        .noneMatch(e -> e.getItem().is(FrameworkItems.COMPUTER_FRAME.get())),
                "building or opening the console dropped a frame");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_console_is_placed_only_where_all_four_blocks_fit(GameTestHelper h) {
        clear(h);
        h.setBlock(LL.below(), Blocks.STONE);
        h.setBlock(UR, Blocks.STONE);
        FakePlayer player = ConstructionTests.builder(h);
        player.setYRot(180.0F); // facing north, so the console faces south

        ItemStack stack = new ItemStack(TestMachines.console());
        place(h, player, stack);
        h.assertBlockState(LL, BlockState::isAir, () -> "a console was placed with its upper right space taken");
        h.assertBlockState(LR, BlockState::isAir, () -> "a refused console left a block beside it");
        h.assertTrue(stack.getCount() == 1, "a refused console used up its item");

        h.setBlock(UR, Blocks.AIR);
        place(h, player, stack);
        assertConsole(h, TestMachines.console(), "the console wasn't placed whole once it fit");
        TestMachines.TestMachine console = h.getBlockEntity(LL);
        h.assertTrue(
                console.placedBy().equals(player.getGameProfile().getName()),
                "the placement hook didn't see the player: " + console.placedBy());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breaking_any_block_of_a_console_breaks_it_all_with_one_drop(GameTestHelper h) {
        placeWhole(h, TestMachines.console().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);
        player.gameMode.destroyBlock(h.absolutePos(UR));
        for (BlockPos p : FOUR) {
            h.assertBlockState(p, BlockState::isAir, () -> p + " is left after breaking the upper right block");
        }
        assertDropped(h, TestMachines.console().asItem(), 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breaking_a_console_in_creative_drops_nothing(GameTestHelper h) {
        placeWhole(h, TestMachines.console().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);
        player.setGameMode(GameType.CREATIVE);
        player.gameMode.destroyBlock(h.absolutePos(LR));
        for (BlockPos p : FOUR) {
            h.assertBlockState(p, BlockState::isAir, () -> p + " is left after breaking the lower right block");
        }
        assertDropped(h, TestMachines.console().asItem(), 0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breaking_any_block_of_a_frame_drops_one_frame_and_what_is_in_it(GameTestHelper h) {
        placeWhole(h, FrameworkBlocks.COMPUTER_FRAME.get().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);
        ConstructionTests.use(h, player, LR, FrameworkItems.WRENCH.toStack());
        ConstructionTests.use(h, player, UR, new ItemStack(TestMachines.consoleBoard()));
        player.gameMode.destroyBlock(h.absolutePos(UL));
        for (BlockPos p : FOUR) {
            h.assertBlockState(p, BlockState::isAir, () -> p + " is left after breaking the upper left block");
        }
        assertDropped(h, FrameworkItems.COMPUTER_FRAME.get(), 1);
        assertDropped(h, TestMachines.consoleBoard(), 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void using_any_block_of_a_console_reaches_its_lower_left_block(GameTestHelper h) {
        placeWhole(h, TestMachines.console().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);
        for (BlockPos p : FOUR) {
            ConstructionTests.use(h, player, p, ItemStack.EMPTY);
        }
        TestMachines.TestMachine console = h.getBlockEntity(LL);
        h.assertTrue(console.uses() == 4, "4 uses reached the console's block entity as " + console.uses());
        for (BlockPos p : FOUR) {
            h.assertTrue(
                    ConsoleBlock.entityAt(h.getLevel(), h.absolutePos(p)) == console,
                    "entityAt doesn't find the console's block entity from " + p);
            h.assertTrue(
                    ConsoleBlock.originAt(h.getLevel(), h.absolutePos(p)).equals(h.absolutePos(LL)),
                    "originAt doesn't find the lower left block from " + p);
        }
        h.succeed();
    }

    /** A one-block frame saved before 0.3.1: kept, grown where it fits, and one frame when broken. */
    @GameTest(template = "empty")
    public static void a_one_block_frame_from_before_consoles_grows_where_it_fits(GameTestHelper h) {
        clear(h);
        BlockState lone =
                FrameworkBlocks.COMPUTER_FRAME.get().defaultBlockState().setValue(FrameBlock.FACING, Direction.SOUTH);
        h.setBlock(LL, lone);
        h.setBlock(UR, Blocks.STONE);
        FakePlayer player = ConstructionTests.builder(h);

        ConstructionTests.use(h, player, LL, FrameworkItems.WRENCH.toStack());
        h.assertTrue(h.getBlockState(LL).getValue(FrameBlock.ANCHORED), "the lone frame didn't work as before");
        h.assertBlockState(LR, BlockState::isAir, () -> "the frame grew in part where it doesn't fit");
        h.assertBlockState(UL, BlockState::isAir, () -> "the frame grew in part where it doesn't fit");
        h.getLevel().destroyBlock(h.absolutePos(LL), true);
        assertDropped(h, FrameworkItems.COMPUTER_FRAME.get(), 1);

        h.setBlock(LL, lone);
        h.setBlock(UR, Blocks.AIR);
        ConstructionTests.use(h, player, LL, FrameworkItems.WRENCH.toStack());
        h.assertTrue(ConsoleBlock.isWhole(h.getLevel(), h.absolutePos(LL)), "the lone frame didn't grow with room");
        for (BlockPos p : FOUR) {
            h.assertTrue(h.getBlockState(p).getValue(FrameBlock.ANCHORED), p + " isn't anchored with the rest");
        }
        h.succeed();
    }

    /** A computer that isn't a console, as from a mod before consoles, takes the lower left block. */
    @GameTest(template = "empty")
    public static void a_one_block_computer_takes_the_frames_lower_left_block(GameTestHelper h) {
        placeWhole(h, FrameworkBlocks.COMPUTER_FRAME.get().defaultBlockState());
        FakePlayer player = ConstructionTests.builder(h);
        ConstructionTests.use(h, player, UR, FrameworkItems.WRENCH.toStack());
        ConstructionTests.use(h, player, UR, new ItemStack(TestMachines.terminalBoard()));
        ConstructionTests.use(h, player, UR, FrameworkItems.SCREWDRIVER.toStack());
        ConstructionTests.use(h, player, UR, FrameworkItems.CABLE_COIL.toStack());
        ConstructionTests.use(h, player, UR, FrameworkItems.CONSOLE_SCREEN.toStack());
        ConstructionTests.use(h, player, UR, FrameworkItems.SCREWDRIVER.toStack());

        h.assertBlockState(
                LL,
                s -> s.is(TestMachines.terminal()) && s.getValue(FrameBlock.FACING) == Direction.SOUTH,
                () -> "the terminal isn't in the lower left block facing south");
        for (BlockPos p : List.of(LR, UL, UR)) {
            h.assertBlockState(p, BlockState::isAir, () -> p + " kept a frame block beside the terminal");
        }
        assertDropped(h, FrameworkItems.COMPUTER_FRAME.get(), 0);

        ConstructionTests.use(h, player, LL, FrameworkItems.SCREWDRIVER.toStack());
        assertFrame(h, FrameStage.SCREEN, "the terminal didn't open into the whole frame");
        h.assertTrue(frame(h).contents().board().is(TestMachines.terminalBoard()), "the frame lost the board");
        h.succeed();
    }

    // Helpers

    /** Clears the four spaces of a console at {@link #LL}, barriers included. */
    static void clear(GameTestHelper h) {
        for (BlockPos p : FOUR) {
            h.setBlock(p, Blocks.AIR);
        }
    }

    /** Places a whole console of a block's state at {@link #LL}, facing south. */
    static void placeWhole(GameTestHelper h, BlockState state) {
        clear(h);
        ConsoleBlock.placeConsole(
                h.getLevel(), h.absolutePos(LL), state.setValue(ConsoleBlock.FACING, Direction.SOUTH));
    }

    /** Places a block item on top of the block below {@link #LL}, as a player's click does. */
    private static void place(GameTestHelper h, FakePlayer player, ItemStack stack) {
        BlockPos below = h.absolutePos(LL.below());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(
                player,
                h.getLevel(),
                stack,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
    }

    private static FrameBlockEntity frame(GameTestHelper h) {
        return h.getBlockEntity(LL);
    }

    /** Asserts all four blocks are the computer frame's, in one stage, facing south. */
    private static void assertFrame(GameTestHelper h, FrameStage stage, String message) {
        assertConsole(h, FrameworkBlocks.COMPUTER_FRAME.get(), message);
        for (BlockPos p : FOUR) {
            BlockState state = h.getBlockState(p);
            h.assertTrue(state.getValue(ComputerFrameBlock.STAGE) == stage, message + " (" + p + ": " + state + ")");
        }
    }

    /** Asserts all four blocks are a console of a block, facing south, each its own part. */
    private static void assertConsole(GameTestHelper h, Block block, String message) {
        for (int i = 0; i < FOUR.size(); i++) {
            BlockPos p = FOUR.get(i);
            BlockState state = h.getBlockState(p);
            boolean ok = state.is(block)
                    && state.getValue(ConsoleBlock.FACING) == Direction.SOUTH
                    && state.getValue(ConsoleBlock.HALF) == (i >= 2 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                    && state.getValue(ConsoleBlock.SIDE) == ((i & 1) == 1 ? ConsoleSide.RIGHT : ConsoleSide.LEFT);
            h.assertTrue(ok, message + " (" + p + ": " + state + ")");
        }
    }

    private static void assertDropped(GameTestHelper h, Item item, int count) {
        int n = ConstructionTests.items(h, LL).stream()
                .map(ItemEntity::getItem)
                .filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
        h.assertTrue(n == count, "expected " + count + " " + item + " dropped, found " + n);
    }
}
