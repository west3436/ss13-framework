package io.github.west3436.ss13.device;

import io.github.west3436.ss13.label.LabelPayload;
import io.github.west3436.ss13.label.LabelTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Starts the device base and labels: the schedule, the activity sync, the name tag and their payloads. */
public final class Devices {
    /** The version of the device base's payloads; a client and server must match. */
    static final String PROTOCOL = "1";

    private Devices() {}

    /**
     * Registers the listeners. Called once, from the mod constructor.
     *
     * @param modBus the mod's own event bus
     * @param gameBus the game's event bus
     */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        DeviceTicker.register(gameBus);
        DeviceActivitySync.register(gameBus);
        LabelTag.register(gameBus);
        modBus.addListener(Devices::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL)
                .playToClient(DeviceActivityPayload.TYPE, DeviceActivityPayload.CODEC, ClientDeviceActivity::handle)
                .playToServer(LabelPayload.TYPE, LabelPayload.CODEC, LabelPayload::handle);
    }
}
