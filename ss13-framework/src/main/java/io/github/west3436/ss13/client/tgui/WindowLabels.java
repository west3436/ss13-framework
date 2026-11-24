package io.github.west3436.ss13.client.tgui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * How the windows of a mod's labelled devices read and send a label. A {@link TguiScreen} whose menu
 * one of these {@linkplain #takes takes} shows the label in its title bar, where a player can type
 * one; any other window shows its title. A mod registers its own with {@link TguiScreen#addLabels}
 * while its client sets up.
 *
 * <p>The label is sent as text, and the server must clean and check it as it checks a button: the
 * window only shows what {@link #clean} makes of it here.
 */
public interface WindowLabels {
    /** Whether a menu is the window of a device that takes a label. */
    boolean takes(AbstractContainerMenu menu);

    /** The label in a window's title as the server sent it, or an empty string for none. */
    String labelIn(Component title);

    /** The device's kind in a window's title, shown after its label, dimmed, or alone without one. */
    Component kindIn(Component title);

    /** Typed text as the server will keep it: an empty string clears the label. */
    String clean(String typed);

    /** The most characters a label keeps. */
    int maxLength();

    /** Sends a label typed in a menu's window to the server. */
    void send(AbstractContainerMenu menu, String label);
}
