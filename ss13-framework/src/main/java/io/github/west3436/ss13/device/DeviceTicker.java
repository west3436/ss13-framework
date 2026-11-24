package io.github.west3436.ss13.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Steps the loaded {@link DeviceBlockEntity}s of each level once per device tick, spread across
 * game ticks. It runs at the end of every server tick, on the server thread. A device whose chunk
 * doesn't tick block entities is passed over ({@link DeviceBlockEntity#ticking()}).
 *
 * <p>Every method is for the server thread only.
 */
public final class DeviceTicker {
    private static final Map<ResourceKey<Level>, DeviceSchedule<DeviceBlockEntity>> SCHEDULES = new LinkedHashMap<>();

    private DeviceTicker() {}

    /** Registers the tick and lifecycle listeners. Called once, from the mod constructor. */
    static void register(IEventBus gameBus) {
        gameBus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        gameBus.addListener((ServerStoppedEvent e) -> SCHEDULES.clear());
        gameBus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                SCHEDULES.remove(level.dimension());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            DeviceSchedule<DeviceBlockEntity> schedule = SCHEDULES.get(level.dimension());
            if (schedule != null) {
                schedule.tick(now);
            }
        }
    }

    static void add(ServerLevel level, DeviceBlockEntity device) {
        SCHEDULES
                .computeIfAbsent(level.dimension(), k -> new DeviceSchedule<>(DeviceTicks.PERIOD))
                .add(device, DeviceTicks.now(level));
    }

    static void remove(ServerLevel level, DeviceBlockEntity device) {
        DeviceSchedule<DeviceBlockEntity> schedule = SCHEDULES.get(level.dimension());
        if (schedule != null) {
            schedule.remove(device);
        }
    }

    /**
     * The devices loaded in a level, in the order they joined: of every mod, so a caller looking for
     * its own kind filters them.
     *
     * @param level the level
     * @return a new list; empty if the level has no devices
     */
    public static List<DeviceBlockEntity> loaded(ServerLevel level) {
        DeviceSchedule<DeviceBlockEntity> schedule = SCHEDULES.get(level.dimension());
        return schedule == null ? List.of() : schedule.members();
    }

    /**
     * Whether a device is in its level's schedule.
     *
     * @param device the device
     * @return true while it is loaded on the server
     */
    public static boolean isScheduled(DeviceBlockEntity device) {
        if (!(device.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        DeviceSchedule<DeviceBlockEntity> schedule = SCHEDULES.get(level.dimension());
        return schedule != null && schedule.contains(device);
    }

    /**
     * Steps every ready device of a level once now, whether its chunk ticks or not, without changing
     * when each is next due. For GameTests, which drive device ticks themselves, and only those.
     *
     * @param level the level
     */
    public static void stepNow(ServerLevel level) {
        DeviceSchedule<DeviceBlockEntity> schedule = SCHEDULES.get(level.dimension());
        if (schedule != null) {
            schedule.stepAll();
        }
    }
}
