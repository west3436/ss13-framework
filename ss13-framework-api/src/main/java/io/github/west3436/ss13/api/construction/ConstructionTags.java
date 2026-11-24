package io.github.west3436.ss13.api.construction;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The item tags of the materials construction takes, so any mod's cable or screen works in a frame.
 * Tools are found by their {@link io.github.west3436.ss13.api.tool.ToolTags tags}:
 *
 * <ul>
 *   <li>a wrench ({@code c:tools/wrench}) anchors and unanchors a frame;
 *   <li>a screwdriver ({@code c:tools/screwdriver}) secures a computer's board, finishes a frame,
 *       opens a machine's panel and takes a computer's screen off;
 *   <li>a crowbar ({@code c:tools/crowbar}) pries a board, parts or a screen out of a frame, and
 *       takes apart a machine whose panel is open;
 *   <li>wirecutters ({@code c:tools/wire_cutter}) cut a frame's wiring out.
 * </ul>
 */
public final class ConstructionTags {
    /** What wires a frame: {@code ss13_framework:cable_coils}, which holds {@code cable_coil}. */
    public static final TagKey<Item> CABLE_COILS = framework("cable_coils");

    /**
     * What a computer frame takes as its screen: {@code ss13_framework:console_screens}, which holds
     * {@code console_screen}.
     */
    public static final TagKey<Item> CONSOLE_SCREENS = framework("console_screens");

    private ConstructionTags() {}

    private static TagKey<Item> framework(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path));
    }
}
