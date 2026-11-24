package io.github.west3436.ss13.api.power;

import static io.github.west3436.ss13.api.power.PowerChannel.ENVIRONMENT;
import static io.github.west3436.ss13.api.power.PowerChannel.EQUIPMENT;
import static io.github.west3436.ss13.api.power.PowerChannel.LIGHTING;
import static io.github.west3436.ss13.api.power.PowerPriority.HIGH;
import static io.github.west3436.ss13.api.power.PowerPriority.LOW;
import static io.github.west3436.ss13.api.power.PowerPriority.NORMAL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.west3436.ss13.api.power.PowerFeed.Outcome;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Sharing a provider's store by channel and priority, and a provider's step. */
class PowerFeedTest {
    private static PowerFeed.Demand d(PowerChannel c, PowerPriority p, long cost) {
        return new PowerFeed.Demand(c, p, cost);
    }

    @Test
    void everyone_is_powered_when_there_is_enough() {
        PowerFeed.Plan plan = PowerFeed.plan(
                1000, List.of(d(EQUIPMENT, NORMAL, 400), d(ENVIRONMENT, HIGH, 200), d(ENVIRONMENT, NORMAL, 400)));
        assertArrayEquals(new boolean[] {true, true, true}, plan.powered());
        assertEquals(1000, plan.spent());
    }

    @Test
    void the_environment_first_and_later_groups_stop_when_a_group_misses_out() {
        // 500 FE: both vents (200 each) run; the pump (400) misses out, so the cheap recharger (50)
        // and the light (10) get nothing.
        PowerFeed.Plan plan = PowerFeed.plan(
                500,
                List.of(
                        d(LIGHTING, NORMAL, 10),
                        d(EQUIPMENT, NORMAL, 50),
                        d(ENVIRONMENT, NORMAL, 400),
                        d(ENVIRONMENT, HIGH, 200),
                        d(ENVIRONMENT, HIGH, 200)));
        assertArrayEquals(new boolean[] {false, false, false, true, true}, plan.powered());
        assertEquals(400, plan.spent());
    }

    @Test
    void channels_go_before_priorities() {
        // 300 FE: the low-priority environment part runs before the high-priority light.
        PowerFeed.Plan plan =
                PowerFeed.plan(300, List.of(d(LIGHTING, HIGH, 200), d(ENVIRONMENT, LOW, 200), d(EQUIPMENT, HIGH, 100)));
        assertArrayEquals(new boolean[] {false, true, true}, plan.powered());
        assertEquals(300, plan.spent());
    }

    @Test
    void within_a_group_the_parts_that_fit_run_and_parts_drawing_nothing_always_run() {
        PowerFeed.Plan plan = PowerFeed.plan(
                300,
                List.of(
                        d(ENVIRONMENT, HIGH, 400),
                        d(ENVIRONMENT, HIGH, 200),
                        d(ENVIRONMENT, NORMAL, 100),
                        d(EQUIPMENT, NORMAL, 0)));
        assertArrayEquals(new boolean[] {false, true, false, true}, plan.powered());
        assertEquals(200, plan.spent());
        assertArrayEquals(
                new boolean[] {false},
                PowerFeed.plan(0, List.of(d(ENVIRONMENT, HIGH, 1))).powered());
    }

    // ---- serve --------------------------------------------------------------------------------

    /** A provider with channels that can be switched off. */
    static final class Apc extends DevicePowerTest.Provider {
        final Set<PowerChannel> on = EnumSet.allOf(PowerChannel.class);

        Apc(int precedence) {
            super(precedence);
        }

        @Override
        public boolean channelOn(PowerChannel channel) {
            return on.contains(channel);
        }
    }

    record Device(DevicePower power) implements PoweredDevice {
        Device(long[] clock, PowerChannel channel, int draw) {
            this(new DevicePower(channel, NORMAL, () -> draw, () -> clock[0]));
        }
    }

    @Test
    void serve_powers_what_it_holds_on_its_channels_that_are_on() {
        long[] clock = {1000};
        Device vent = new Device(clock, ENVIRONMENT, 10);
        Device recharger = new Device(clock, EQUIPMENT, 10);
        Device light = new Device(clock, LIGHTING, 10);
        Apc apc = new Apc(PowerProvider.MAINS);
        apc.on.remove(LIGHTING);

        PowerFeed.Served s = PowerFeed.serve(apc, List.of(vent, recharger, light), 10_000);
        assertEquals(List.of(Outcome.SUPPLIED, Outcome.SUPPLIED, Outcome.CHANNEL_OFF), s.outcomes());
        assertEquals(2 * 10 * DevicePower.PERIOD, s.spent());
        assertSame(apc, light.power().claimant(), "a channel switched off keeps the device held");
        assertEquals(false, light.power().isPowered());
    }

    @Test
    void serve_leaves_alone_what_another_holds_and_never_pays_twice() {
        long[] clock = {1000};
        Device vent = new Device(clock, ENVIRONMENT, 10);
        Device pump = new Device(clock, ENVIRONMENT, 10);
        DevicePowerTest.Provider controller = new DevicePowerTest.Provider(PowerProvider.BACKUP);
        Apc apc = new Apc(PowerProvider.MAINS);

        // The controller steps first and pays for both.
        PowerFeed.Served first = PowerFeed.serve(controller, List.of(vent, pump), 10_000);
        assertEquals(List.of(Outcome.SUPPLIED, Outcome.SUPPLIED), first.outcomes());
        // The APC covers the vent only, takes it over, and doesn't pay for the step already paid.
        PowerFeed.Served taken = PowerFeed.serve(apc, List.of(vent), 10_000);
        assertEquals(List.of(Outcome.PAID_ALREADY), taken.outcomes());
        assertEquals(0, taken.spent());

        clock[0] += DevicePower.PERIOD;
        PowerFeed.Served controllerAgain = PowerFeed.serve(controller, List.of(vent, pump), 10_000);
        assertEquals(List.of(Outcome.HELD_BY_ANOTHER, Outcome.SUPPLIED), controllerAgain.outcomes());
        assertEquals(10 * DevicePower.PERIOD, controllerAgain.spent());
        PowerFeed.Served apcAgain = PowerFeed.serve(apc, List.of(vent, vent), 10_000);
        assertEquals(List.of(Outcome.SUPPLIED, Outcome.PAID_ALREADY), apcAgain.outcomes(), "listed twice");
        assertEquals(10 * DevicePower.PERIOD, apcAgain.spent());
        assertSame(apc, vent.power().source());
    }

    @Test
    void serve_reports_a_short_store() {
        long[] clock = {1000};
        Device vent = new Device(clock, ENVIRONMENT, 10);
        Device light = new Device(clock, LIGHTING, 10);
        Apc apc = new Apc(PowerProvider.MAINS);
        PowerFeed.Served s = PowerFeed.serve(apc, List.of(light, vent), 10 * DevicePower.PERIOD);
        assertEquals(List.of(Outcome.SHORT, Outcome.SUPPLIED), s.outcomes());
        assertEquals(10 * DevicePower.PERIOD, s.spent());
        assertEquals(false, s.outcomes().get(0).powered());
        assertEquals(true, s.outcomes().get(1).powered());
    }
}
