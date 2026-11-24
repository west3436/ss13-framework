package io.github.west3436.ss13.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.tool.WeldingTool;
import io.github.west3436.ss13.registry.FrameworkItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** The capabilities the framework's own tools offer: the welder is a {@link WeldingTool}. */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID)
public final class ToolCapabilities {
    private ToolCapabilities() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(WeldingTool.ITEM, (stack, context) -> WelderItem.tool(stack), FrameworkItems.WELDER.get());
    }
}
