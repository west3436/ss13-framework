package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.device.ItemData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's item data components, each saved with a format version ({@link ItemData}), and
 * the item capabilities they back.
 */
public final class FrameworkComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FrameworkApi.MOD_ID);

    /**
     * The FE a battery holds, {@code ss13_framework:energy}, read and written through its FE item
     * capability. ZAS³'s {@code zas_atmos:energy} is an alias of it.
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
