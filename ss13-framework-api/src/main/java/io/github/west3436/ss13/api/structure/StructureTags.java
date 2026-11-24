package io.github.west3436.ss13.api.structure;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The block tags that say what a station is built of, so a tool or a machine finds walls and windows
 * from any mod without reading its internals: the framework's welder and NT Engineering's RCD take
 * them apart ({@link Dismantling}), whoever made them. The framework has no walls or windows of its
 * own, so each tag is empty here and each mod fills it with its blocks:
 *
 * <ul>
 *   <li>ZAS³: its station walls, reinforced or not, and their pipe variants, and its reinforced
 *       glass.
 *   <li>Expedition: its ship hulls.
 *   <li>Any other mod: its own walls and windows.
 * </ul>
 */
public final class StructureTags {
    /**
     * {@code ss13_framework:walls}: every station wall, reinforced included. It takes in {@link
     * #REINFORCED_WALLS}, so a mod adds a reinforced wall to that tag alone. A wall with no {@link
     * Dismantlable} comes apart into its own loot.
     */
    public static final TagKey<Block> WALLS = tag("walls");

    /**
     * {@code ss13_framework:reinforced_walls}: the walls that take a reinforced wall's longer steps
     * to take apart, such as ZAS³'s reinforced station wall.
     */
    public static final TagKey<Block> REINFORCED_WALLS = tag("reinforced_walls");

    /**
     * {@code ss13_framework:windows}: every station window, such as ZAS³'s reinforced glass. A
     * window with no {@link Dismantlable} comes apart into its own loot.
     */
    public static final TagKey<Block> WINDOWS = tag("windows");

    private StructureTags() {}

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path));
    }
}
