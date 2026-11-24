package io.github.west3436.ss13.client.tgui;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.label.LabelledMenu;
import io.github.west3436.ss13.api.label.Labels;
import io.github.west3436.ss13.label.LabelPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The labels of every mod's devices that use the framework's labels: a window whose menu is a
 * {@link LabelledMenu} shows the label its title carries ({@link Labels#title}), and a label typed
 * there goes to the server as a {@link LabelPayload}, which cleans and checks it. A mod whose menus
 * are {@link LabelledMenu}s needs nothing else.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeviceWindowLabels implements WindowLabels {
    private DeviceWindowLabels() {}

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        TguiScreen.addLabels(new DeviceWindowLabels());
    }

    @Override
    public boolean takes(AbstractContainerMenu menu) {
        return menu instanceof LabelledMenu;
    }

    @Override
    public String labelIn(Component title) {
        return Labels.labelIn(title);
    }

    @Override
    public Component kindIn(Component title) {
        return Labels.kindIn(title);
    }

    @Override
    public String clean(String typed) {
        return Labels.clean(typed);
    }

    @Override
    public int maxLength() {
        return Labels.MAX_LENGTH;
    }

    @Override
    public void send(AbstractContainerMenu menu, String label) {
        PacketDistributor.sendToServer(new LabelPayload(menu.containerId, label));
    }
}
