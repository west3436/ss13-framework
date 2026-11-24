package io.github.west3436.ss13.api.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** A device's lease and claim. */
class DevicePowerTest {
    /** A provider for tests: its precedence, and whether it still provides. */
    static class Provider implements PowerProvider {
        final int precedence;
        boolean providing = true;

        Provider(int precedence) {
            this.precedence = precedence;
        }

        @Override
        public int precedence() {
            return precedence;
        }

        @Override
        public boolean providing() {
            return providing;
        }
    }

    private static DevicePower power(long[] clock, int[] draw) {
        return new DevicePower(PowerChannel.ENVIRONMENT, PowerPriority.HIGH, () -> draw[0], () -> clock[0]);
    }

    @Test
    void a_device_has_no_power_until_supplied_and_a_lease_lasts_two_steps() {
        long[] clock = {1000};
        DevicePower p = power(clock, new int[] {10});
        assertFalse(p.isPowered());
        assertNull(p.source());
        assertFalse(p.suppliedThisStep());

        Provider provider = new Provider(PowerProvider.BACKUP);
        assertTrue(p.supply(provider));
        assertTrue(p.isPowered());
        assertSame(provider, p.source());
        assertSame(provider, p.claimant(), "a supply claims");
        assertTrue(p.suppliedThisStep());

        clock[0] += DevicePower.PERIOD;
        assertTrue(p.isPowered(), "one step later");
        assertFalse(p.suppliedThisStep());
        clock[0] += DevicePower.LEASE_TICKS - DevicePower.PERIOD - 1;
        assertTrue(p.isPowered(), "the lease's last tick");
        clock[0]++;
        assertFalse(p.isPowered(), "after the lease");
        assertNull(p.source());
        assertNull(p.claimant(), "the claim lapsed with it");
    }

    /**
     * A device the schedule passes over keeps the power and claim it has: each hold makes them last
     * two steps from then, without counting as a supply. A device with no power gets none from a
     * hold.
     */
    @Test
    void a_device_that_waits_keeps_its_power_and_its_claim() {
        long[] clock = {1000};
        DevicePower p = power(clock, new int[] {10});
        p.hold();
        assertFalse(p.isPowered(), "a hold with no lease");

        Provider provider = new Provider(PowerProvider.MAINS);
        p.supply(provider);
        for (int i = 0; i < 10; i++) {
            clock[0] += DevicePower.PERIOD;
            p.hold();
            assertTrue(p.isPowered(), "held, after " + (i + 1) + " steps");
            assertTrue(p.paidFor(), "a held paid lease is paid for");
            assertSame(provider, p.source(), "the provider while held");
            assertSame(provider, p.claimant(), "the claim while held");
            assertFalse(p.suppliedThisStep(), "a hold counted as a supply");
        }
        clock[0] += DevicePower.LEASE_TICKS - 1;
        assertTrue(p.isPowered(), "the held lease's last tick");
        clock[0]++;
        assertFalse(p.isPowered(), "after the held lease");
        assertNull(p.source());
        p.hold();
        assertFalse(p.isPowered(), "a hold brought back a lease that had run out");
    }

    /** A provider of higher precedence takes a device over; one of lower precedence can't. */
    @Test
    void the_provider_of_highest_precedence_holds_the_claim() {
        long[] clock = {1000};
        DevicePower p = power(clock, new int[] {10});
        Provider controller = new Provider(PowerProvider.BACKUP);
        Provider apc = new Provider(PowerProvider.MAINS);

        assertTrue(p.claim(controller));
        assertTrue(p.claim(apc), "the APC takes over");
        assertFalse(p.claim(controller), "the controller can't take it back");
        assertFalse(p.supply(controller), "nor power it");
        assertNull(p.source(), "a refused supply powers nothing");
        assertSame(apc, p.claimant());
        assertTrue(p.supply(apc));
        assertSame(apc, p.source());

        // Between equals, the holder keeps it.
        Provider otherApc = new Provider(PowerProvider.MAINS);
        assertFalse(p.claim(otherApc));
        assertTrue(p.claim(apc));
    }

