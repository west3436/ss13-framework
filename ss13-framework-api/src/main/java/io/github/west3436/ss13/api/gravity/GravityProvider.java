package io.github.west3436.ss13.api.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A source of gravity, such as a planet or a gravity generator, registered with {@link
 * Gravity#register}.
 *
 * <p>The framework asks every provider about every entity it moves, many times a tick, so an
 * answer must be quick and must never load a chunk or block. A provider answers on both logical
 * sides: the client asks too, to predict how dropped items and projectiles fall, so a provider
 * whose field the client can't know about should answer {@link GravityEffect#NONE} there and let
 * the server's answer reach living entities through their synced attributes.
 */
@FunctionalInterface
public interface GravityProvider {
    /**
     * What this provider does to the gravity at a position.
     *
     * <p>Thread: the level's own thread, the server thread for a server level and the client
     * thread for a client level.
     *
     * @param level the level, server or client
     * @param pos the position, which may be in a chunk that isn't loaded
     * @return the effect, or {@link GravityEffect#NONE} where this provider has no say; never null
     */
    GravityEffect at(Level level, BlockPos pos);
}
