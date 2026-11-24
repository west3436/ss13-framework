package io.github.west3436.ss13.api.power;

import java.util.Objects;
import java.util.function.IntSupplier;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * An FE store with a configured capacity, exposed through NeoForge's FE capability. Its own block
 * fills or drains it through {@link #fill} and {@link #take}; other blocks see only the side it
 * allows: a provider's buffer takes FE in, and a generator's store gives FE out.
 *
 * <p>Its block saves it: {@link #getEnergyStored()} out, {@link #set} back in.
 */
public final class EnergyStore implements IEnergyStorage {
    private final IntSupplier capacity;
    private final boolean receives;
    private final boolean extracts;
    private final Runnable changed;
    private int energy;

    /**
     * Makes an empty store.
     *
     * @param capacity the capacity in FE, read each time, so a config change takes effect
     * @param receives whether other blocks may put FE in
     * @param extracts whether other blocks may take FE out
     * @param changed called after the store changes, to mark its block entity dirty
     */
    public EnergyStore(IntSupplier capacity, boolean receives, boolean extracts, Runnable changed) {
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.receives = receives;
        this.extracts = extracts;
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /**
     * Puts FE in from the block itself, whichever sides other blocks see.
     *
     * @param amount the FE offered
     * @return what fit
     */
    public int fill(int amount) {
        int moved = Math.max(0, Math.min(amount, space()));
        if (moved > 0) {
            energy += moved;
            changed.run();
        }
        return moved;
    }

    /**
     * Takes FE out for the block itself, whichever sides other blocks see.
     *
     * @param amount the FE wanted
     * @return whether it took it: false, taking nothing, if there isn't enough
     */
    public boolean take(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (amount > getEnergyStored()) {
            return false;
        }
        energy = getEnergyStored() - (int) amount;
        changed.run();
        return true;
    }

    /**
     * The room left.
     *
     * @return FE, at least 0
     */
    public int space() {
        return Math.max(0, getMaxEnergyStored() - getEnergyStored());
    }

    /**
     * Sets the stored FE, clamped to the capacity: for loading and tests.
     *
     * @param amount the FE
     */
    public void set(int amount) {
        energy = Math.max(0, Math.min(amount, getMaxEnergyStored()));
        changed.run();
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!receives || toReceive <= 0) {
            return 0;
        }
        int moved = Math.min(toReceive, space());
        if (!simulate && moved > 0) {
            energy = getEnergyStored() + moved;
            changed.run();
        }
        return moved;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!extracts || toExtract <= 0) {
            return 0;
        }
        int moved = Math.min(toExtract, getEnergyStored());
        if (!simulate && moved > 0) {
            energy = getEnergyStored() - moved;
            changed.run();
        }
        return moved;
    }

    @Override
    public int getEnergyStored() {
        return Math.min(energy, getMaxEnergyStored());
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(0, capacity.getAsInt());
    }

    @Override
    public boolean canExtract() {
        return extracts;
    }

    @Override
    public boolean canReceive() {
        return receives;
    }
}
