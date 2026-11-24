package io.github.west3436.ss13.power;

import io.github.west3436.ss13.api.area.Area;
import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.ss13.api.power.PoweredDevice;
import io.github.west3436.ss13.device.DeviceBlockEntity;
import io.github.west3436.ss13.device.DeviceTicker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * The powered devices in an area, for a provider that covers its area, as an APC does: the loaded
 * {@link DeviceBlockEntity devices} of every mod that are {@link PoweredDevice}s and whose block is
 * in the area ({@link Areas#at}), in the order they joined the schedule. Asking loads nothing.
 */
public final class AreaDevices {
    private AreaDevices() {}

    /**
     * The powered devices in an area.
     *
     * @param level the level
     * @param area the area's id
     * @return a new list; empty if none is loaded there
     */
    public static List<PoweredDevice> in(ServerLevel level, ResourceLocation area) {
        List<PoweredDevice> out = new ArrayList<>();
        for (DeviceBlockEntity device : DeviceTicker.loaded(level)) {
            if (device instanceof PoweredDevice powered && !device.isRemoved()) {
                Optional<Area> at = Areas.at(level, device.getBlockPos());
                if (at.isPresent() && at.get().id().equals(area)) {
                    out.add(powered);
                }
            }
        }
        return out;
    }
}
