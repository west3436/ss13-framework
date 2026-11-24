package io.github.west3436.ss13.api.device;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The registered {@link BlockMover}s, asked by the framework's devices whether their block is being
 * moved and where the world shows them. With none registered, nothing is ever moving and every
 * point is shown where it is.
 *
 * <p>Thread: registering may happen on any thread; the questions run on the level's own thread.
 */
public final class BlockMovers {
    private record Registration(ResourceLocation id, BlockMover mover) {}

    /** The movers in order of their ids, replaced whole on every registration. */
    private static volatile List<Registration> movers = List.of();

    private BlockMovers() {}

    /**
     * Registers a mover, usually during mod construction.
     *
     * @param id the mover's id, in its mod's namespace
     * @param mover the mover
     * @throws IllegalArgumentException if a mover with this id is already registered
     */
    public static synchronized void register(ResourceLocation id, BlockMover mover) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mover, "mover");
        List<Registration> next = new ArrayList<>(movers);
        if (next.stream().anyMatch(r -> r.id().equals(id))) {
            throw new IllegalArgumentException("a block mover is already registered as " + id);
        }
        next.add(new Registration(id, mover));
        next.sort(Comparator.comparing(Registration::id));
        movers = List.copyOf(next);
    }

    /**
     * Whether a mover is registered under an id.
     *
     * @param id the id
     * @return true if one is
     */
    public static boolean isRegistered(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return movers.stream().anyMatch(r -> r.id().equals(id));
    }

    /**
     * Whether any mover is moving the block at a position now.
     *
     * @param level the level
     * @param pos the block
     * @return true if one says it is
     */
    public static boolean isMoving(ServerLevel level, BlockPos pos) {
        for (Registration r : movers) {
            if (r.mover().isMoving(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the world shows a point: where the first mover, in order of their ids, that has a say
     * shows it.
     *
     * @param level the level, server or client
     * @param point the point, in the level's own coordinates
     * @return where it is shown; {@code point} itself if no mover has a say
     */
    public static Vec3 shownAt(Level level, Vec3 point) {
        for (Registration r : movers) {
            Vec3 shown = r.mover().shownAt(level, point);
            if (shown != null && !shown.equals(point)) {
                return shown;
            }
        }
        return point;
    }
}
