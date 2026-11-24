package io.github.west3436.ss13.client.demo;

import com.mojang.brigadier.CommandDispatcher;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.client.tgui.TguiScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * A look at the TGUI kit in a development client, with nothing else installed: the client command
 * {@code /ss13_framework tgui window} opens a window of every control, and {@code /ss13_framework
 * tgui device} a device window with a label, slots and the player's inventory. Neither talks to the
 * server. A released jar registers neither.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, value = Dist.CLIENT)
public final class TguiDemo {
    private static boolean labelsAdded;

    private TguiDemo() {}

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(FrameworkApi.MOD_ID)
                .then(Commands.literal("tgui")
                        .then(Commands.literal("window").executes(context -> open(DemoWindow::new)))
                        .then(Commands.literal("device").executes(context -> open(TguiDemo::device)))));
    }

    private static Screen device() {
        if (!labelsAdded) {
            labelsAdded = true;
            TguiScreen.addLabels(new DemoLabels());
        }
        Minecraft minecraft = Minecraft.getInstance();
        return new DemoScreen(new DemoMenu(minecraft.player.getInventory()), minecraft.player.getInventory());
    }

    /** Opens a screen once the chat that ran the command has closed. */
    private static int open(Supplier<Screen> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> minecraft.setScreen(screen.get()));
        return 1;
    }
}
