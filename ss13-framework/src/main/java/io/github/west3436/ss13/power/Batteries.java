package io.github.west3436.ss13.power;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * What counts as a battery: an item in the {@code ss13_framework:batteries} tag that exposes the FE
 * item capability. The tag holds the framework's three batteries; a pack can add other mods' FE items
 * to it. A device's battery slot and a recharger take only these.
 */
public final class Batteries {
    /** The battery tag, {@code ss13_framework:batteries}. */
    public static final TagKey<Item> TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "batteries"));

    private Batteries() {}

    /**
     * Whether a stack is a battery: in the tag, with an FE item capability.
     *
     * @param stack the stack
     * @return whether it is one
     */
    public static boolean isBattery(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TAG) && stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    /**
     * A battery's FE store.
     *
     * @param stack the stack
     * @return its store, or null if the stack isn't a battery
     */
    public static @Nullable IEnergyStorage energy(ItemStack stack) {
        return isBattery(stack) ? stack.getCapability(Capabilities.EnergyStorage.ITEM) : null;
    }

    /**
     * A one-battery slot: it takes one item, and only a battery. A device saves it under {@link
     * #SLOT_KEY} and drops its battery when broken.
     */
    public static class Slot extends ItemStackHandler {
        /** The key a device saves its slot under. */
        public static final String SLOT_KEY = "Battery";

        private final Runnable changed;

        /**
         * Makes an empty slot.
         *
         * @param changed called after its contents change, to mark the device dirty
         */
        public Slot(Runnable changed) {
            super(1);
            this.changed = changed;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed.run();
        }

        /**
         * The battery in the slot.
         *
         * @return the battery, or an empty stack
         */
        public ItemStack battery() {
            return getStackInSlot(0);
        }

        /**
         * The battery's FE store.
         *
         * @return its store, or null with no battery
         */
        public @Nullable IEnergyStorage energy() {
            return Batteries.energy(battery());
        }

        /**
         * Saves the slot into a device's tag.
         *
         * @param tag the device's tag
         * @param registries the registries
         */
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put(SLOT_KEY, serializeNBT(registries));
        }

        /**
         * Loads what {@link #save} wrote.
         *
         * @param tag the device's tag
         * @param registries the registries
         */
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
            if (tag.contains(SLOT_KEY)) {
                deserializeNBT(registries, tag.getCompound(SLOT_KEY));
            }
        }
    }
}