    /** When the provider that holds a device goes, the next claims it at once; and so on a release. */
    @Test
    void a_claim_ends_when_its_provider_stops_providing_or_releases_it() {
        long[] clock = {1000};
        DevicePower p = power(clock, new int[] {10});
        Provider controller = new Provider(PowerProvider.BACKUP);
        Provider apc = new Provider(PowerProvider.MAINS);
        p.supply(apc);
        assertFalse(p.claim(controller));

        apc.providing = false;
        assertNull(p.claimant(), "a removed provider holds nothing");
        assertTrue(p.isPowered(), "the lease it paid for lasts");
        assertTrue(p.supply(controller), "the controller takes over at once");
        assertSame(controller, p.source());
        assertFalse(p.claim(apc), "a provider that doesn't provide can't claim");

        Provider other = new Provider(PowerProvider.BACKUP);
        assertFalse(p.claim(other));
        p.release(other);
        assertSame(controller, p.claimant(), "releasing what another holds changes nothing");
        p.release(controller);
        assertNull(p.claimant());
        assertTrue(p.claim(other), "a released device is free to claim");
    }

    /** A claim that isn't renewed lapses after a lease, and a lower provider can then take it. */
    @Test
    void a_claim_not_renewed_lapses() {
        long[] clock = {1000};
        DevicePower p = power(clock, new int[] {10});
        Provider controller = new Provider(PowerProvider.BACKUP);
        Provider apc = new Provider(PowerProvider.MAINS);
        p.claim(apc);
        clock[0] += DevicePower.LEASE_TICKS - 1;
        assertFalse(p.claim(controller));
        clock[0]++;
        assertTrue(p.claim(controller), "the APC stopped claiming a lease ago");
    }

    /**
     * A lease supplied while the device drew nothing is power, but not paid-for power, so a recharger
     * given a battery mid-lease waits for a paid supply before it charges.
     */
    @Test
    void power_supplied_at_no_cost_is_not_paid_for() {
        long[] clock = {1000};
        int[] draw = {0};
        DevicePower p = power(clock, draw);
        Provider provider = new Provider(PowerProvider.BACKUP);
        assertFalse(p.paidFor(), "before any supply");
        p.supply(provider);
        draw[0] = 40;
        assertTrue(p.isPowered());
        assertFalse(p.paidFor(), "a lease taken at no cost");

        clock[0] += DevicePower.PERIOD;
        p.supply(provider);
        assertTrue(p.paidFor(), "a lease paid for");
        clock[0] += DevicePower.LEASE_TICKS;
        assertFalse(p.paidFor(), "after the lease");

        p.setTestPowered(true);
        assertTrue(p.paidFor(), "test-powered");
    }

    @Test
    void draw_is_per_game_tick_and_never_negative() {
        int[] draw = {10};
        DevicePower p = new DevicePower(PowerChannel.LIGHTING, PowerPriority.LOW, () -> draw[0], () -> 0L);
        assertEquals(10, p.draw());
        assertEquals(10L * DevicePower.PERIOD, p.drawPerStep());
        draw[0] = -5;
        assertEquals(0, p.draw());
        assertEquals(PowerChannel.LIGHTING, p.channel());
        assertEquals(PowerPriority.LOW, p.priority());
    }

    @Test
    void test_power_needs_no_provider_and_is_saved() {
        DevicePower p = new DevicePower(PowerChannel.EQUIPMENT, PowerPriority.NORMAL, () -> 1, () -> 0L);
        p.setTestPowered(true);
        assertTrue(p.isPowered());
        assertNull(p.source());
        CompoundTag tag = new CompoundTag();
        p.save(tag);
        DevicePower q = new DevicePower(PowerChannel.EQUIPMENT, PowerPriority.NORMAL, () -> 1, () -> 0L);
        q.load(tag);
        assertTrue(q.isPowered());
        DevicePower r = new DevicePower(PowerChannel.EQUIPMENT, PowerPriority.NORMAL, () -> 1, () -> 0L);
        r.load(new CompoundTag());
        assertFalse(r.isPowered());
    }

    @Test
    void channels_and_priorities_sort_in_the_order_served() {
        assertTrue(PowerChannel.ENVIRONMENT.compareTo(PowerChannel.EQUIPMENT) < 0);
        assertTrue(PowerChannel.EQUIPMENT.compareTo(PowerChannel.LIGHTING) < 0);
        assertTrue(PowerPriority.HIGH.compareTo(PowerPriority.NORMAL) < 0);
        assertTrue(PowerPriority.NORMAL.compareTo(PowerPriority.LOW) < 0);
        assertTrue(PowerProvider.BACKUP < PowerProvider.MAINS && PowerProvider.MAINS < PowerProvider.DIRECT);
    }
}
