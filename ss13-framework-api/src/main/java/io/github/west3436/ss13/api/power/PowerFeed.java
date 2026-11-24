package io.github.west3436.ss13.api.power;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How a provider shares one step's power among the devices it covers when its store may not meet
 * the demand.
 *
 * <p><b>Groups.</b> Devices are grouped by {@link PowerChannel channel} and, inside it, by {@link
 * PowerPriority priority}, and the groups are served in that order: the environment first, highest
 * priority first, then equipment, then lighting. Within a group, devices are served in the order
 * given, and each whose cost fits what is left is powered. If any device of a group misses out, the
 * groups after it get nothing, so a cheap light never runs while a vent is starved. A device that
 * draws nothing now is always powered, at no cost, on a channel that is on.
 *
 * <p>{@link #plan} is pure, with no world access. {@link #serve} is a provider's whole step: claims,
 * channels and the plan.
 */
public final class PowerFeed {
    private PowerFeed() {}

    /**
     * One device's demand for this step.
     *
     * @param channel its channel
     * @param priority its priority inside the channel
     * @param cost what one step of power costs, in FE ({@link DevicePower#drawPerStep})
     */
    public record Demand(PowerChannel channel, PowerPriority priority, long cost) {
        /**
         * Checks the demand.
         *
         * @param channel its channel
         * @param priority its priority inside the channel
         * @param cost what one step of power costs, in FE
         */
        public Demand {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(priority, "priority");
        }
    }

    /**
     * The outcome of {@link #plan}.
     *
     * @param powered for each demand, in the order given, whether it is powered
     * @param spent the FE taken from the store
     */
    public record Plan(boolean[] powered, long spent) {}

    /**
     * Shares a store among demands, with every channel on.
     *
     * @param store the FE available
     * @param demands the devices' demands, in the order to serve each group in
     * @return who is powered and what it costs
     */
    public static Plan plan(long store, List<Demand> demands) {
        boolean[] powered = new boolean[demands.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < demands.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> demands.get(i).channel())
                .thenComparing(i -> demands.get(i).priority()));
        long left = store;
        boolean starved = false;
        Demand group = null;
        boolean groupMissed = false;
        for (int i : order) {
            Demand d = demands.get(i);
            if (group == null || d.channel() != group.channel() || d.priority() != group.priority()) {
                starved |= groupMissed;
                group = d;
                groupMissed = false;
            }
            if (d.cost() <= 0) {
                powered[i] = true;
            } else if (!starved && d.cost() <= left) {
                powered[i] = true;
                left -= d.cost();
            } else {
                groupMissed = true;
            }
        }
        return new Plan(powered, store - left);
    }

    /** What became of one device in a provider's step ({@link #serve}). */
    public enum Outcome {
        /** The provider powered it this step. */
        SUPPLIED,
        /** The provider holds it, and its step was paid for already: covered twice, or just taken over. */
        PAID_ALREADY,
        /** Another provider, of higher precedence, holds it: this one only controls it, if anything. */
        HELD_BY_ANOTHER,
        /** The provider holds it, and its channel is off at the provider. */
        CHANNEL_OFF,
        /** The provider holds it, and the store ran short. */
        SHORT;

        /**
         * Whether the device has this provider's power for this step.
         *
         * @return true for {@link #SUPPLIED} and {@link #PAID_ALREADY}
         */
        public boolean powered() {
            return this == SUPPLIED || this == PAID_ALREADY;
        }
    }

    /**
     * The outcome of {@link #serve}.
     *
     * @param outcomes for each device, in the order given, what became of it
     * @param spent the FE the provider must take from its store
     */
    public record Served(List<Outcome> outcomes, long spent) {
        /**
         * Copies the outcomes.
         *
         * @param outcomes for each device, in the order given, what became of it
         * @param spent the FE the provider must take from its store
         */
        public Served {
            outcomes = List.copyOf(outcomes);
        }
    }

    /**
     * A provider's step: {@linkplain DevicePower#claim claims} each device it covers, and powers
     * those it holds on its channels that are on, sharing what its store has as {@link #plan} does.
     * A device it holds whose step another provider paid for already is left as it is, so taking
     * over never pays twice. A device listed twice is served once. The provider then takes {@link
     * Served#spent()} from its store; it is never more than {@code available}.
     *
     * @param provider the provider
     * @param devices the devices it covers, in the order to serve each group in
     * @param available the FE it can spend this step
     * @return what became of each device, and what it costs
     */
    public static Served serve(PowerProvider provider, List<? extends PoweredDevice> devices, long available) {
        Objects.requireNonNull(provider, "provider");
        Outcome[] outcomes = new Outcome[devices.size()];
        Map<PoweredDevice, Integer> first = new IdentityHashMap<>();
        List<Integer> asked = new ArrayList<>();
        List<Demand> demands = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            PoweredDevice device = devices.get(i);
            if (first.putIfAbsent(device, i) != null) {
                continue;
            }
            DevicePower p = device.power();
            if (!p.claim(provider)) {
                outcomes[i] = Outcome.HELD_BY_ANOTHER;
            } else if (!provider.channelOn(p.channel())) {
                outcomes[i] = Outcome.CHANNEL_OFF;
            } else if (p.suppliedThisStep()) {
                outcomes[i] = Outcome.PAID_ALREADY;
            } else {
                asked.add(i);
                demands.add(new Demand(p.channel(), p.priority(), p.drawPerStep()));
            }
        }
        Plan plan = plan(available, demands);
        for (int j = 0; j < asked.size(); j++) {
            int i = asked.get(j);
            boolean powered = plan.powered()[j] && devices.get(i).power().supply(provider);
            outcomes[i] = powered ? Outcome.SUPPLIED : Outcome.SHORT;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (outcomes[i] == null) {
                Outcome once = outcomes[first.get(devices.get(i))];
                outcomes[i] = once == Outcome.SUPPLIED ? Outcome.PAID_ALREADY : once;
            }
        }
        return new Served(Arrays.asList(outcomes), plan.spent());
    }
}
