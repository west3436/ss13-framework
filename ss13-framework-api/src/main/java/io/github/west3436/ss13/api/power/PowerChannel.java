package io.github.west3436.ss13.api.power;

/**
 * The channel a device draws on, after an SS13 APC's three: a provider can switch each one off on
 * its own ({@link PowerProvider#channelOn}). Declared in the order a provider short of power serves
 * them, so {@link #compareTo} sorts the first served first: the environment, which keeps a room
 * alive, then equipment, then lighting.
 */
public enum PowerChannel {
    /** What keeps a room's air and temperature: vents, scrubbers, pumps, air alarms, airlocks. */
    ENVIRONMENT,
    /** Machines and consoles: rechargers, igniters, computers, drives. */
    EQUIPMENT,
    /** Lights. */
    LIGHTING
}
