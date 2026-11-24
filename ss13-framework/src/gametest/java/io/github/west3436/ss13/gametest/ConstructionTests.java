package io.github.west3436.ss13.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.api.construction.MachineBoards;
import io.github.west3436.ss13.api.construction.PartHolder;
import io.github.west3436.ss13.api.construction.PartTags;
import io.github.west3436.ss13.construction.ComputerFrameBlock;
import io.github.west3436.ss13.construction.FrameBlock;
import io.github.west3436.ss13.construction.FrameBlockEntity;
import io.github.west3436.ss13.construction.FrameStage;
import io.github.west3436.ss13.construction.MachineFrameBlock;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import io.github.west3436.ss13.registry.FrameworkComponents;
import io.github.west3436.ss13.registry.FrameworkItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Machine construction, played through as a player would: tools and parts used on frames and
 * machines through the server's own use path, so the frame blocks and the construction event
 * hooks both run.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionTests {
    private static final BlockPos AT = new BlockPos(0, 1, 0);

    private ConstructionTests() {}

    /** The issue's "done when": a test machine from frame, board and parts, upgraded in place. */
    @GameTest(template = "empty")
    public static void machine_frame_builds_a_machine_whose_speed_follows_its_manipulators_tier(GameTestHelper h) {
        h.setBlock(
                AT,
                FrameworkBlocks.MACHINE_FRAME.get().defaultBlockState().setValue(FrameBlock.FACING, Direction.EAST));
        FakePlayer player = builder(h);

        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.EMPTY, "an unanchored frame took cable");
        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        h.assertTrue(h.getBlockState(AT).getValue(FrameBlock.ANCHORED), "the wrench didn't anchor the frame");
        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.WIRED, "the cable didn't wire the frame");
        use(h, player, AT, new ItemStack(TestMachines.consoleBoard()));
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.WIRED, "a machine frame took a computer's board");
        use(h, player, AT, new ItemStack(TestMachines.machineBoard()));
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.BOARD, "the frame didn't take its board");

        use(h, player, AT, FrameworkItems.MICRO_LASER.toStack());
        h.assertTrue(frame(h).contents().parts().isEmpty(), "the frame took a laser its board doesn't need");
        use(h, player, AT, FrameworkItems.MICRO_MANIPULATOR.toStack());
        use(h, player, AT, FrameworkItems.FEMTO_MANIPULATOR.toStack());
        h.assertTrue(frame(h).contents().parts().size() == 1, "the frame took a second manipulator");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.BOARD, "the frame closed without its capacitors");
        ItemStack capacitors = FrameworkItems.CAPACITOR.toStack().copyWithCount(TestMachines.CAPACITORS);
        for (int i = 0; i < TestMachines.CAPACITORS; i++) {
            use(h, player, AT, capacitors);
        }
        h.assertTrue(capacitors.isEmpty(), "the capacitors weren't used up");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());

        h.assertBlockState(
                AT,
                s -> s.is(TestMachines.machine())
                        && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                () -> "the frame didn't become the machine facing east");
        TestMachines.TestMachine machine = h.getBlockEntity(AT);
        h.assertTrue(machine.speed() == 1, "a tier-1 manipulator gives speed " + machine.speed());
        h.assertTrue(
                machine.installedParts().board().is(TestMachines.machineBoard()), "the machine doesn't hold its board");
        h.assertTrue(machine.installedParts().parts().size() == 3, "the machine doesn't hold its three parts");

        use(h, player, AT, FrameworkItems.NANO_MANIPULATOR.toStack());
        h.assertTrue(machine.speed() == 1, "a part went in with the panel closed");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());
        h.assertTrue(machine.panelOpen(), "the screwdriver didn't open the panel");
        use(h, player, AT, FrameworkItems.NANO_MANIPULATOR.toStack());
        h.assertTrue(machine.speed() == 2, "swapping in a tier-2 manipulator gives speed " + machine.speed());
        h.assertTrue(
                player.getInventory().contains(FrameworkItems.MICRO_MANIPULATOR.toStack()),
                "the swapped-out manipulator didn't go to the player");
        use(h, player, AT, FrameworkItems.MICRO_MANIPULATOR.toStack());
        h.assertTrue(machine.speed() == 2, "a worse manipulator replaced a better one");

        use(h, player, AT, FrameworkItems.CROWBAR.toStack());
        h.assertBlockState(
                AT,
                s -> s.is(FrameworkBlocks.MACHINE_FRAME.get())
                        && s.getValue(MachineFrameBlock.STAGE) == FrameStage.WIRED
                        && s.getValue(FrameBlock.ANCHORED)
                        && s.getValue(FrameBlock.FACING) == Direction.EAST,
                () -> "the crowbar didn't leave a wired frame facing east");
        assertDropped(h, TestMachines.machineBoard(), 1);
        assertDropped(h, FrameworkItems.NANO_MANIPULATOR.get(), 1);
        assertDropped(h, FrameworkItems.CAPACITOR.get(), TestMachines.CAPACITORS);
        h.succeed();
    }

    /** The Phase B gate's shape: a computer, as Expedition's Shuttle Computer, from frame and board. */
    @GameTest(template = "empty")
    public static void computer_frame_builds_a_computer_from_its_board(GameTestHelper h) {
        h.setBlock(
                AT,
                FrameworkBlocks.COMPUTER_FRAME.get().defaultBlockState().setValue(FrameBlock.FACING, Direction.SOUTH));
        FakePlayer player = builder(h);

        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        use(h, player, AT, new ItemStack(TestMachines.machineBoard()));
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.EMPTY, "a computer frame took a machine's board");
        use(h, player, AT, new ItemStack(TestMachines.consoleBoard()));
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.BOARD, "the frame didn't take its board");
        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.BOARD, "the frame took cable with its board loose");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.SECURED, "the screwdriver didn't secure the board");
        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.WIRED, "the cable didn't wire the frame");
        use(h, player, AT, FrameworkItems.CONSOLE_SCREEN.toStack());
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.SCREEN, "the frame didn't take its screen");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());

        h.assertBlockState(
                AT,
                s -> s.is(TestMachines.console())
                        && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH,
                () -> "the frame didn't become the console facing south");
        PartHolder console = h.getBlockEntity(AT);
        h.assertTrue(console.installedParts().board().is(TestMachines.consoleBoard()), "the console lost its board");

        use(h, player, AT, FrameworkItems.CROWBAR.toStack());
        h.assertBlockState(AT, s -> s.is(TestMachines.console()), () -> "a crowbar opened a computer");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());
        assertStage(h, ComputerFrameBlock.STAGE, FrameStage.SCREEN, "the screwdriver didn't take the screen off");
        h.assertTrue(frame(h).contents().board().is(TestMachines.consoleBoard()), "the frame lost the board");
        use(h, player, AT, FrameworkItems.SCREWDRIVER.toStack());
        h.assertBlockState(AT, s -> s.is(TestMachines.console()), () -> "the frame didn't close again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breaking_a_frame_drops_everything_in_it(GameTestHelper h) {
        h.setBlock(AT, FrameworkBlocks.MACHINE_FRAME.get());
        FakePlayer player = builder(h);
        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        use(h, player, AT, new ItemStack(TestMachines.machineBoard()));
        use(h, player, AT, FrameworkItems.PICO_MANIPULATOR.toStack());
        h.getLevel().destroyBlock(h.absolutePos(AT), true);
        assertDropped(h, FrameworkItems.MACHINE_FRAME.get(), 1);
        assertDropped(h, FrameworkItems.CABLE_COIL.get(), 1);
        assertDropped(h, TestMachines.machineBoard(), 1);
        assertDropped(h, FrameworkItems.PICO_MANIPULATOR.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wirecutters_and_crowbar_take_a_frame_back_apart(GameTestHelper h) {
        h.setBlock(AT, FrameworkBlocks.MACHINE_FRAME.get());
        FakePlayer player = builder(h);
        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        use(h, player, AT, FrameworkItems.CABLE_COIL.toStack());
        use(h, player, AT, new ItemStack(TestMachines.machineBoard()));
        use(h, player, AT, FrameworkItems.MICRO_MANIPULATOR.toStack());
        use(h, player, AT, FrameworkItems.CROWBAR.toStack());
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.WIRED, "the crowbar didn't pry the board out");
        h.assertTrue(frame(h).contents().isEmpty(), "the frame kept its board or parts");
        assertDropped(h, TestMachines.machineBoard(), 1);
        assertDropped(h, FrameworkItems.MICRO_MANIPULATOR.get(), 1);
        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        h.assertTrue(h.getBlockState(AT).getValue(FrameBlock.ANCHORED), "a wired frame was unanchored");
        use(h, player, AT, FrameworkItems.WIRECUTTERS.toStack());
        assertStage(h, MachineFrameBlock.STAGE, FrameStage.EMPTY, "the wirecutters didn't cut the wiring");
        assertDropped(h, FrameworkItems.CABLE_COIL.get(), 1);
        use(h, player, AT, FrameworkItems.WRENCH.toStack());
        h.assertFalse(h.getBlockState(AT).getValue(FrameBlock.ANCHORED), "the wrench didn't unanchor the frame");
        h.succeed();
    }

    /** The plain recipe's route: a placed machine holds stock parts, and a mined one keeps its own. */
    @GameTest(template = "empty")
    public static void a_placed_machine_holds_stock_parts_and_a_mined_one_keeps_its_upgrades(GameTestHelper h) {
        FakePlayer player = builder(h);
        BlockPos first = new BlockPos(0, 2, 0);
        BlockPos second = new BlockPos(1, 2, 0);
        h.setBlock(first.below(), Blocks.STONE);
        h.setBlock(second.below(), Blocks.STONE);

        place(h, player, first, new ItemStack(TestMachines.machine()));
        TestMachines.TestMachine placed = h.getBlockEntity(first);
        InstalledParts stock = placed.installedParts();
        h.assertTrue(stock.board().is(TestMachines.machineBoard()), "a placed machine has no board");
        h.assertTrue(
                stock.count(PartTags.MANIPULATORS) == 1 && stock.count(PartTags.CAPACITORS) == TestMachines.CAPACITORS,
                "a placed machine doesn't hold its stock parts");
        h.assertTrue(placed.speed() == 1, "a placed machine's speed is " + placed.speed());

        use(h, player, first, FrameworkItems.SCREWDRIVER.toStack());
        use(h, player, first, FrameworkItems.FEMTO_MANIPULATOR.toStack());
        h.assertTrue(placed.speed() == 4, "a tier-4 manipulator gives speed " + placed.speed());

        h.getLevel().destroyBlock(h.absolutePos(first), true, player);
        ItemEntity mined = dropped(h, first, TestMachines.machine().asItem());
        InstalledParts carried = mined.getItem().get(FrameworkComponents.INSTALLED_PARTS.get());
        h.assertTrue(carried != null, "the mined machine's item doesn't carry its parts");
        place(h, player, second, mined.getItem().copy());
        TestMachines.TestMachine again = h.getBlockEntity(second);
        h.assertTrue(again.speed() == 4, "the machine placed again has speed " + again.speed());

        place(h, player, first, new ItemStack(TestMachines.machine()));
        mined.discard();
        h.getLevel().destroyBlock(h.absolutePos(first), true, player);
        ItemEntity plain = dropped(h, first, TestMachines.machine().asItem());
        h.assertFalse(
                plain.getItem().has(FrameworkComponents.INSTALLED_PARTS.get()),
                "a stock machine's item carries its parts, so it won't stack with crafted ones");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void boards_are_registered_by_item_id(GameTestHelper h) {
        h.assertTrue(
                MachineBoards.of(new ItemStack(TestMachines.machineBoard())).isPresent(),
                "the test board isn't a board");
        h.assertTrue(
                MachineBoards.boardFor(TestMachines.id("test_console"))
                        .equals(Optional.of(TestMachines.id("test_console_board"))),
                "the console's board isn't found from the console");
        h.assertFalse(
                MachineBoards.of(FrameworkItems.CIRCUIT_BOARD.toStack()).isPresent(), "a blank board builds something");
        h.succeed();
    }

    // Helpers

    private static FakePlayer builder(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ss13_builder"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    /** Uses an item on a block through the server's own use path, as a player's click does. */
    private static void use(GameTestHelper h, FakePlayer player, BlockPos pos, ItemStack stack) {
        BlockPos at = h.absolutePos(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(
                player,
                h.getLevel(),
                stack,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    /** Places a block item on top of the block below a position. */
    private static void place(GameTestHelper h, FakePlayer player, BlockPos pos, ItemStack stack) {
        BlockPos below = h.absolutePos(pos.below());
        // A GameTest's structure is walled in barriers, so clear the spot first.
        h.setBlock(pos, Blocks.AIR);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = player.gameMode.useItemOn(
                player,
                h.getLevel(),
                stack,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
        h.assertBlockState(
                pos,
                s -> s.is(TestMachines.machine()),
                () -> "placing gave " + result + " and left " + h.getBlockState(pos));
    }

    private static FrameBlockEntity frame(GameTestHelper h) {
        return h.getBlockEntity(AT);
    }

    private static void assertStage(
            GameTestHelper h, EnumProperty<FrameStage> stage, FrameStage expected, String message) {
        BlockState state = h.getBlockState(AT);
        h.assertTrue(state.hasProperty(stage) && state.getValue(stage) == expected, message + " (" + state + ")");
    }

    private static List<ItemEntity> items(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pos)).inflate(1.5));
    }

    private static ItemEntity dropped(GameTestHelper h, BlockPos pos, Item item) {
        return items(h, pos).stream()
                .filter(e -> e.getItem().is(item))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no " + item + " dropped"));
    }

    private static void assertDropped(GameTestHelper h, Item item, int count) {
        int n = items(h, AT).stream()
                .filter(e -> e.getItem().is(item))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
        h.assertTrue(n == count, "expected " + count + " " + item + " dropped, found " + n);
    }
}
