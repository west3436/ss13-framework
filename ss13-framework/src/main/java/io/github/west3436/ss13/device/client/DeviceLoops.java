package io.github.west3436.ss13.device.client;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.device.ClientDeviceActivity;
import io.github.west3436.ss13.device.DeviceActivity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays a device's loop while the server says it is working. A kind of device gets its sound with
 * one line, {@link #add}. Each client tick this asks the {@link Player} for the loop of every device
 * working at its kind's lowest level or above, within {@value #RANGE} blocks and whose block is
 * still there, as loud as its level. The player does the rest: a device that stops being asked for
 * fades out by itself.
 *
 * <p>The framework doesn't play the loops itself: a mod that has a looping-sound manager installs
 * it with {@link #playWith}, as ZAS³ does with its {@code ClientSounds}, which fades loops in thin
 * air. Until one is installed, nothing plays.
 *
 * <p>A device on blocks a {@linkplain io.github.west3436.ss13.api.device.BlockMovers mover} shows
 * somewhere else, as on a ship, plays where the world shows it and is measured from there. It is
 * placed again each tick, so it follows the ship.
 *
 * <p>It also keeps the client's table to the current level, so it runs whether or not any sound has
 * been added. Client thread only.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID, value = Dist.CLIENT)
public final class DeviceLoops {
    /** How far a working device is heard, in blocks. */
    public static final double RANGE = 8;

    /** What plays the loops: a looping-sound manager. */
    @FunctionalInterface
    public interface Player {
        /**
         * Asks for a loop, or renews the request. It is asked again every client tick while the
         * device works, and a loop that isn't asked for again within a few ticks should stop.
         *
         * @param source the device's block, which names the loop with {@code sound}
         * @param sound the loop
         * @param at where the world shows the device
         * @param volume how loud, 0 to 1, before distance
         * @param range how far away it is heard, in blocks
         */
        void loop(BlockPos source, SoundEvent sound, Vec3 at, float volume, double range);
    }

    private record Loop(Supplier<? extends Block> block, Supplier<? extends SoundEvent> sound, int minLevel) {}

    /** Added but not looked up yet: the registries aren't ready when a mod's constructor runs. */
    private static final List<Loop> ADDED = new ArrayList<>();

    private static final Map<Block, Loop> BY_BLOCK = new IdentityHashMap<>();

    private static volatile Player player;

    private DeviceLoops() {}

    /**
     * Installs what plays the loops, from a client entry point. The last one installed plays them.
     *
     * @param loops the player
     */
    public static void playWith(Player loops) {
        player = Objects.requireNonNull(loops, "loops");
    }

    /**
     * Gives a kind of device its loop, from the client entry point, playing whenever the server says
     * it is doing anything.
     *
     * @param block its block, as the registry's holder
     * @param sound the loop, as the registry's holder
     */
    public static void add(Supplier<? extends Block> block, Supplier<? extends SoundEvent> sound) {
        add(block, sound, DeviceActivity.TRICKLE);
    }

    /**
     * Gives a kind of device its loop, from the client entry point, playing from a level up: for one
     * whose loop goes with particles that start at that level.
     *
     * @param block its block, as the registry's holder
     * @param sound the loop, as the registry's holder
     * @param minLevel the lowest {@link DeviceActivity} level it plays at
     */
    public static void add(Supplier<? extends Block> block, Supplier<? extends SoundEvent> sound, int minLevel) {
        ADDED.add(new Loop(block, sound, minLevel));
    }

    @SubscribeEvent
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDeviceActivity.LOCAL.clear();
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ClientDeviceActivity.LOCAL.enter(level);
        if (level == null || mc.isPaused()) {
            return;
        }
        for (Loop loop : ADDED) {
            BY_BLOCK.put(loop.block().get(), loop);
        }
        ADDED.clear();
        Player loops = player;
        if (BY_BLOCK.isEmpty() || loops == null) {
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        ClientDeviceActivity.LOCAL.forEachHeard(level, ear, RANGE, (at, activity, shown) -> {
            Loop loop = BY_BLOCK.get(level.getBlockState(at).getBlock());
            if (loop != null && activity >= loop.minLevel()) {
                loops.loop(at.immutable(), loop.sound().get(), shown, DeviceActivity.volume(activity), RANGE);
            }
        });
    }
}
