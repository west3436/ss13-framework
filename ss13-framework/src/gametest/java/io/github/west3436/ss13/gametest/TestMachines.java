package io.github.west3436.ss13.gametest;

import com.mojang.serialization.MapCodec;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.console.ConsoleBlock;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.api.construction.MachineBoard;
import io.github.west3436.ss13.api.construction.MachineBoards;
import io.github.west3436.ss13.api.construction.PartHolder;
import io.github.west3436.ss13.api.construction.PartRequirement;
import io.github.west3436.ss13.api.construction.PartTags;
import io.github.west3436.ss13.device.DeviceBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * A machine and a computer built from frames, and their boards, for the construction GameTests and
 * for trying construction in a dev run. They are registered from the GameTest source set, so they exist in every dev run but never
 * in the mod jar.
 *
 * <ul>
 *   <li>{@code ss13_framework:test_machine}: a machine frame's machine that needs one manipulator
 *       and two capacitors; its speed is the sum of its manipulators' tiers. Its board is {@code
 *       test_machine_board}.
 *   <li>{@code ss13_framework:test_console}: a computer frame's computer, a 2 x 2 {@link
 *       ConsoleBlock} standing in for Expedition's Shuttle Computer. It counts its uses and
 *       remembers who placed it. Its board is {@code test_console_board}.
 *   <li>{@code ss13_framework:test_terminal}: a computer frame's computer of one block, as a
 *       computer from before consoles is. Its board is {@code test_terminal_board}.
 * </ul>
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID)
public final class TestMachines {
    /** The test machine's board needs this many capacitors. */
    public static final int CAPACITORS = 2;

    private static Block machine;
    private static Block console;
    private static Block terminal;
    private static BlockEntityType<TestMachine> machineType;
    private static BlockEntityType<TestMachine> consoleType;
    private static BlockEntityType<TestMachine> terminalType;
    private static Item machineBoard;
    private static Item consoleBoard;
    private static Item terminalBoard;

