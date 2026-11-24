package io.github.west3436.ss13.api.power;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A powered device's side of the power contract: its draw, its channel, and the lease and claim
 * its providers keep on it.
 *
 * <p><b>The device</b> declares what it draws now, in FE/t ({@link #draw()}; 0 while it is off), the
 * {@link #channel()} it draws on and its {@link #priority()} inside that channel. It works only
 * while {@link #isPowered()}, and does nothing otherwise.
 *
 * <p><b>Claims.</b> Each provider that covers the device {@linkplain #claim claims} it each step.
 * The claim is held by the one of highest {@linkplain PowerProvider#precedence precedence}; between
 * equals, by the one that held it first. A claim lasts {@link #LEASE_TICKS} unless renewed, and ends
 * at once when its provider {@linkplain PowerProvider#providing stops providing} or {@linkplain
 * #release releases} it, so a provider of lower precedence takes over at its next step.
 *
 * <p><b>Leases.</b> The provider that holds the claim powers the device one step at a time: it
 * takes {@link #drawPerStep()} FE from its own store and calls {@link #supply}. Each supply powers
 * the device for {@link #LEASE_TICKS} game ticks from the call, which covers one step with room to
 * spare, so the device and its provider needn't step in any order. Nothing needs to switch a device
 * off: when no provider supplies it any more, its lease runs out and it stops.
 *
 * <p><b>Waiting.</b> A device the schedule passes over, because its chunk or its provider's doesn't
 * tick now, does no work, and its lease and claim are {@linkplain #hold() held} meanwhile, so it
 * comes back powered rather than reading the wait as a power cut. The framework's device base does
 * this for a device that is a {@link PoweredDevice}.
 *
 * <p><b>Without a provider</b> a device has no power, even beside an FE source.
 *
 * <p><b>For tests,</b> {@link #setTestPowered(boolean)} powers a device with no provider. It is
 * saved with the device as {@code TestPowered}, so a test world can set it with {@code /data merge
 * block}; it is not a gameplay feature, and nothing in survival sets it.
 *
 * <p>Thread: the server thread.
 */
public final class DevicePower {
    /** Game ticks per device step, 20: the framework's device tick, one second. */
    public static final int PERIOD = 20;

    /** How long one {@link #supply} powers a device, and one {@link #claim} lasts: two steps. */
    public static final int LEASE_TICKS = 2 * PERIOD;

    private static final String TEST_POWERED = "TestPowered";

    private final PowerChannel channel;
    private final PowerPriority priority;
    private final IntSupplier draw;
    private final LongSupplier clock;
    private final Claim claim = new Claim();
    private long poweredUntil = Long.MIN_VALUE;
    private long heldUntil = Long.MIN_VALUE;
    private @Nullable PowerProvider source;
    private int suppliedDraw;
    private boolean testPowered;

    /**
     * Makes a device's power state, with no power.
     *
     * @param channel the channel it draws on
     * @param priority who it is powered before or after in its channel when power runs short
     * @param draw what the device draws now, in FE/t; 0 while it is off
     * @param clock the current game tick, as the framework's {@code DeviceTicks.now}
     */
    public DevicePower(PowerChannel channel, PowerPriority priority, IntSupplier draw, LongSupplier clock) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.draw = Objects.requireNonNull(draw, "draw");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The channel the device draws on.
     *
     * @return its channel
     */
    public PowerChannel channel() {
        return channel;
    }

    /**
     * Its priority inside its channel.
     *
     * @return its priority
     */
    public PowerPriority priority() {
        return priority;
    }

    /**
     * What the device draws now; 0 while it is off.
     *
     * @return FE/t, never negative
     */
    public int draw() {
        return Math.max(0, draw.getAsInt());
    }

    /**
     * What one step of power costs a provider now: {@link #draw()} × {@link #PERIOD}.
     *
     * @return FE
     */
    public long drawPerStep() {
        return (long) draw() * PERIOD;
    }

    // ---- Claims -----------------------------------------------------------------------------

    /**
     * Claims the device for a provider that covers it, for the next {@link #LEASE_TICKS}. A provider
     * calls this each step for each device it covers, before it pays for any.
     *
     * @param provider the provider
     * @return whether the provider holds the claim now, and so is the one to power the device
     */
    public boolean claim(PowerProvider provider) {
        return claim.claim(provider, clock.getAsLong());
    }

    /**
     * Drops a provider's claim, if it holds it, so another may take the device at once: for a
     * provider that stops covering the device, or one that hands on a device on a channel it has
     * switched off. A provider that is removed needn't call this; its claims end when it stops
     * {@linkplain PowerProvider#providing providing}.
     *
     * @param provider the provider
     */
    public void release(PowerProvider provider) {
        claim.release(provider);
    }

    /**
     * The provider that holds the claim now.
     *
     * @return the provider, or null if none does
     */
    public @Nullable PowerProvider claimant() {
        return claim.holder(clock.getAsLong());
    }

    // ---- Leases -----------------------------------------------------------------------------

    /**
     * Powers the device for the next {@link #LEASE_TICKS} game ticks, if the provider {@linkplain
     * #claim claims} it and holds the claim. The provider has already taken, or takes now, {@link
     * #drawPerStep()} FE from its store.
     *
     * @param provider who powers it, for {@link #source()}
     * @return whether it powered the device: false, changing nothing, if another provider holds it
     */
    public boolean supply(PowerProvider provider) {
        if (!claim(provider)) {
            return false;
        }
        source = provider;
        suppliedDraw = draw();
        poweredUntil = clock.getAsLong() + LEASE_TICKS;
        return true;
    }

    /**
     * Keeps the power and the claim the device has now while it waits: the schedule calls this each
     * time it passes the device over, and a lease that hasn't run out lasts {@link #LEASE_TICKS}
     * from then. A held lease pays for nothing, since the device does no work while it waits, so
     * {@link #suppliedThisStep} doesn't count it: a provider that steps pays for the next one as
     * before. A device with no power keeps none.
     */
    public void hold() {
        long now = clock.getAsLong();
        if (now < leaseEnd()) {
            heldUntil = Math.max(heldUntil, now + LEASE_TICKS);
        }
        claim.hold(now);
    }

    /** The end of the lease, held or supplied. */
    private long leaseEnd() {
        return Math.max(poweredUntil, heldUntil);
    }

    /**
     * Whether the device has power now: a supply's lease hasn't run out, or is {@linkplain #hold()
     * held}, or it is test-powered.
     *
     * @return whether it may work
     */
    public boolean isPowered() {
        return testPowered || clock.getAsLong() < leaseEnd();
    }

    /**
     * Whether a provider has supplied the device within the last step, so that a provider covering
     * it a second time, or taking it over in the step another paid for, needn't pay again.
     *
     * @return whether this step is paid for
     */
    public boolean suppliedThisStep() {
        return poweredUntil != Long.MIN_VALUE && clock.getAsLong() < poweredUntil - LEASE_TICKS + PERIOD;
    }

    /**
     * Whether the device has power that was paid for: it is test-powered, or its lease came from a
     * supply made while it drew more than nothing. A device whose draw rises during a lease taken at
     * no cost, such as a recharger given a battery, waits for a paid one before it does work.
     *
     * @return whether its power was paid for
     */
    public boolean paidFor() {
        return testPowered || (clock.getAsLong() < leaseEnd() && suppliedDraw > 0);
    }

    /**
     * Who supplied the device last, while its lease lasts.
     *
     * @return the provider, or null with no lease, and when test-powered alone
     */
    public @Nullable PowerProvider source() {
        return clock.getAsLong() < leaseEnd() ? source : null;
    }

    // ---- Tests and saving -------------------------------------------------------------------

    /**
     * Powers the device with no provider, for GameTests and test worlds only.
     *
     * @param on whether it is test-powered
     */
    public void setTestPowered(boolean on) {
        testPowered = on;
    }

    /**
     * Whether the device is test-powered.
     *
     * @return whether {@link #setTestPowered} switched it on
     */
    public boolean testPowered() {
        return testPowered;
    }

    /**
     * Saves what outlasts a reload: only the test flag. A lease or a claim never does.
     *
     * @param tag the device's tag
     */
    public void save(CompoundTag tag) {
        if (testPowered) {
            tag.putBoolean(TEST_POWERED, true);
        }
    }

    /**
     * Loads what {@link #save} wrote.
     *
     * @param tag the device's tag
     */
    public void load(CompoundTag tag) {
        testPowered = tag.getBoolean(TEST_POWERED);
    }
}
