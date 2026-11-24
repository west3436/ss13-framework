package io.github.west3436.ss13.api.area;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A level's stored areas: areas drawn block by block and saved with the level, such as rooms a
 * player marks out with blueprints or an admin with {@code /ss13 area}. {@link
 * Areas#store(ServerLevel)} gives a level's store. Its areas answer {@link Areas#at} as the provider
 * {@link Areas#STORED}, with priority {@link Areas#ROOM}.
 *
 * <p>Each block is in at most one stored area: {@linkplain #assign assigning} a block takes it from
 * the area it was in. An area is first {@linkplain #define defined}, then given blocks; it may have
 * none. Its blocks needn't touch, and they may be in chunks that aren't loaded.
 *
 * <p>Every change fires an {@link AreasChangedEvent} in the level, over the blocks whose area
 * changed, before the method returns; a call that changes nothing fires none. When blocks move, the
 * areas wholly inside them go too: see {@link Areas#moved}.
 *
 * <p>Thread: server thread only. Every method throws {@link IllegalStateException} when called from
 * another.
 */
public interface AreaStore {
    /** The most blocks one call may assign or clear: a box of 128 by 128 by 64. */
    int MAX_BLOCKS_PER_CALL = 1 << 20;

    /** The most areas a level may store. */
    int MAX_AREAS = Short.MAX_VALUE;

    /**
     * The level this store belongs to.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * The stored area a block is in.
     *
     * @param pos the block
     * @return its area, or empty if it is in none
     */
    Optional<Area> at(BlockPos pos);

    /**
     * A stored area.
     *
     * @param id its id
     * @return the area, or empty if this level has none with that id
     */
    Optional<Area> get(ResourceLocation id);

    /**
     * Every stored area of this level, in no particular order.
     *
     * @return a copy of the list
     */
    List<Area> areas();

    /**
     * Defines an area, with no blocks yet.
     *
     * @param id its id, in the namespace of the mod that makes it, unused in this level
     * @param name what players read: not blank, at most {@link Area#MAX_NAME_LENGTH} characters
     * @return the area
     * @throws IllegalArgumentException if this level already has an area with that id, or the name
     *     is blank or too long
     * @throws IllegalStateException if the level already has {@link #MAX_AREAS} areas
     */
    Area define(ResourceLocation id, String name);

    /**
     * Renames an area. Its blocks and its id stay.
     *
     * @param id its id
     * @param name its new name: not blank, at most {@link Area#MAX_NAME_LENGTH} characters
     * @return the area with its new name
     * @throws IllegalArgumentException if this level has no area with that id, or the name is blank
     *     or too long
     */
    Area rename(ResourceLocation id, String name);

    /**
     * Removes an area: its blocks are in no stored area any more.
     *
     * @param id its id
     * @return whether there was such an area
     */
    boolean remove(ResourceLocation id);

    /**
     * Puts every block of a box in an area, taking each from the area it was in.
     *
     * @param id the area's id
     * @param box the blocks, both corners inside
     * @return how many blocks changed area
     * @throws IllegalArgumentException if this level has no area with that id, or the box holds more
     *     than {@link #MAX_BLOCKS_PER_CALL} blocks
     */
    int assign(ResourceLocation id, BoundingBox box);

    /**
     * Puts blocks in an area, taking each from the area it was in: for a room found by flood fill.
     *
     * @param id the area's id
     * @param blocks the blocks; a block named twice counts once
     * @return how many blocks changed area
     * @throws IllegalArgumentException if this level has no area with that id, or there are more than
     *     {@link #MAX_BLOCKS_PER_CALL} blocks
     */
    int assign(ResourceLocation id, Iterable<BlockPos> blocks);

    /**
     * Takes every block of a box out of whichever stored area it is in. The areas stay defined,
     * even with no blocks left.
     *
     * @param box the blocks, both corners inside
     * @return how many blocks were in an area
     * @throws IllegalArgumentException if the box holds more than {@link #MAX_BLOCKS_PER_CALL}
     *     blocks
     */
    int clear(BoundingBox box);

    /**
     * How many blocks an area has.
     *
     * @param id the area's id
     * @return its blocks; 0 for an area with none, or no such area
     */
    int size(ResourceLocation id);

    /**
     * The smallest box that holds an area's blocks. It looks at every block this level stores, so a
     * device should ask once and remember, not ask every tick.
     *
     * @param id the area's id
     * @return the box, or empty for an area with no blocks, or no such area
     */
    Optional<BoundingBox> bounds(ResourceLocation id);

    /**
     * Moves the areas wholly inside a box, as {@link Areas#moved} describes. Call {@link Areas#moved}
     * rather than this.
     *
     * @param box the blocks that moved, where they were, both corners inside
     * @param to the level they went to: this one, or another
     * @param pivot a position before the move
     * @param target where {@code pivot} went
     * @param rotation how the blocks turned about the vertical axis
     * @return how many areas moved
     */
    int move(BoundingBox box, ServerLevel to, BlockPos pivot, BlockPos target, Rotation rotation);
}
