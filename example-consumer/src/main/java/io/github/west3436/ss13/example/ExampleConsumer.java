package io.github.west3436.ss13.example;

import io.github.west3436.ss13.compat.Adapters;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * A mod that nests the framework, as the README tells consumers to. Besides that it has one
 * adapter, loaded through the framework's loader, which shows the loader starting a class in
 * another mod's jar.
 */
@Mod(ExampleConsumer.MOD_ID)
public final class ExampleConsumer {
    public static final String MOD_ID = "ss13_example";

    /** The mod's adapters: one, for Minecraft itself, which is always installed. */
    public static final Adapters ADAPTERS = Adapters.of(
            List.of(new Adapters.Row("minecraft", "[1.21,1.22)", "io.github.west3436.ss13.example.MinecraftAdapter")));

    public ExampleConsumer(IEventBus modBus, ModContainer container) {
        ADAPTERS.load(modBus, container);
    }
}
