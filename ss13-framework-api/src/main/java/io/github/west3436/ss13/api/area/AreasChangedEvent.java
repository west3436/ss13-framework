package io.github.west3436.ss13.api.area;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.Event;

/**
 * The area some blocks are in may have changed: an area was drawn, cleared, renamed or removed,
 * its blocks moved, or a provider's answer changed. A device that remembers its area, such as a
 * light that looks up its area's lighting channel, asks {@link Areas#at} again if it stands inside
 * {@link #bounds()}.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the level's own thread, after the change: by the
 * framework's {@link AreaStore stored areas} and by {@link Areas#moved}, and by {@link
 * Areas#changed} for a mod's own provider. A move fires one in each level it touches. Not
 * cancellable: the change has been made.
 */
public final class AreasChangedEvent extends Event {
    private final Level level;
    private final BoundingBox bounds;

    /**
     * Creates the event. Only the framework fires it; a provider calls {@link Areas#changed}.
     *
     * @param level the level
     * @param bounds a box that holds every block whose area may have changed; it is copied
     */
    public AreasChangedEvent(Level level, BoundingBox bounds) {
        this.level = Objects.requireNonNull(level, "level");
        Objects.requireNonNull(bounds, "bounds");
        this.bounds = copy(bounds);
    }

    /**
     * The level.
     *
     * @return the level, server or client
     */
    public Level level() {
        return level;
    }

    /**
     * A box that holds every block whose area may have changed. It may hold others too.
     *
     * @return a copy of the box
     */
    public BoundingBox bounds() {
        return copy(bounds);
    }

    /**
     * Whether a block's area may have changed.
     *
     * @param pos the block, in {@link #level()}
     * @return whether it is inside {@link #bounds()}
     */
    public boolean affects(BlockPos pos) {
        return bounds.isInside(pos);
    }

    /** A box is mutable, so the event keeps its own. */
    private static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