    private TestMachines() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path);
    }

    /** The test machine's block. */
    public static Block machine() {
        return machine;
    }

    /** The test console's block. */
    public static Block console() {
        return console;
    }

    /** The one-block test computer's block. */
    public static Block terminal() {
        return terminal;
    }

    /** The one-block test computer's board. */
    public static Item terminalBoard() {
        return terminalBoard;
    }

    /** The test machine's board. */
    public static Item machineBoard() {
        return machineBoard;
    }

    /** The test console's board. */
    public static Item consoleBoard() {
        return consoleBoard;
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            machine = new TestMachineBlock(() -> machineType);
            console = new TestConsoleBlock(() -> consoleType);
            terminal = new TestMachineBlock(() -> terminalType);
            helper.register(id("test_machine"), machine);
            helper.register(id("test_console"), console);
            helper.register(id("test_terminal"), terminal);
            MachineBoards.register(
                    id("test_machine_board"),
                    MachineBoard.machine(
                            id("test_machine"),
                            PartRequirement.of(PartTags.MANIPULATORS, 1),
                            PartRequirement.of(PartTags.CAPACITORS, CAPACITORS)));
            MachineBoards.register(id("test_console_board"), MachineBoard.computer(id("test_console")));
            MachineBoards.register(id("test_terminal_board"), MachineBoard.computer(id("test_terminal")));
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            machineType = BlockEntityType.Builder.<TestMachine>of(
                            (pos, state) -> new TestMachine(machineType, pos, state), machine)
                    .build(null);
            consoleType = BlockEntityType.Builder.<TestMachine>of(
                            (pos, state) -> new TestMachine(consoleType, pos, state), console)
                    .build(null);
            terminalType = BlockEntityType.Builder.<TestMachine>of(
                            (pos, state) -> new TestMachine(terminalType, pos, state), terminal)
                    .build(null);
            helper.register(id("test_machine"), machineType);
            helper.register(id("test_console"), consoleType);
            helper.register(id("test_terminal"), terminalType);
        });
        event.register(Registries.ITEM, helper -> {
            helper.register(id("test_machine"), new BlockItem(machine, named("Test Machine")));
            helper.register(id("test_console"), new BlockItem(console, named("Test Console")));
            helper.register(id("test_terminal"), new BlockItem(terminal, named("Test Terminal")));
            machineBoard = new Item(named("Test Machine Board"));
            consoleBoard = new Item(named("Test Console Board"));
            helper.register(id("test_machine_board"), machineBoard);
            helper.register(id("test_console_board"), consoleBoard);
            terminalBoard = new Item(named("Test Terminal Board"));
            helper.register(id("test_terminal_board"), terminalBoard);
        });
    }

    private static Item.Properties named(String name) {
        return new Item.Properties().component(DataComponents.ITEM_NAME, Component.literal(name));
    }

    /** A block that faces the way it was placed, with a {@link TestMachine}. */
    public static final class TestMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {
        private final Supplier<BlockEntityType<TestMachine>> type;

        TestMachineBlock(Supplier<BlockEntityType<TestMachine>> type) {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F));
            this.type = type;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TestMachine(type.get(), pos, state);
        }
    }

    /**
     * A 2 x 2 console with a {@link TestMachine} in its lower left block, which counts the uses that
     * reach it from any block and remembers who placed it.
     */
    public static final class TestConsoleBlock extends ConsoleBlock {
        private final Supplier<BlockEntityType<TestMachine>> type;

        TestConsoleBlock(Supplier<BlockEntityType<TestMachine>> type) {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F));
            this.type = type;
        }

        @Override
        protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected BlockEntity newConsoleEntity(BlockPos pos, BlockState state) {
            return new TestMachine(type.get(), pos, state);
        }

        @Override
        protected InteractionResult useConsole(
                BlockState state, Level level, BlockPos origin, Player player, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(origin) instanceof TestMachine machine) {
                machine.uses++;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        protected void consolePlaced(
                Level level, BlockPos origin, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!level.isClientSide && placer != null && level.getBlockEntity(origin) instanceof TestMachine machine) {
                machine.placedBy = placer.getName().getString();
            }
        }
    }

    /**
     * A device whose speed is the sum of its manipulators' tiers, as a stat set from parts: each
     * device tick it does that much work while its panel is closed. It shows a machine on the
     * device base taking the construction contract as well.
     */
    public static final class TestMachine extends DeviceBlockEntity implements PartHolder {
        private InstalledParts parts = InstalledParts.EMPTY;
        private boolean panelOpen;
        private long work;
        private int uses;
        private String placedBy = "";

        TestMachine(BlockEntityType<TestMachine> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        /** The stat: the sum of its manipulators' tiers, 1 with none installed. */
        public int speed() {
            return Math.max(1, parts.totalTier(PartTags.MANIPULATORS));
        }

        /** How many times a test console's use has reached it. */
        public int uses() {
            return uses;
        }

        /** The name of the player who placed a test console, or empty. */
        public String placedBy() {
            return placedBy;
        }

        /** The work done so far, {@link #speed()} per device tick with the panel closed. */
        public long work() {
            return work;
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            if (!panelOpen) {
                work += speed();
            }
        }

        @Override
        public InstalledParts installedParts() {
            return parts;
        }

        @Override
        public void installParts(InstalledParts parts) {
            this.parts = parts;
            setChanged();
        }

        @Override
        public boolean panelOpen() {
            return panelOpen;
        }

        @Override
        public void setPanelOpen(boolean open) {
            panelOpen = open;
            setChanged();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("Parts", parts.save(registries));
            tag.putBoolean("PanelOpen", panelOpen);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            parts = InstalledParts.load(tag.get("Parts"), registries);
            panelOpen = tag.getBoolean("PanelOpen");
        }
    }
}
