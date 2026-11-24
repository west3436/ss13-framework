package io.github.west3436.ss13.example;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** The example consumer's adapter, which the framework's loader starts by name. */
public final class MinecraftAdapter {
    private static volatile String startedBy;

    private MinecraftAdapter() {}

    /**
     * Called by the framework's loader.
     *
     * @param modBus the example consumer's bus
     * @param container the example consumer's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        startedBy = container.getModId();
    }

    /** The mod id the loader started this adapter for, or null if it never did. */
    public static String startedBy() {
        return startedBy;
    }
}
