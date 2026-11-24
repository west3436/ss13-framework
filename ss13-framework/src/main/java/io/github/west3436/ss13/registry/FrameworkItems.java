package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's items: the SS13 crafting components, frames and stock parts every mod builds
 * from. The first 19 moved from ZAS³, which registers its old {@code zas_atmos:} ids as aliases of
 * these. Each is crafted from vanilla materials or a lower tier. The stock parts work by their
 * {@link io.github.west3436.ss13.api.construction.PartTags tags}; the frames are blocks.
 */
public final class FrameworkItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrameworkApi.MOD_ID);

    public static final DeferredItem<Item> CABLE_COIL = ITEMS.registerSimpleItem("cable_coil");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> ELECTRONICS = ITEMS.registerSimpleItem("electronics");

    /** The machine frame, a block since machine construction; ZAS³'s alias still points here. */
    public static final DeferredItem<BlockItem> MACHINE_FRAME =
            ITEMS.registerSimpleBlockItem(FrameworkBlocks.MACHINE_FRAME);

    public static final DeferredItem<BlockItem> COMPUTER_FRAME =
            ITEMS.registerSimpleBlockItem(FrameworkBlocks.COMPUTER_FRAME);

    public static final DeferredItem<Item> CAPACITOR = ITEMS.registerSimpleItem("capacitor");
    public static final DeferredItem<Item> ADVANCED_CAPACITOR = ITEMS.registerSimpleItem("advanced_capacitor");
    public static final DeferredItem<Item> SUPER_CAPACITOR = ITEMS.registerSimpleItem("super_capacitor");
    public static final DeferredItem<Item> QUADRATIC_CAPACITOR = ITEMS.registerSimpleItem("quadratic_capacitor");
    public static final DeferredItem<Item> SCANNING_MODULE = ITEMS.registerSimpleItem("scanning_module");
    public static final DeferredItem<Item> ADVANCED_SCANNING_MODULE =
            ITEMS.registerSimpleItem("advanced_scanning_module");
    public static final DeferredItem<Item> PHASIC_SCANNING_MODULE = ITEMS.registerSimpleItem("phasic_scanning_module");
    public static final DeferredItem<Item> TRIPHASIC_SCANNING_MODULE =
            ITEMS.registerSimpleItem("triphasic_scanning_module");
    public static final DeferredItem<Item> MICRO_MANIPULATOR = ITEMS.registerSimpleItem("micro_manipulator");
    public static final DeferredItem<Item> NANO_MANIPULATOR = ITEMS.registerSimpleItem("nano_manipulator");
    public static final DeferredItem<Item> PICO_MANIPULATOR = ITEMS.registerSimpleItem("pico_manipulator");
    public static final DeferredItem<Item> FEMTO_MANIPULATOR = ITEMS.registerSimpleItem("femto_manipulator");
    public static final DeferredItem<Item> MICRO_LASER = ITEMS.registerSimpleItem("micro_laser");
    public static final DeferredItem<Item> HIGH_POWER_MICRO_LASER = ITEMS.registerSimpleItem("high_power_micro_laser");
    public static final DeferredItem<Item> ULTRA_HIGH_POWER_MICRO_LASER =
            ITEMS.registerSimpleItem("ultra_high_power_micro_laser");
    public static final DeferredItem<Item> QUAD_ULTRA_MICRO_LASER = ITEMS.registerSimpleItem("quad_ultra_micro_laser");
    public static final DeferredItem<Item> MATTER_BIN = ITEMS.registerSimpleItem("matter_bin");
    public static final DeferredItem<Item> ADVANCED_MATTER_BIN = ITEMS.registerSimpleItem("advanced_matter_bin");
    public static final DeferredItem<Item> SUPER_MATTER_BIN = ITEMS.registerSimpleItem("super_matter_bin");
    public static final DeferredItem<Item> BLUESPACE_MATTER_BIN = ITEMS.registerSimpleItem("bluespace_matter_bin");
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
     * The crafting components, frames and stock parts, in the creative tab's order: each kind of
     * part from tier 1 to tier 4.
     *
     * @return every one of them
     */
    public static List<DeferredItem<? extends Item>> components() {
        return List.of(
                CABLE_COIL,
                CIRCUIT_BOARD,
                ELECTRONICS,
                MACHINE_FRAME,
                COMPUTER_FRAME,
                CAPACITOR,
                ADVANCED_CAPACITOR,
                SUPER_CAPACITOR,
                QUADRATIC_CAPACITOR,
                SCANNING_MODULE,
                ADVANCED_SCANNING_MODULE,
                PHASIC_SCANNING_MODULE,
                TRIPHASIC_SCANNING_MODULE,
                MICRO_MANIPULATOR,
                NANO_MANIPULATOR,
                PICO_MANIPULATOR,
                FEMTO_MANIPULATOR,
                MICRO_LASER,
                HIGH_POWER_MICRO_LASER,
                ULTRA_HIGH_POWER_MICRO_LASER,
                QUAD_ULTRA_MICRO_LASER,
                MATTER_BIN,
                ADVANCED_MATTER_BIN,
                SUPER_MATTER_BIN,
                BLUESPACE_MATTER_BIN,
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
