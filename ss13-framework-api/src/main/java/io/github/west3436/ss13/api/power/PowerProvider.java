package io.github.west3436.ss13.api.power;

/**
 * Something that powers devices from its own store, such as ZAS³'s area air controller or NT
 * Engineering's APC, usually a block entity.
 *
 * <p>Each step, a provider {@linkplain DevicePower#claim claims} every device it covers, and powers
 * those whose claim it holds ({@link PowerFeed#serve} does both). Where several cover one device,
 * the one of highest {@link #precedence} holds it, so an APC takes over the devices in its area from
 * an area air controller. Powering and controlling are apart: the controller still sends the
 * devices it covers their orders, and only stops paying for those another provider holds. When the
 * APC goes, it stops {@link #providing}, its claims end at once, and the controller claims and
 * powers the devices again at its next step, within the lease the APC paid for.
 *
 * <p>A provider that holds a device keeps it while one of its channels is off: the device goes
 * without power, as it would on an SS13 APC with that channel switched off. A provider that would
 * rather hand such devices on {@linkplain DevicePower#release releases} them.
 *
 * <p>Thread: the server thread.
 */
public interface PowerProvider {
    /**
     * The precedence of a provider that powers devices only where nothing better does, from its own
     * buffer: ZAS³'s area air controller.
     */
    int BACKUP = 0;

    /** The precedence of a provider on a power grid: NT Engineering's APC. */
    int MAINS = 1000;

    /**
     * The precedence of a device that powers the parts wired to it as part of its own draw, such as
     * ZAS³'s airlock controller its vents: no other provider pays for them while it holds them.
     */
    int DIRECT = 2000;

    /**
     * Its precedence: {@link #BACKUP}, {@link #MAINS}, {@link #DIRECT}, or any other number, higher
     * taking over. It must not change while the provider holds claims. Between two of equal
     * precedence, the one that holds a device keeps it.
     *
     * @return the precedence
     */
    int precedence();

    /**
     * Whether it can hold claims now: false once it has been removed or unloaded, which ends every
     * claim it holds at once, so a provider of lower precedence takes over at its next step.
     *
     * @return whether it provides
     */
    boolean providing();

    /**
     * Whether a channel is on: a device it holds on a channel that is off gets no power. The default
     * is every channel on, for a provider without channels such as an area air controller.
     *
     * @param channel the channel
     * @return whether it powers that channel now
     */
    default boolean channelOn(PowerChannel channel) {
        return true;
    }
}
