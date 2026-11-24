package io.github.west3436.ss13.api.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An FE store: its own block's side and the side other blocks see. */
class EnergyStoreTest {
    @Test
    void its_block_fills_and_takes_whatever_others_may_do() {
        int[] changes = {0};
        EnergyStore store = new EnergyStore(() -> 1000, false, false, () -> changes[0]++);
        assertEquals(1000, store.fill(1500), "fills to its capacity");
        assertEquals(0, store.space());
        assertTrue(store.take(400));
        assertFalse(store.take(601), "takes nothing when there isn't enough");
        assertEquals(600, store.getEnergyStored());
        assertTrue(store.take(0));
        assertEquals(2, changes[0]);
    }

    @Test
    void others_see_only_the_sides_it_allows() {
        EnergyStore buffer = new EnergyStore(() -> 1000, true, false, () -> {});
        assertEquals(700, buffer.receiveEnergy(700, false));
        assertEquals(300, buffer.receiveEnergy(500, true), "a simulated fill");
        assertEquals(700, buffer.getEnergyStored(), "a simulation changes nothing");
        assertEquals(0, buffer.extractEnergy(100, false), "a buffer gives nothing out");

        EnergyStore generator = new EnergyStore(() -> 1000, false, true, () -> {});
        generator.set(500);
        assertEquals(0, generator.receiveEnergy(100, false), "a generator takes nothing in");
        assertEquals(500, generator.extractEnergy(800, false));
        assertEquals(0, generator.getEnergyStored());
    }

    @Test
    void a_smaller_capacity_clamps_what_it_holds() {
        int[] capacity = {1000};
        EnergyStore store = new EnergyStore(() -> capacity[0], true, true, () -> {});
        store.set(900);
        capacity[0] = 500;
        assertEquals(500, store.getEnergyStored());
        assertFalse(store.take(600), "it can't take what it no longer holds");
        assertTrue(store.take(500));
        assertEquals(0, store.getEnergyStored());
    }
}
