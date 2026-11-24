package io.github.west3436.ss13.api.power;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Who holds something a provider claims, a device or an area: the provider of highest precedence
 * among those that claimed it within the last {@link DevicePower#LEASE_TICKS} and still provide.
 */
final class Claim {
    private @Nullable PowerProvider holder;
    private long until = Long.MIN_VALUE;

    /** Claims it for a provider; true if the provider holds it afterwards. */
    boolean claim(PowerProvider provider, long now) {
        Objects.requireNonNull(provider, "provider");
        if (!provider.providing()) {
            return false;
        }
        PowerProvider current = holder(now);
        if (current == null || current == provider || provider.precedence() > current.precedence()) {
            holder = provider;
            until = now + DevicePower.LEASE_TICKS;
            return true;
        }
        return false;
    }

    /** The provider that holds it now, or null. */
    @Nullable
    PowerProvider holder(long now) {
        if (holder != null && (now >= until || !holder.providing())) {
            holder = null;
        }
        return holder;
    }

    /** Keeps the claim held now for another lease from now, while what holds it waits. */
    void hold(long now) {
        if (holder(now) != null) {
            until = Math.max(until, now + DevicePower.LEASE_TICKS);
        }
    }

    /** Drops a provider's claim, if it holds it. */
    void release(PowerProvider provider) {
        if (holder == provider) {
            holder = null;
            until = Long.MIN_VALUE;
        }
    }
}
