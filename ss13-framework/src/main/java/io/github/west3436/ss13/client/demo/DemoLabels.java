package io.github.west3436.ss13.client.demo;

import io.github.west3436.ss13.client.tgui.WindowLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** The demo device's label, kept on this client only: a real device's goes to its server. */
final class DemoLabels implements WindowLabels {
    private static final int MAX_LENGTH = 32;

    /** What the demo device is labelled, for as long as the client runs. */
    static String label = "";

    @Override
    public boolean takes(AbstractContainerMenu menu) {
        return menu instanceof DemoMenu;
    }

    @Override
    public String labelIn(Component title) {
        return label;
    }

    @Override
    public Component kindIn(Component title) {
        return title;
    }

    @Override
    public String clean(String typed) {
        String clean = typed.strip().replaceAll("\\s+", " ");
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH).stripTrailing() : clean;
    }

    @Override
    public int maxLength() {
        return MAX_LENGTH;
    }

    @Override
    public void send(AbstractContainerMenu menu, String sent) {
        label = sent;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("TGUI demo: label set to \"" + sent + "\""), false);
        }
    }
}
