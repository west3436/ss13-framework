package io.github.west3436.ss13.client.tgui;

import io.github.west3436.ss13.client.tgui.Ui.Face;
import io.github.west3436.ss13.client.tgui.Ui.Tone;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;

/**
 * What device windows share. They hold to labels, values and graphics: a status is a dot and a few
 * words, a bar stands in for a sentence, and a state that's fine says nothing.
 */
public final class DeviceParts {
    /** The column of labels in a window's rows. */
    public static final int LABEL_WIDTH = 34;

    /** A stepper's steps for a value set in tens and hundreds. */
    public static final String[] BIG_STEPS = {"−100", "−10", "+10", "+100"};

    /** A stepper's steps for a value set in ones and tens. */
    public static final String[] SMALL_STEPS = {"−10", "−1", "+1", "+10"};

    /** The redstone modes, in the order {@link #redstone} numbers them. */
    private static final List<String> REDSTONE_MODES = List.of("ignored", "high", "low");

    /** The redstone row's dot, lit while the device gets a signal, as redstone dust is. */
    private static final int SIGNAL_ON = 0xFFFF2020;

    private static final int SIGNAL_OFF = 0xFF4A1010;

    private DeviceParts() {}

    /** The kit's own text for device windows. */
    private static Component text(String key) {
        return Component.translatable("gui.ss13_framework.device." + key);
    }

    /** A row's label. */
    public static void label(Ui ui, Component text, int x, int y, int height) {
        ui.line(text, x, y, height, Ui.LABEL, Face.BODY);
    }

    /** A status: a dot in its colour, then its word. */
    public static void status(Ui ui, Component word, int colour, int x, int y, int height) {
        ui.line(Component.literal("● ").append(word), x, y, height, colour, Face.BODY);
    }

    /** The switch, ending at {@code right}. */
    public static void onOff(Ui ui, int right, int y, boolean on, Runnable toggle) {
        Component label = text(on ? "on" : "off");
        int w = ui.buttonWidth(text("off"));
        ui.button(right - w, y, w, Ui.BUTTON_HEIGHT, label, on ? Tone.SELECTED : Tone.DEFAULT, toggle);
    }

    /**
     * The redstone setting's row: its label, a button for each mode, and, unless it is ignored, a dot
     * lit while the device gets a signal. The modes are 0, ignored; 1, high, running only while it
     * gets a signal; and 2, low, running only while it gets none.
     *
     * @param mode the device's mode; anything else reads as ignored
     * @param signal whether the device got a signal at its last read
     * @param onSelect given the mode a player chose; null to grey the buttons out
     */
    public static void redstone(Ui ui, int x, int y, int w, int mode, boolean signal, IntConsumer onSelect) {
        int labelX = x + Ui.PAD;
        label(ui, text("redstone"), labelX, y, Ui.BUTTON_HEIGHT);
        int selected = mode >= 0 && mode < REDSTONE_MODES.size() ? mode : 0;
        List<Component> labels =
                REDSTONE_MODES.stream().map(word -> text("redstone." + word)).toList();
        int end = ui.choice(labelX + LABEL_WIDTH, y, labels, selected, -1, onSelect);
        if (selected != 0) {
            int size = 4;
            ui.fill(end + Ui.PAD + 1, y + (Ui.BUTTON_HEIGHT - size) / 2, size, size, signal ? SIGNAL_ON : SIGNAL_OFF);
        }
    }

    /** What a device's state row says while redstone holds it. */
    public static Component held() {
        return text("redstone.held");
    }
}
