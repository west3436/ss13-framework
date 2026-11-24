package io.github.west3436.ss13.api.area;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A source of areas, registered with {@link Areas#register}: the framework's {@link
 * Areas#store(net.minecraft.server.level.ServerLevel) stored areas}, or a mod's own, such as
 * Expedition's ship boxes, which follow its Shuttle Computers.
 *
 * <p>Devices ask often, so an answer must be quick and must never load a chunk or a block. A
 * provider whose areas depend on its own state, such as where a ship's computer stands, keeps that
 * state where it can look it up by position, and moves it along when its blocks move. When its
 * answer for some positions changes, it calls {@link Areas#changed} so devices there can ask again.
 */
@FunctionalInterface
public interface AreaProvider {
    /**
     * The area this provider puts a position in.
     *
     * <p>Thread: the level's own thread, the server thread for a server level and the client
     * thread for a client level. A provider that only knows its areas on the server answers
     * nothing on the client.
     *
     * @param level the level, server or client
     * @param pos the position, which may be in a chunk that isn't loaded
     * @return the area, or empty where this provider has no say; never null
     */
    Optional<Area> at(Level level, BlockPos pos);
}
