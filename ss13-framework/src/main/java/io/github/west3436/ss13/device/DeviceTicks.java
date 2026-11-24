package io.github.west3436.ss13.device;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The devices' clock. Every {@link DeviceBlockEntity} steps once per device tick, which is one
 * second, and every rate a device has is defined per second.
 */
public final class DeviceTicks {
    /** Game ticks per device step: 20, one second. */
    public static final int PERIOD = 20;

    /** Seconds per device step: every per-second rate is multiplied by this once per step. */
    public static final double SECONDS = PERIOD / 20.0;

    private DeviceTicks() {}

    /**
     * The game tick that schedules and leases count in: the server's tick counter, which never goes
     * backwards while the server runs.
     *
     * @param level any level of the server
     * @return the current game tick
     */
    public static long now(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    /**
     * Whether something an entity carries is due its device tick now. An item stack has no schedule,
     * and the game offers it a tick each game tick only while it is in a player's inventory or
     * dropped, so an item that works asks this each time. It is true once per {@link #PERIOD} for
     * each entity, at a phase given by its id, which spreads entities across game ticks as {@link
     * DeviceSchedule} does devices.
     *
     * @param level the level, on the server
     * @param holder the entity carrying the item: the player, or the dropped item
     * @return true in one game tick of every {@link #PERIOD}
     */
    public static boolean due(ServerLevel level, Entity holder) {
        return Math.floorMod(now(level) + holder.getId(), PERIOD) == 0;
    }
}
