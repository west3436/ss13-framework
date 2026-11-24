package io.github.west3436.ss13.client;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.power.BatteryItem;
import io.github.west3436.ss13.registry.FrameworkItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * A battery shows its charge: {@code ss13_framework:charge} is 0 empty, 0.5 part charged and 1 full,
 * which each battery's item model maps to its {@code _partial} and {@code _full} models.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BatteryModels {
    /** The item property, {@code ss13_framework:charge}. */
    public static final ResourceLocation CHARGE = ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "charge");

    private BatteryModels() {}

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> FrameworkItems.batteries()
                .forEach(b -> ItemProperties.register(b.get(), CHARGE, (stack, level, entity, seed) -> {
                    float charge = BatteryItem.charge(stack);
                    return charge <= 0 ? 0 : charge >= 1 ? 1 : 0.5f;
                })));
    }
}
