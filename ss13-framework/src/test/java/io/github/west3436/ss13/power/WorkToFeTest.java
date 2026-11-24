package io.github.west3436.ss13.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.ss13.api.power.DevicePower;
import io.github.west3436.ss13.api.power.EnergyStore;
import io.github.west3436.ss13.device.DeviceTicks;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Work turned into FE, and the clock power and devices share. */
class WorkToFeTest {
    @Test
    void a_lease_is_counted_in_device_steps() {
        assertEquals(DeviceTicks.PERIOD, DevicePower.PERIOD, "a device step and a power step differ");
    }

    @Test
    void work_turns_into_whole_fe_and_keeps_the_rest() {
        EnergyStore store = new EnergyStore(() -> 100, false, true, () -> {});
        WorkToFe work = new WorkToFe();
        assertEquals(1000.0, work.room(store, 10.0));
        assertEquals(2, work.add(25.0, store, 10.0));
        assertEquals(5.0, work.carry());
        assertEquals(2, store.getEnergyStored());
        assertEquals(1, work.add(5.0, store, 10.0), "the kept work makes a whole FE");
        assertEquals(0.0, work.carry());
        assertEquals(0, work.add(Double.NaN, store, 10.0), "work that isn't a number is ignored");

        assertEquals(97, work.add(5000.0, store, 10.0), "no more than the room left");
        assertEquals(100, store.getEnergyStored());
        assertEquals(0.0, work.room(store, 10.0));
    }

    @Test
    void what_it_keeps_is_saved() {
        EnergyStore store = new EnergyStore(() -> 100, false, true, () -> {});
        WorkToFe work = new WorkToFe();
        work.add(7.5, store, 10.0);
        CompoundTag tag = new CompoundTag();
        work.save(tag);
        WorkToFe loaded = new WorkToFe();
        loaded.load(tag);
        assertEquals(7.5, loaded.carry());
        CompoundTag bad = new CompoundTag();
        bad.putDouble("WorkCarry", -3);
        loaded.load(bad);
        assertEquals(0.0, loaded.carry());
    }
}
