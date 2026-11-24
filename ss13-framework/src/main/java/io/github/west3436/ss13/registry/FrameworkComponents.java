package io.github.west3436.ss13.registry;

import com.mojang.serialization.Codec;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.device.ItemData;
import java.util.function.Supplier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The data components the framework's items keep on their stacks, and the item capabilities they back. */
public final class FrameworkComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FrameworkApi.MOD_ID);

    /** {@code link_source}: the multitool's buffer, the link source it has stored. */
    public static final Supplier<DataComponentType<GlobalPos>> LINK_SOURCE = COMPONENTS.registerComponentType(
            "link_source", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** {@code fuel}: the fuel a welder holds, in units; a welder without it is full. */
    public static final Supplier<DataComponentType<Integer>> FUEL = COMPONENTS.registerComponentType(
            "fuel", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** {@code lit}: whether a welder is lit; a welder without it is out. */
    public static final Supplier<DataComponentType<Boolean>> LIT = COMPONENTS.registerComponentType(
            "lit", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** {@code installed_parts}: the board and parts a mined machine's item carries back to its place. */
    public static final Supplier<DataComponentType<InstalledParts>> INSTALLED_PARTS = COMPONENTS.registerComponentType(
            "installed_parts",
            b -> b.persistent(InstalledParts.CODEC).networkSynchronized(InstalledParts.STREAM_CODEC));

    /**
     * {@code energy}: the FE a battery holds, read and written through its FE item capability, and
     * saved with a format version ({@link ItemData}). ZAS³'s {@code zas_atmos:energy} is an alias of
     * it.
     */
    public static final Supplier<DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType(
            "energy",
            b -> b.persistent(ItemData.versioned(ExtraCodecs.NON_NEGATIVE_INT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private FrameworkComponents() {}

    /**
     * Registers the components and the batteries' FE item capability.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        modBus.addListener(FrameworkComponents::capabilities);
    }

    /** Each battery is an FE item: it takes and gives up to its capacity at once. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        FrameworkItems.batteries().forEach(battery -> {
            int capacity = battery.get().capacity();
            event.registerItem(
                    Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new ComponentEnergyStorage(stack, ENERGY.get(), capacity, capacity, capacity),
                    battery.get());
        });
    }
}
