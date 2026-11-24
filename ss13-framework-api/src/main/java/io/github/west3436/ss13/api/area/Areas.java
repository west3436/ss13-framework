package io.github.west3436.ss13.api.area;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The {@link Area} a block is in, answered by the registered {@link AreaProvider providers}.
 *
 * <p><b>Precedence.</b> Areas don't overlap: {@link #at} answers one area, from the provider of the
 * highest priority that has one there. Providers of equal priority go in order of their ids, and the
 * one whose id sorts last wins, as among {@link io.github.west3436.ss13.api.gravity.Gravity gravity}
 * providers the one applied last does. {@link #claims} lists every provider's answer, the winner
 * first, for a mod that wants the area under the one that won, such as the ship a room is on. The
 * usual priorities:
 *
 * <ul>
 *   <li>{@link #REGION}: a large region with rooms in it, such as a ship's box.
 *   <li>{@link #ROOM}: an area drawn block by block, such as a room marked out with blueprints. The
 *       framework's {@link #store stored areas} answer with this priority, as {@link #STORED}.
 * </ul>
 *
 * <p><b>Changes.</b> Whenever the area at some blocks may have changed, an {@link
 * AreasChangedEvent} names them: the stored areas fire it themselves, and a mod's own provider
 * calls {@link #changed}.
 *
 * <p><b>Moves.</b> A mod that moves a box of blocks, such as a ship's mover, calls {@link #moved}
 * once they are in place, and the stored areas wholly inside the box go with them. ZAS³'s relocation
 * hand-over calls it for a mover that uses it, so such a mover doesn't. A provider's own areas move
 * however the provider keeps them: a ship's box follows its computer.
 *
 * <p>Thread: registering may happen on any thread; {@link #at} and {@link #claims} run on the
 * level's own thread; {@link #store}, {@link #moved} and {@link #changed} for a server level, on
 * the server thread.
 */
public final class Areas {
    /** The priority of a provider of large regions with rooms in them, such as a ship's box. */
    public static final int REGION = 0;

    /** The priority of a provider of areas drawn block by block, such as the stored areas. */
    public static final int ROOM = 1000;

    /** {@code ss13_framework:stored}: the provider of each level's {@link #store stored areas}. */
    public static final ResourceLocation STORED = ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "stored");

    /** A registered provider. */
    record Registration(ResourceLocation id, int priority, AreaProvider provider) {}

    private static final Comparator<Registration> ORDER =
            Comparator.comparingInt(Registration::priority).thenComparing(Registration::id);

    /** The providers, lowest precedence first, replaced whole on every registration. */
    private static volatile List<Registration> providers = List.of();

    /** Each level's stored areas, once the framework mod has installed them. */
    private static volatile Function<ServerLevel, AreaStore> stores;

    private Areas() {}

    /**
     * Registers a provider, usually during mod construction.
     *
     * @param id the provider's id, in its mod's namespace
     * @param priority its precedence: {@link #REGION}, {@link #ROOM}, or any other number, higher
     *     winning
     * @param provider the provider
     * @throws IllegalArgumentException if a provider with this id is already registered
     */
    public static synchronized void register(ResourceLocation id, int priority, AreaProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        List<Registration> next = new ArrayList<>(providers);
        if (next.stream().anyMatch(r -> r.id().equals(id))) {
            throw new IllegalArgumentException("an area provider is already registered as " + id);
        }
        next.add(new Registration(id, priority, provider));
        next.sort(ORDER);
        providers = List.copyOf(next);
    }

    /**
     * Whether a provider is registered under an id.
     *
     * @param id the id
     * @return true if one is
     */
    public static boolean isRegistered(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return providers.stream().anyMatch(r -> r.id().equals(id));
    }

    /**
     * The area a block is in: the answer of the provider with the highest precedence that has one.
     *
     * <p>Thread: the level's own.
     *
     * @param level a server or client level
     * @param pos the block
     * @return its area, or empty if no provider puts it in one
     * @throws IllegalStateException if a provider answers null
     */
    public static Optional<Area> at(Level level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        return resolve(providers, level, pos);
    }

    /**
     * Every provider's answer at a block, the one {@link #at} gives first, then the others in order
     * of precedence. Providers with no answer there are left out.
     *
     * <p>Thread: the level's own.
     *
     * @param level a server or client level
     * @param pos the block
     * @return the answers; empty if no provider puts the block in an area
     * @throws IllegalStateException if a provider answers null
     */
    public static List<AreaClaim> claims(Level level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        return claims(providers, level, pos);
    }

    /**
     * Says that a provider's answer may have changed at some blocks: fires an {@link
     * AreasChangedEvent}. A provider calls it, for instance, when a ship's box is resized.
     *
     * <p>Thread: the level's own.
     *
     * @param level the level
     * @param bounds a box holding every block whose area may have changed
     */
    public static void changed(Level level, BoundingBox bounds) {
        NeoForge.EVENT_BUS.post(new AreasChangedEvent(level, bounds));
    }

    /**
     * A level's stored areas.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @return its store
     * @throws IllegalStateException if the {@code ss13_framework} mod isn't loaded, which only
     *     happens when the API is used without it
     */
    public static AreaStore store(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Function<ServerLevel, AreaStore> installed = stores;
        if (installed == null) {
            throw new IllegalStateException("stored areas need the " + FrameworkApi.MOD_ID + " mod");
        }
        return installed.apply(level);
    }

    /**
     * Carries the stored areas along with a box of blocks that has moved, once the blocks are in
     * place. A block at {@code p} went to {@link #map map(p, pivot, target, rotation)}: the same rule
     * as ZAS³'s relocation {@code Destination}, so its hand-over passes its destination on as it is.
     *
     * <p>An area goes if all its blocks were inside the box, and keeps its id and name; in another
     * level it is defined there, or, if that level has an area with the same id, the two become one
     * with the moved area's name. An area with blocks inside and outside the box, such as a hangar the
     * box cuts through, stays where it is with all its blocks. At the destination an arriving area
     * takes its blocks from whatever area held them. Fires an {@link AreasChangedEvent} in each
     * level where an area moved.
     *
     * <p>Call it once per move, in the tick the blocks arrive. ZAS³ calls it from its relocation
     * hand-over, so a mover that hands over to ZAS³ doesn't call it as well: a second call would move
     * an area again if the box still held it.
     *
     * <p>Thread: server thread.
     *
     * @param from the level the blocks left
     * @param box the blocks, where they were, both corners inside
     * @param to the level they arrived in: the same, or another
     * @param pivot a position before the move
     * @param target where {@code pivot} went
     * @param rotation how the blocks turned about the vertical axis
     * @return how many areas moved; 0 without the {@code ss13_framework} mod
     */
    public static int moved(
            ServerLevel from, BoundingBox box, ServerLevel to, BlockPos pivot, BlockPos target, Rotation rotation) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(pivot, "pivot");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rotation, "rotation");
        Function<ServerLevel, AreaStore> installed = stores;
        return installed == null ? 0 : installed.apply(from).move(box, to, pivot, target, rotation);
    }

    /**
     * Carries the stored areas along with a box of blocks that has moved by an offset, without
     * turning, as {@link #moved(ServerLevel, BoundingBox, ServerLevel, BlockPos, BlockPos, Rotation)}
     * describes.
     *
     * <p>Thread: server thread.
     *
     * @param from the level the blocks left
     * @param box the blocks, where they were, both corners inside
     * @param to the level they arrived in: the same, or another
     * @param offset how far every block went
     * @return how many areas moved; 0 without the {@code ss13_framework} mod
     */
    public static int moved(ServerLevel from, BoundingBox box, ServerLevel to, Vec3i offset) {
        Objects.requireNonNull(offset, "offset");
        return moved(from, box, to, BlockPos.ZERO, new BlockPos(offset), Rotation.NONE);
    }

    /**
     * Where a move puts a block: {@code target + (pos - pivot)} turned by {@code rotation} about the
     * vertical axis, as {@link BlockPos#rotate} turns it.
     *
     * <p>Thread: any.
     *
     * @param pos the block before the move
     * @param pivot a position before the move
     * @param target where {@code pivot} goes
     * @param rotation how the blocks turn
     * @return the block after the move
     */
    public static BlockPos map(BlockPos pos, BlockPos pivot, BlockPos target, Rotation rotation) {
        return target.offset(pos.subtract(pivot).rotate(rotation));
    }

    /**
     * Installs each level's stored areas. Only the {@code ss13_framework} mod calls this, once, while
     * it is constructed.
     *
     * <p>Thread: any; in practice the mod-loading thread.
     *
     * @param levelStores gives a level's store, on the server thread
     * @throws IllegalStateException if stores are already installed
     */
    public static synchronized void installStores(Function<ServerLevel, AreaStore> levelStores) {
        Objects.requireNonNull(levelStores, "levelStores");
        if (stores != null) {
            throw new IllegalStateException("stored areas are already installed");
        }
        stores = levelStores;
    }

    /** The answer of the provider with the highest precedence that has one. */
    static Optional<Area> resolve(List<Registration> registrations, Level level, BlockPos pos) {
        for (int i = registrations.size() - 1; i >= 0; i--) {
            Optional<Area> area = ask(registrations.get(i), level, pos);
            if (area.isPresent()) {
                return area;
            }
        }
        return Optional.empty();
    }

    /** Every provider's answer, highest precedence first. */
    static List<AreaClaim> claims(List<Registration> registrations, Level level, BlockPos pos) {
        List<AreaClaim> claims = new ArrayList<>();
        for (int i = registrations.size() - 1; i >= 0; i--) {
            Registration registration = registrations.get(i);
            ask(registration, level, pos)
                    .ifPresent(area -> claims.add(new AreaClaim(registration.id(), registration.priority(), area)));
        }
        return List.copyOf(claims);
    }

    private static Optional<Area> ask(Registration registration, Level level, BlockPos pos) {
        Optional<Area> area = registration.provider().at(level, pos);
        if (area == null) {
            throw new IllegalStateException("area provider " + registration.id() + " answered null");
        }
        return area;
    }

    /** The registrations in order, sorted as {@link #register} sorts them; for tests. */
    static List<Registration> sorted(List<Registration> registrations) {
        return registrations.stream().sorted(ORDER).toList();
    }
}
