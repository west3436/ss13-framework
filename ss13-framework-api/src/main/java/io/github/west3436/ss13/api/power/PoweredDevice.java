package io.github.west3436.ss13.api.power;

/**
 * A device that draws FE from a {@link PowerProvider}: implemented by the block entities of vents,
 * scrubbers, pumps, lights, drives and the rest. The contract is in {@link DevicePower}.
 *
 * <p>A provider finds the devices it covers its own way: by its area (the framework's {@code
 * AreaDevices}), by a zone, or by links made with a multitool.
 */
public interface PoweredDevice {
    /**
     * The device's power state, the same object every time.
     *
     * @return its lease, claim and draw
     */
    DevicePower power();
}
