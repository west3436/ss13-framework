package io.github.west3436.ss13.api.construction;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The item tags construction reads, so any mod's tool or material works in a frame: a tool by its
 * {@code c:tools/*} tag, never by its item.
 *
 * <ul>
 *   <li>{@link #WRENCH} anchors and unanchors a frame;
 *   <li>{@link #SCREWDRIVER} secures a computer's board, finishes a frame, opens a machine's panel
 *       and takes a computer's screen off;
 *   <li>{@link #CROWBAR} pries a board, parts or a screen out of a frame, and takes apart a machine
 *       whose panel is open;
 *   <li>{@link #WIRECUTTER} cuts a frame's wiring out;
 *   <li>{@link #CABLE_COILS} wires a frame, one item at a time;
 *   <li>{@link #CONSOLE_SCREENS} is a computer's screen.
 * </ul>
 */
public final class ConstructionTags {
    /** Wrenches: {@code c:tools/wrench}. */
    public static final TagKey<Item> WRENCH = common("tools/wrench");

    /** Screwdrivers: {@code c:tools/screwdriver}. */
    public static final TagKey<Item> SCREWDRIVER = common("tools/screwdriver");

    /** Crowbars: {@code c:tools/crowbar}. */
    public static final TagKey<Item> CROWBAR = common("tools/crowbar");

    /** Wirecutters: {@code c:tools/wirecutter}. */
    public static final TagKey<Item> WIRECUTTER = common("tools/wirecutter");

    /** What wires a frame: {@code ss13_framework:cable_coils}, which holds {@code cable_coil}. */
    public static final TagKey<Item> CABLE_COILS = framework("cable_coils");

    /**
     * What a computer frame takes as its screen: {@code ss13_framework:console_screens}, which holds
     * {@code console_screen}.
     */
    public static final TagKey<Item> CONSOLE_SCREENS = framework("console_screens");

    private ConstructionTags() {}

    private static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Item> framework(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path));
    }
}
