package io.github.west3436.ss13.client.demo;

import io.github.west3436.ss13.client.tgui.DeviceParts;
import io.github.west3436.ss13.client.tgui.Readout;
import io.github.west3436.ss13.client.tgui.TguiWindow;
import io.github.west3436.ss13.client.tgui.Ui;
import io.github.west3436.ss13.client.tgui.Ui.Face;
import io.github.west3436.ss13.client.tgui.Ui.Tone;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Every control of the TGUI kit in one window with no menu behind it, as a mod builds one on {@link TguiWindow}. */
final class DemoWindow extends TguiWindow {
    private static final int WIDTH = 250;
    private static final int TABS = 12;
    private static final int TITLED = 14;
    private static final int CONTROLS = TITLED + 5 * Ui.BUTTON_ROW + 2;
    private static final int STATUS = TITLED + 3 * Ui.ROW + 2;
    private static final int BARS = TITLED + 2 * (Ui.ROW + 2) + 2;
    private static final int GAUGE = TITLED + 34;

    private static final List<Component> TAB_NAMES =
            List.of(Component.literal("Controls"), Component.literal("Readings"));
    private static final List<Component> MODES = List.of(
            Component.literal("Off"), Component.literal("Slow"), Component.literal("Fast"), Component.literal("Purge"));
    private static final double[] GAUGE_STOPS = {200, 300, 400};
    private static final int[] GAUGE_COLOURS = {Ui.GOOD, Ui.AVERAGE, Ui.BAD};

    private int tab;
    private int presses;
    private int mode;
    private boolean checked = true;
    private double kpa = 101.325;
    private boolean confirmed;

    DemoWindow() {
        super(Component.literal("TGUI kit"), WIDTH);
    }

    @Override
    protected Component windowTitle() {
        return Component.literal("TGUI kit · " + TAB_NAMES.get(tab).getString());
    }

    @Override
    protected int windowHeight() {
        int content = tab == 0
                ? TABS + Ui.PAD + CONTROLS
                : TABS + Ui.PAD + STATUS + Ui.PAD + BARS + Ui.PAD + GAUGE + 2 * (Ui.PAD + Ui.NOTICE_HEIGHT);
        return Ui.TITLE_HEIGHT + 2 * Ui.PAD + content;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y += ui.tabs(x, y, w, TAB_NAMES, tab, i -> tab = i) + Ui.PAD;
        if (tab == 0) {
            controls(ui, x, y, w);
        } else {
            readings(ui, x, y, w);
        }
    }

