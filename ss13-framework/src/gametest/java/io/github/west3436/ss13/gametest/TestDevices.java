package io.github.west3436.ss13.gametest;

import com.mojang.serialization.MapCodec;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.api.label.LabelledPart;
import io.github.west3436.ss13.device.BlockTurns;
import io.github.west3436.ss13.device.DeviceBlockEntity;
import io.github.west3436.ss13.device.RedstoneControl;
import io.github.west3436.ss13.device.RedstoneControlled;
import io.github.west3436.ss13.device.WallPanelBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Blocks with no gas in them, for GameTests of the device base and labels. They are registered from
 * the GameTest source set, so they exist in GameTest servers but never in the mod jar, and have no
 * item or model.
 *
 * <ul>
 *   <li>{@code ss13_framework:test_device}: a device that takes a label and a redstone setting, and
 *       records its steps;
 *   <li>{@code ss13_framework:test_plain_device}: a device that takes neither;
 *   <li>{@code ss13_framework:test_part}: the upper block of a device of two, whose label the block
 *       below keeps;
 *   <li>{@code ss13_framework:test_panel}: a wall panel;
 *   <li>{@code ss13_framework:test_sides}: a block that faces a way and joins on each face.
 * </ul>
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestDevices {
    private static Block device;
    private static Block plainDevice;
    private static Block part;
    private static Block panel;
    private static Block sides;
    private static BlockEntityType<Device> deviceType;
    private static BlockEntityType<PlainDevice> plainType;

    private TestDevices() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path);
    }

    /** The labelled device's block. */
    public static Block device() {
        return device;
    }

    /** The plain device's block. */
    public static Block plainDevice() {
        return plainDevice;
    }

    /** The upper block of a device of two. */
    public static Block part() {
        return part;
    }

    /** The wall panel. */
    public static Block panel() {
        return panel;
    }

    /** The block that faces a way and joins on each face. */
    public static Block sides() {
        return sides;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            device = new DeviceBlock(props(), true);
            plainDevice = new DeviceBlock(props(), false);
            part = new PartBlock(props());
            panel = new PanelBlock(props());
            sides = new SidesBlock(props());
            helper.register(id("test_device"), device);
            helper.register(id("test_plain_device"), plainDevice);
            helper.register(id("test_part"), part);
            helper.register(id("test_panel"), panel);
            helper.register(id("test_sides"), sides);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            deviceType = BlockEntityType.Builder.of(Device::new, device).build(null);
            plainType =
                    BlockEntityType.Builder.of(PlainDevice::new, plainDevice).build(null);
            helper.register(id("test_device"), deviceType);
            helper.register(id("test_plain_device"), plainType);
        });
    }

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().strength(1.0f);
    }

    private static final class DeviceBlock extends Block implements EntityBlock {
        private final boolean labelled;

        DeviceBlock(Properties properties, boolean labelled) {
            super(properties);
            this.labelled = labelled;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return labelled ? new Device(pos, state) : new PlainDevice(pos, state);
        }
    }

    /** The labelled device: it records each step's game tick and thread, and works as it is told. */
    public static final class Device extends DeviceBlockEntity implements Labelled, RedstoneControlled {
        /** The game ticks it stepped at. */
        public final List<Long> steps = new ArrayList<>();
        /** The game ticks it ran at, with redstone letting it. */
        public final List<Long> ran = new ArrayList<>();
        /** The threads it stepped on. */
        public final List<Thread> threads = new ArrayList<>();
        /** Whether it is ready to step. */
        public boolean ready = true;
        /** The work it reports each step it runs. */
        public double work;
        /** Whether it says it has stopped at each step. */
        public boolean stopping;

        private final RedstoneControl redstone = new RedstoneControl(this);

        Device(BlockPos pos, BlockState state) {
            super(deviceType, pos, state);
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            steps.add(now());
            threads.add(Thread.currentThread());
            if (!redstone.read()) {
                reportStopped();
                return;
            }
            ran.add(now());
            reportActivity(work);
            if (stopping) {
                reportStopped();
            }
        }

        @Override
        public boolean readyToStep() {
            return ready;
        }

        @Override
        public RedstoneControl redstone() {
            return redstone;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            redstone.save(tag);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            redstone.load(tag);
        }
    }

    /** A device that takes no label: it only counts its steps. */
    public static final class PlainDevice extends DeviceBlockEntity {
        /** How many times it stepped. */
        public int steps;

        PlainDevice(BlockPos pos, BlockState state) {
            super(plainType, pos, state);
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            steps++;
        }
    }

    /** The upper block of a device of two: the device below keeps its label. */
    private static final class PartBlock extends Block implements LabelledPart {
        PartBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockPos labelledAt(BlockState state, BlockPos pos) {
            return pos.below();
        }
    }

    /** A panel 6 pixels wide and 8 high in the middle of its wall. */
    private static final class PanelBlock extends WallPanelBlock {
        static final MapCodec<PanelBlock> CODEC = simpleCodec(PanelBlock::new);

        PanelBlock(Properties properties) {
            super(properties, 5, 4, 11, 12);
        }

        @Override
        protected MapCodec<PanelBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
    }

    /** Faces any way, and joins on each face as a pipe does. */
    private static final class SidesBlock extends Block implements BlockTurns.Sides {
        SidesBlock(Properties properties) {
            super(properties);
            BlockState state = stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH);
            for (BooleanProperty side : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
                state = state.setValue(side, false);
            }
            registerDefaultState(state);
        }

        @Override
        public Map<Direction, BooleanProperty> sideProperties() {
            return PipeBlock.PROPERTY_BY_DIRECTION;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.FACING);
            PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
        }
    }
}
