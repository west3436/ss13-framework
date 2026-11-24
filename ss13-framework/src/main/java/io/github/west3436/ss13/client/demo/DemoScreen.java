package io.github.west3436.ss13.client.demo;

import io.github.west3436.ss13.client.tgui.DeviceParts;
import io.github.west3436.ss13.client.tgui.TguiScreen;
import io.github.west3436.ss13.client.tgui.Ui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A device window as a mod builds one on {@link TguiScreen}: a label in the title bar, a state, the
 * redstone and power rows, a few slots and the player's inventory. Its controls change only what
 * this window shows.
 */
final class DemoScreen extends TguiScreen<DemoMenu> {
    private static final int WIDTH = 176;

    private int redstone;
    private boolean on = true;

    DemoScreen(DemoMenu menu, Inventory inventory) {
        super(menu, inventory, Component.literal("Demo device"), WIDTH, heightWithInventory(DemoMenu.INVENTORY_Y));
    }

    @Override
    protected int eyeColour() {
        return on ? Ui.GOOD : Ui.AVERAGE;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        int sectionHeight = 14 + Ui.ROW + 2 * Ui.BUTTON_ROW + 2;
        int row = ui.section(x, y, w, sectionHeight, Component.literal("Device")) + 1;
        int labelX = x + Ui.PAD;
        DeviceParts.label(ui, Component.literal("State"), labelX, row, Ui.ROW);
        boolean held = redstone == 1;
        if (!on) {
            DeviceParts.status(ui, Component.literal("Off"), Ui.DIM, labelX + DeviceParts.LABEL_WIDTH, row, Ui.ROW);
        } else if (held) {
            DeviceParts.status(ui, DeviceParts.held(), Ui.AVERAGE, labelX + DeviceParts.LABEL_WIDTH, row, Ui.ROW);
        } else {
            DeviceParts.status(
                    ui, Component.literal("Running"), Ui.GOOD, labelX + DeviceParts.LABEL_WIDTH, row, Ui.ROW);
        }
        row += Ui.ROW;
        // The demo's signal is always off, so "high" holds the device and "low" lets it run.
        DeviceParts.redstone(ui, x, row, w, redstone, false, mode -> redstone = mode);
        row += Ui.BUTTON_ROW;
        DeviceParts.label(ui, Component.literal("Power"), labelX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(ui, x + w - Ui.PAD, row, on, () -> on = !on);

        int thingsTop = topPos + DemoMenu.THINGS_Y - 3;
        ui.section(x, thingsTop, w, 22, null);
        slots(ui, DemoMenu.INVENTORY_Y);
        // An empty slot shows a faint picture of what it takes.
        ui.ghost(leftPos + 26, topPos + DemoMenu.THINGS_Y, new ItemStack(Items.BUCKET));
        ui.line(
                Component.literal("Nothing here can be moved"),
                leftPos + 70,
                topPos + DemoMenu.THINGS_Y,
                16,
                Ui.LABEL,
                Ui.Face.BODY);
    }
}
