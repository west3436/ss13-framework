package io.github.west3436.ss13.client;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.registry.FrameworkItems;
import io.github.west3436.ss13.tool.WelderItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The tools' model properties: {@code ss13_framework:lit} is 1 for a lit welder, which shows its flame. */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, value = Dist.CLIENT)
public final class ToolModels {
    private ToolModels() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                FrameworkItems.WELDER.get(),
                ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "lit"),
                (stack, level, entity, seed) -> WelderItem.isLit(stack) ? 1.0F : 0.0F));
    }
}
