package io.github.west3436.ss13.api.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The item tags of the SS13 tools, so a block or a recipe asks for "a wrench" rather than for one
 * mod's item. The tool tags follow the {@code c:tools/<kind>} convention that NeoForge's own {@code
 * c:tools/wrench} uses, so another mod's wrench joins by adding its item to the tag, and each is in
 * {@code c:tools}.
 *
 * <p>The framework puts its own tools in them: {@code ss13_framework:multitool}, {@code wrench},
 * {@code crowbar}, {@code screwdriver}, {@code wirecutters} and {@code welder}. What a tool does to
 * a block it has a capability for ({@link Linkable}, {@link Wrenchable}, {@link Pryable}) is the
 * framework item's; a block that handles a tool itself, such as a machine frame that a wrench
 * anchors, checks its tag in its own {@code useItemOn}.
 */
public final class ToolTags {
    /** {@code c:tools/multitool}: multitools, which link devices by the {@link Linkable} buffer. */
    public static final TagKey<Item> MULTITOOLS = common("tools/multitool");

    /** {@code c:tools/wrench}: wrenches, which turn and remove {@link Wrenchable} blocks and anchor. */
    public static final TagKey<Item> WRENCHES = common("tools/wrench");

    /** {@code c:tools/crowbar}: crowbars, which pry {@link Pryable} blocks and pry parts out. */
    public static final TagKey<Item> CROWBARS = common("tools/crowbar");

    /** {@code c:tools/screwdriver}: screwdrivers, which open and close panels. */
    public static final TagKey<Item> SCREWDRIVERS = common("tools/screwdriver");

    /** {@code c:tools/wire_cutter}: wirecutters, which cut wires and cables. */
    public static final TagKey<Item> WIRE_CUTTERS = common("tools/wire_cutter");

    /** {@code c:tools/welder}: welders, which weld while lit and burn fuel ({@link WeldingTool}). */
    public static final TagKey<Item> WELDERS = common("tools/welder");

    /**
     * {@code ss13_framework:welding_protection}: what, worn on the head, shields the eyes from a
     * welder's arc, such as a welding mask or helmet. Empty in the framework; each mod adds its own.
     */
    public static final TagKey<Item> WELDING_PROTECTION = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "welding_protection"));

    private ToolTags() {}

    private static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
