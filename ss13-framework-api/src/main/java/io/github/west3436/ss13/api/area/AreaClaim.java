package io.github.west3436.ss13.api.area;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One provider's answer at a position, as {@link Areas#claims} lists them: which provider put the
 * position in which area, and with what priority.
 *
 * <p>Immutable, and may be used from any thread.
 *
 * @param provider the id the provider was registered under
 * @param priority the priority it was registered with
 * @param area the area it answered
 */
public record AreaClaim(ResourceLocation provider, int priority, Area area) {
    /**
     * Checks the arguments.
     *
     * @throws NullPointerException if the provider or the area is null
     */
    public AreaClaim {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(area, "area");
    }
}
