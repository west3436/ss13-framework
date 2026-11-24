package io.github.west3436.ss13.api.power;

/**
 * Who a provider powers first inside one {@link PowerChannel} when it can't power everyone.
 * Declared in priority order, so {@link #compareTo} sorts the first served first.
 *
 * <p>ZAS³'s groups map onto a channel and a priority: life support (vents and scrubbers) is {@link
 * PowerChannel#ENVIRONMENT} at {@link #HIGH}, gas handling (pumps, filters and mixers) is {@link
 * PowerChannel#ENVIRONMENT} at {@link #NORMAL}, and the rest (igniters, rechargers) is {@link
 * PowerChannel#EQUIPMENT} at {@link #NORMAL}.
 */
public enum PowerPriority {
    /** Served first in its channel, such as a vent or a scrubber. */
    HIGH,
    /** Served after {@link #HIGH}. */
    NORMAL,
    /** Served last in its channel. */
    LOW
}
