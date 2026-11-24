/**
 * The power contract between devices and the providers that power them, after SS13's APCs.
 *
 * <ul>
 *   <li>A <b>device</b> ({@link io.github.west3436.ss13.api.power.PoweredDevice}) declares, in its
 *       {@link io.github.west3436.ss13.api.power.DevicePower}, what it draws, on which {@link
 *       io.github.west3436.ss13.api.power.PowerChannel channel} and with what {@link
 *       io.github.west3436.ss13.api.power.PowerPriority priority} inside it, and works only while
 *       a provider powers it.
 *   <li>A <b>provider</b> ({@link io.github.west3436.ss13.api.power.PowerProvider}), such as ZAS³'s
 *       area air controller or NT Engineering's APC, claims the devices it covers each step and
 *       powers those it holds, one step at a time, from its own store. Where two cover a device, the
 *       one of higher precedence holds it: an APC takes over from an area air controller, which
 *       goes on controlling the device. {@link io.github.west3436.ss13.api.power.PowerFeed#serve}
 *       does a provider's step.
 *   <li>An <b>area</b> can have a provider too ({@link io.github.west3436.ss13.api.power.AreaPower}),
 *       so that what isn't a device, such as a light, can ask whether its area's lighting is on.
 * </ul>
 *
 * <p>{@link io.github.west3436.ss13.api.power.EnergyStore} is an FE store that other mods reach
 * through NeoForge's FE capability.
 */
package io.github.west3436.ss13.api.power;
