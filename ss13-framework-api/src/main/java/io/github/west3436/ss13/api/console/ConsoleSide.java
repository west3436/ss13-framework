package io.github.west3436.ss13.api.console;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Which column of a {@link ConsoleBlock console} a block is in, as seen from the console's front:
 * its {@code side} block state property.
 */
public enum ConsoleSide implements StringRepresentable {
    /** The left column, which holds the console's origin. */
    LEFT,
    /** The right column. */
    RIGHT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
