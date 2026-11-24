package io.github.west3436.ss13.api.device;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Something that moves blocks with their block entities, such as a ship mover, a Create contraption
 * or a Sable sub-level, registered with {@link BlockMovers#register}. The framework's devices ask it
 * two things: whether a device's block is being moved now, so it waits rather than stepping beside
 * air where a neighbour was or will be, and where the world shows a block, so a device on a ship is
 * heard and seen where the ship is.
 *
 * <p>Answers must be quick, and must never load a chunk.
 */
@FunctionalInterface
public interface BlockMover {
    /**
     * Whether the block at a position is being moved now. A device there waits, and steps again once
     * the move is over.
     *
     * <p>Thread: the server thread.
     *
     * @param level the level
     * @param pos the block
     * @return true while it is being moved
     */
    boolean isMoving(ServerLevel level, BlockPos pos);

    /**
     * Where the world shows a point of a level, as when its blocks are in a ship's plot far from
     * where the ship is drawn. The default shows every point where it is.
     *
     * <p>Thread: the level's own thread.
     *
     * @param level the level, server or client
     * @param point the point, in the level's own coordinates
     * @return where it is shown; {@code point} itself where this mover has no say
     */
    default Vec3 shownAt(Level level, Vec3 point) {
        return point;
    }
}