    private void controls(Ui ui, int x, int y, int w) {
        int row = ui.section(x, y, w, CONTROLS, Component.literal("Controls")) + 1;
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;

        DeviceParts.label(ui, Component.literal("Buttons"), labelX, row, Ui.BUTTON_HEIGHT);
        int bx = valueX;
        for (Tone tone : Tone.values()) {
            Component label = Component.literal(
                    tone.name().charAt(0) + tone.name().substring(1).toLowerCase(Locale.ROOT));
            int bw = ui.buttonWidth(label);
            ui.button(bx, row, bw, Ui.BUTTON_HEIGHT, label, tone, () -> presses++);
            bx += bw + 1;
        }
        Component off = Component.literal("Greyed");
        ui.button(bx, row, ui.buttonWidth(off), Ui.BUTTON_HEIGHT, off, Tone.DEFAULT, null);
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, Component.literal("Confirm"), labelX, row, Ui.BUTTON_HEIGHT);
        Component vent = Component.literal("Vent");
        ui.confirm(
                valueX,
                row,
                ui.buttonWidth(Component.literal("Confirm?")),
                Ui.BUTTON_HEIGHT,
                vent,
                Component.literal("Confirm?"),
                Tone.CAUTION,
                () -> confirmed = !confirmed);
        ui.lineRight(
                Component.literal(confirmed ? "Vented" : "Pressed " + presses + " times"),
                x + w - Ui.PAD,
                row,
                Ui.BUTTON_HEIGHT,
                confirmed ? Ui.BAD : Ui.DIM,
                Face.BODY);
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, Component.literal("Mode"), labelX, row, Ui.BUTTON_HEIGHT);
        ui.choice(valueX, row, MODES, mode, 3, i -> mode = i);
        row += Ui.BUTTON_ROW;

        ui.checkbox(
                labelX,
                row,
                valueW,
                Ui.BUTTON_HEIGHT,
                Component.literal("A box that ticks"),
                checked,
                () -> checked = !checked);
        row += Ui.BUTTON_ROW;

        DeviceParts.label(ui, Component.literal("Target"), labelX, row, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                row,
                valueW,
                Component.literal(Readout.pressure(kpa)),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                "kPa",
                i -> kpa = Math.max(0, kpa + Double.parseDouble(DeviceParts.BIG_STEPS[i].replace('−', '-'))),
                Component.literal("Default"),
                () -> kpa = 101.325);
    }

    private void readings(Ui ui, int x, int y, int w) {
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;

        int row = ui.section(x, y, w, STATUS, Component.literal("Status")) + 1;
        DeviceParts.label(ui, Component.literal("Good"), labelX, row, Ui.ROW);
        DeviceParts.status(ui, Component.literal("Running"), Ui.GOOD, valueX, row, Ui.ROW);
        ui.lineRight(Component.literal(Readout.temperature(293.15)), x + w - Ui.PAD, row, Ui.ROW, Ui.WHITE, Face.BODY);
        row += Ui.ROW;
        DeviceParts.label(ui, Component.literal("Average"), labelX, row, Ui.ROW);
        DeviceParts.status(ui, Component.literal("Starting"), Ui.AVERAGE, valueX, row, Ui.ROW);
        ui.tooltip(valueX, row, valueW, Ui.ROW, Component.literal("A tooltip, under the mouse"));
        row += Ui.ROW;
        DeviceParts.label(ui, Component.literal("Bad"), labelX, row, Ui.ROW);
        DeviceParts.status(ui, ui.alert("⚠", Component.literal("Overpressure")), Ui.BAD, valueX, row, Ui.ROW);
        y += STATUS + Ui.PAD;

        row = ui.section(x, y, w, BARS, Component.literal("Bars")) + 2;
        DeviceParts.label(ui, Component.literal("Charge"), labelX, row, Ui.ROW);
        ui.bar(valueX, row, valueW, Ui.ROW, 0.62, Ui.GOOD, Ui.GOOD_EDGE, Component.literal("62%"));
        row += Ui.ROW + 2;
        DeviceParts.label(ui, Component.literal("Split"), labelX, row, Ui.ROW);
        ui.split(
                valueX,
                row,
                valueW,
                Ui.ROW,
                0.78,
                Ui.BLUE,
                Ui.TEAL,
                Component.literal("N₂ " + Readout.percentNumber(0.78) + "%"),
                Component.literal("O₂ " + Readout.percentNumber(0.22) + "%"));
        y += BARS + Ui.PAD;

        row = ui.section(x, y, w, GAUGE, Component.literal("Gauge")) + 1;
        double pressure = 250;
        ui.gauge(x + 34, row + 30, 26, pressure, GAUGE_STOPS, GAUGE_COLOURS);
        ui.line(Component.literal(Readout.pressure(pressure)), x + 70, row + 4, Ui.ROW, Ui.WHITE, Face.HEADING);
        ui.arrow(x + 70, row + 22, x + 100, row + 22, Ui.TEAL);
        ui.lock(x + 108, row + 19, Ui.WHITE);
        ui.dot(x + 120, row + 20, Ui.ORANGE);
        ui.line(Component.literal("Arrow, lock, dot"), x + 128, row + 17, Ui.ROW, Ui.LABEL, Face.BODY);
        y += GAUGE + Ui.PAD;

        y += ui.notice(x, y, w, Ui.NOTICE, Ui.BLACK, Component.literal("A notice: no power.")) + Ui.PAD;
        ui.notice(x, y, w, Ui.NOTICE_DANGER, Ui.WHITE, ui.alert("⚠", Component.literal("A danger notice.")));
    }
}
