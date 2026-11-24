package io.github.west3436.ss13.registry;

import com.mojang.serialization.Codec;
import io.github.west3436.ss13.api.FrameworkApi;
import java.util.function.Supplier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The data components the framework's items keep on their stacks. */
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

    private FrameworkComponents() {}

    /**
     * Registers the components.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
