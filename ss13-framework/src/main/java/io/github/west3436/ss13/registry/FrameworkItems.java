package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.List;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's items: the SS13 crafting components and stock parts every mod builds from, moved
 * from ZAS³, which registers its old {@code zas_atmos:} ids as aliases of these. Each is crafted
 * from vanilla materials. They are plain items for now; machine construction makes the stock parts
 * work, by their {@link PartTags tags}.
 */
public final class FrameworkItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrameworkApi.MOD_ID);

    public static final DeferredItem<Item> CABLE_COIL = ITEMS.registerSimpleItem("cable_coil");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> ELECTRONICS = ITEMS.registerSimpleItem("electronics");
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.registerSimpleItem("machine_frame");
    public static final DeferredItem<Item> CAPACITOR = ITEMS.registerSimpleItem("capacitor");
    public static final DeferredItem<Item> ADVANCED_CAPACITOR = ITEMS.registerSimpleItem("advanced_capacitor");
    public static final DeferredItem<Item> SUPER_CAPACITOR = ITEMS.registerSimpleItem("super_capacitor");
    public static final DeferredItem<Item> SCANNING_MODULE = ITEMS.registerSimpleItem("scanning_module");
    public static final DeferredItem<Item> MICRO_MANIPULATOR = ITEMS.registerSimpleItem("micro_manipulator");
    public static final DeferredItem<Item> MICRO_LASER = ITEMS.registerSimpleItem("micro_laser");
    public static final DeferredItem<Item> CONSOLE_SCREEN = ITEMS.registerSimpleItem("console_screen");
    public static final DeferredItem<Item> IGNITER_ASSEMBLY = ITEMS.registerSimpleItem("igniter_assembly");
    public static final DeferredItem<Item> TIMER_ASSEMBLY = ITEMS.registerSimpleItem("timer_assembly");
    public static final DeferredItem<Item> ANSIBLE_CRYSTAL = ITEMS.registerSimpleItem("ansible_crystal");
    public static final DeferredItem<Item> SUBSPACE_ANSIBLE = ITEMS.registerSimpleItem("subspace_ansible");
    public static final DeferredItem<Item> SUBSPACE_TRANSMITTER = ITEMS.registerSimpleItem("subspace_transmitter");
    public static final DeferredItem<Item> BLUESPACE_CRYSTAL = ITEMS.registerSimpleItem("bluespace_crystal");
    public static final DeferredItem<Item> PLASTEEL_SHEET = ITEMS.registerSimpleItem("plasteel_sheet");
    public static final DeferredItem<Item> REINFORCED_GLASS = ITEMS.registerSimpleItem("reinforced_glass");

    private FrameworkItems() {}

    /**
     * The crafting components and stock parts, in the creative tab's order.
     *
     * @return every one of them
     */
    public static List<DeferredItem<Item>> components() {
        return List.of(
                CABLE_COIL,
                CIRCUIT_BOARD,
                ELECTRONICS,
                MACHINE_FRAME,
                CAPACITOR,
                ADVANCED_CAPACITOR,
                SUPER_CAPACITOR,
                SCANNING_MODULE,
                MICRO_MANIPULATOR,
                MICRO_LASER,
                CONSOLE_SCREEN,
                IGNITER_ASSEMBLY,
                TIMER_ASSEMBLY,
                ANSIBLE_CRYSTAL,
                SUBSPACE_ANSIBLE,
                SUBSPACE_TRANSMITTER,
                BLUESPACE_CRYSTAL,
                PLASTEEL_SHEET,
                REINFORCED_GLASS);
    }

    /**
     * Registers the items.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
