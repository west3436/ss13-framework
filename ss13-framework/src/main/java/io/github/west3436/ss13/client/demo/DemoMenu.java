package io.github.west3436.ss13.client.demo;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The demo device's menu. It lives on the client alone, with an id no server window has, so the
 * game drops a click in it before it reaches an item or the server: nothing in it can be moved.
 */
final class DemoMenu extends AbstractContainerMenu {
    /** No window the server opens has this id. */
    private static final int ID = Integer.MIN_VALUE;

    static final int THINGS_Y = 75;
    static final int INVENTORY_Y = 100;

    DemoMenu(Inventory inventory) {
        super(null, ID);
        SimpleContainer things = new SimpleContainer(
                new ItemStack(Items.IRON_INGOT, 16), ItemStack.EMPTY, new ItemStack(Items.REDSTONE, 4));
        for (int i = 0; i < things.getContainerSize(); i++) {
            addSlot(new Slot(things, i, 8 + i * 18, THINGS_Y) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
