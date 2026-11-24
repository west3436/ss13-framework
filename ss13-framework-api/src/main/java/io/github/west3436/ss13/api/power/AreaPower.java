package io.github.west3436.ss13.api.power;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Which provider serves each {@link io.github.west3436.ss13.api.area.Area area}, by the area's
 * {@linkplain io.github.west3436.ss13.api.area.Area#id() id}: an APC claims its area each step, as it
 * claims the devices in it, so that what isn't a device can ask whether its area's channel is on, as
 * a light asks about {@link PowerChannel#LIGHTING}.
 *
 * <p>An area's claim follows the same rule as a device's ({@link DevicePower}): the provider of
 * highest precedence holds it, the first of equals keeps it, and it lasts {@link
 * DevicePower#LEASE_TICKS} unless renewed, or until its provider stops {@linkplain
 * PowerProvider#providing providing}. A provider that doesn't hold its area, such as a second APC in
 * one room, powers nothing there.
 *
 * <p>Nothing is saved: providers claim again as they step after a load.
 *
 * <p>Thread: the server thread.
 */
public final class AreaPower {
    private static final Map<ServerLevel, Map<ResourceLocation, Claim>> CLAIMS = new WeakHashMap<>();

    private AreaPower() {}

    /**
     * Claims an area for a provider for the next {@link DevicePower#LEASE_TICKS}. A provider calls
     * this each step, before it claims the devices in the area.
     *
     * @param level the level
     * @param area the area's id
     * @param provider the provider
     * @return whether the provider holds the area now
     */
    public static boolean claim(ServerLevel level, ResourceLocation area, PowerProvider provider) {
        Objects.requireNonNull(area, "area");
        return CLAIMS.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(area, a -> new Claim())
                .claim(provider, now(level));
    }

    /**
     * Drops a provider's claim on an area, if it holds it, as when it is moved to another area.
     *
     * @param level the level
     * @param area the area's id
     * @param provider the provider
     */
    public static void release(ServerLevel level, ResourceLocation area, PowerProvider provider) {
        Map<ResourceLocation, Claim> areas = CLAIMS.get(level);
        Claim claim = areas == null ? null : areas.get(area);
        if (claim != null) {
            claim.release(provider);
            if (claim.holder(now(level)) == null) {
                areas.remove(area);
            }
        }
    }

    /**
     * The provider that holds an area now.
     *
     * @param level the level
     * @param area the area's id
     * @return the provider, or empty if none does
     */
    public static Optional<PowerProvider> provider(ServerLevel level, ResourceLocation area) {
        Map<ResourceLocation, Claim> areas = CLAIMS.get(level);
        Claim claim = areas == null ? null : areas.get(area);
        return claim == null ? Optional.empty() : Optional.ofNullable(claim.holder(now(level)));
    }

    /**
     * Whether an area's channel is on: a provider holds the area and has the channel on.
     *
     * @param level the level
     * @param area the area's id
     * @param channel the channel
     * @return false with no provider
     */
    public static boolean channelOn(ServerLevel level, ResourceLocation area, PowerChannel channel) {
        return provider(level, area).map(p -> p.channelOn(channel)).orElse(false);
    }

    private static long now(ServerLevel level) {
        return level.getServer().getTickCount();
    }
}
