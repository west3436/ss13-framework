package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The "SS13 Framework" creative tab, which lists every item of the framework. */
public final class FrameworkCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FrameworkApi.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ss13_framework"))
                    .icon(() -> new ItemStack(FrameworkItems.MACHINE_FRAME.get()))
                    .displayItems(FrameworkCreativeTab::fill)
                    .build());

    private FrameworkCreativeTab() {}

    /** The tab's contents, in order: the crafting components and stock parts, then the tools, then each battery empty and full. */
    private static void fill(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output out) {
        FrameworkItems.components().forEach(c -> out.accept(c.get()));
        FrameworkItems.tools().forEach(t -> out.accept(t.get()));
        FrameworkItems.batteries().forEach(b -> {
            out.accept(b.get());
            out.accept(b.get().full());
        });
    }

    /**
     * Registers the tab.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
