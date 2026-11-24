package io.github.west3436.ss13.device;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

/**
 * The format version of what a device saves, and how an older save is brought forward. {@link
 * DeviceBlockEntity} does both, before any subclass reads the tag, so a subclass only reads and
 * writes its own fields.
 *
 * <p>A format is a list of steps: entry {@code i} changes version {@code i}'s tag, in place, into
 * version {@code i + 1}'s, and the version written is the list's size. Version 0 is a save with no
 * version, from before versions were added. A device keeps {@link #FIRST_FORMAT} until its format
 * changes, then returns a longer list from {@link DeviceBlockEntity#formatSteps()}. A new field that
 * reads as a default when it's missing needs no step.
 *
 * <p>Whatever else a mod saves can keep a list of its own, under the same key, and bring itself
 * forward with {@link #migrate(CompoundTag, List)}, as {@link ItemData} does.
 */
public final class DeviceData {
    /** The key of the format version, in a saved tag. */
    public static final String VERSION_KEY = "FormatVersion";

    /**
     * The steps of a format that hasn't changed since versions were added: one that does nothing, so
     * a save with no version reads as version 1, whose fields are those it always had.
     */
    public static final List<Consumer<CompoundTag>> FIRST_FORMAT = List.of(tag -> {});

    /** The format version a device that keeps {@link #FIRST_FORMAT} writes: 1. */
    public static final int VERSION = FIRST_FORMAT.size();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private DeviceData() {}

    /** Writes {@link #VERSION} into a tag being saved. */
    public static void writeVersion(CompoundTag tag) {
        tag.putInt(VERSION_KEY, VERSION);
    }

    /** Brings a saved tag forward to {@link #FIRST_FORMAT}, in place. */
    public static void migrate(CompoundTag tag) {
        migrate(tag, FIRST_FORMAT);
    }

    /**
     * Brings a saved tag forward with the given steps, in place. A newer version, from a newer mod,
     * is left as it is and read by name, as the fields are read one at a time and each has a default.
     * One warning is logged the first time, since a station holds thousands of block entities.
     *
     * @param tag the saved tag
     * @param steps entry {@code i} takes version {@code i} to {@code i + 1}
     * @return whether the tag's version is newer than the steps reach, so it was left as it is
     */
    public static boolean migrate(CompoundTag tag, List<Consumer<CompoundTag>> steps) {
        int version = tag.getInt(VERSION_KEY);
        if (version > steps.size()) {
            if (WARNED.compareAndSet(false, true)) {
                LOGGER.warn(
                        "Something was saved in format {}, and this version of its mod only knows up to {}. "
                                + "It reads the fields it recognises, and any others are lost at the next save",
                        version,
                        steps.size());
            }
            return true;
        }
        for (int v = Math.max(0, version); v < steps.size(); v++) {
            steps.get(v).accept(tag);
        }
        tag.putInt(VERSION_KEY, steps.size());
        return false;
    }
}
