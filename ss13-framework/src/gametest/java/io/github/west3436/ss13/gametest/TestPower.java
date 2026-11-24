package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.power.AreaPower;
import io.github.west3436.ss13.api.power.DevicePower;
import io.github.west3436.ss13.api.power.EnergyStore;
import io.github.west3436.ss13.api.power.PowerChannel;
import io.github.west3436.ss13.api.power.PowerFeed;
import io.github.west3436.ss13.api.power.PowerPriority;
import io.github.west3436.ss13.api.power.PowerProvider;
import io.github.west3436.ss13.api.power.PoweredDevice;
import io.github.west3436.ss13.device.DeviceBlockEntity;
import io.github.west3436.ss13.power.AreaDevices;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Powered devices and two providers, for the GameTests of the power contract. They are registered
 * from the GameTest source set, so they exist in GameTest servers but never in the mod jar, and have
 * no item or model.
 *
 * <ul>
 *   <li>{@code ss13_framework:test_environment_device}, {@code test_equipment_device} and {@code
 *       test_lighting_device}: a device on each channel, drawing 10 FE/t, like a vent, a recharger
 *       and a light;
 *   <li>{@code ss13_framework:test_controller}: a provider like ZAS³'s area air controller, of
 *       {@link PowerProvider#BACKUP} precedence, that covers the devices linked to it and orders
 *       them about each step, whoever powers them;
 *   <li>{@code ss13_framework:test_apc}: a provider like NT Engineering's APC, of {@link
 *       PowerProvider#MAINS} precedence, that covers the devices in its area, with channels that
 *       switch off, and a buffer other blocks fill through the FE capability.
 * </ul>
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestPower {
    /** What each test device draws. */
    public static final int DRAW = 10;

    /** A provider's buffer. */
    public static final int CAPACITY = 1_000_000;

    private static Block environment;
    private static Block equipment;
    private static Block lighting;
    private static Block controller;
    private static Block apc;
    private static BlockEntityType<Part> partType;
    private static BlockEntityType<Provider> providerType;

    private TestPower() {}

    /**
     * The test device on a channel.
     *
     * @param channel the channel
     * @return its block
     */
    public static Block device(PowerChannel channel) {
        return switch (channel) {
            case ENVIRONMENT -> environment;
            case EQUIPMENT -> equipment;
            case LIGHTING -> lighting;
        };
    }

    /** The controller-like provider's block. */
    public static Block controller() {
        return controller;
    }

    /** The APC-like provider's block. */
    public static Block apc() {
        return apc;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            environment = new PartBlock(props(), PowerChannel.ENVIRONMENT);
            equipment = new PartBlock(props(), PowerChannel.EQUIPMENT);
            lighting = new PartBlock(props(), PowerChannel.LIGHTING);
            controller = new ProviderBlock(props(), false);
            apc = new ProviderBlock(props(), true);
            helper.register(TestDevices.id("test_environment_device"), environment);
            helper.register(TestDevices.id("test_equipment_device"), equipment);
            helper.register(TestDevices.id("test_lighting_device"), lighting);
            helper.register(TestDevices.id("test_controller"), controller);
            helper.register(TestDevices.id("test_apc"), apc);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            partType = BlockEntityType.Builder.of(Part::new, environment, equipment, lighting)
                    .build(null);
            providerType =
                    BlockEntityType.Builder.of(Provider::new, controller, apc).build(null);
            helper.register(TestDevices.id("test_powered_device"), partType);
            helper.register(TestDevices.id("test_provider"), providerType);
        });
    }

    /** A provider's buffer is an FE store other blocks fill, as an APC's or a controller's is. */
    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, providerType, (be, side) -> be.store);
    }

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().strength(1.0f);
    }

    private static final class PartBlock extends Block implements EntityBlock {
        final PowerChannel channel;

        PartBlock(Properties properties, PowerChannel channel) {
            super(properties);
            this.channel = channel;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Part(pos, state);
        }
    }

    private static final class ProviderBlock extends Block implements EntityBlock {
        final boolean apc;

        ProviderBlock(Properties properties, boolean apc) {
            super(properties);
            this.apc = apc;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Provider(pos, state);
        }
    }

    /** A device on its block's channel: it counts the steps it works, and keeps its last order. */
    public static final class Part extends DeviceBlockEntity implements PoweredDevice {
        /** Its power state. */
        public final DevicePower power;
        /** The steps it worked, with power. */
        public int worked;
        /** The last order a controller gave it, or -1. */
        public int order = -1;
        /** The controller that gave it. */
        public @Nullable Provider orderedBy;

        Part(BlockPos pos, BlockState state) {
            super(partType, pos, state);
            PowerChannel channel = ((PartBlock) state.getBlock()).channel;
            power = new DevicePower(channel, PowerPriority.NORMAL, () -> DRAW, this::now);
        }

        @Override
        public DevicePower power() {
            return power;
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            if (power.isPowered()) {
                worked++;
            }
        }
    }

    /**
     * A provider. As a controller it covers the devices linked to it and orders them about; as an APC
     * it covers the devices in its area while it holds the area, with channels that switch off.
     */
    public static final class Provider extends DeviceBlockEntity implements PowerProvider {
        /** Whether it is the APC; otherwise the controller. */
        public final boolean apc;
        /** Its buffer: other blocks put FE in through its capability, and none take it out. */
        public final EnergyStore store = new EnergyStore(() -> CAPACITY, true, false, this::setChanged);
        /** Its channels that are on. */
        public final Set<PowerChannel> on = EnumSet.allOf(PowerChannel.class);
        /** The controller's links, absolute positions. */
        public final List<BlockPos> links = new ArrayList<>();
        /** The APC's area. */
        public @Nullable ResourceLocation area;
        /** The devices it covered at its last step. */
        public List<PoweredDevice> covered = List.of();
        /** What became of each at its last step. */
        public List<PowerFeed.Outcome> outcomes = List.of();
        /** Its steps. */
        public int steps;

        Provider(BlockPos pos, BlockState state) {
            super(providerType, pos, state);
            apc = ((ProviderBlock) state.getBlock()).apc;
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            steps++;
            List<PoweredDevice> devices = new ArrayList<>();
            if (apc) {
                if (area != null && AreaPower.claim(level, area, this)) {
                    devices.addAll(AreaDevices.in(level, area));
                }
            } else {
                for (BlockPos link : links) {
                    if (level.getBlockEntity(link) instanceof Part part) {
                        devices.add(part);
                        part.order = steps; // control, whoever powers it
                        part.orderedBy = this;
                    }
                }
            }
            PowerFeed.Served served = PowerFeed.serve(this, devices, store.getEnergyStored());
            store.take(served.spent());
            covered = List.copyOf(devices);
            outcomes = served.outcomes();
        }

        /**
         * What became of a device at its last step.
         *
         * @param device the device
         * @return the outcome, or null if it didn't cover it
         */
        public PowerFeed.Outcome outcome(PoweredDevice device) {
            int i = covered.indexOf(device);
            return i < 0 ? null : outcomes.get(i);
        }

        @Override
        public int precedence() {
            return apc ? MAINS : BACKUP;
        }

        @Override
        public boolean providing() {
            return !isRemoved();
        }

        @Override
        public boolean channelOn(PowerChannel channel) {
            return on.contains(channel);
        }
    }
}
