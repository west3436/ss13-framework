package io.github.west3436.ss13.api.preset;

import java.util.Locale;
import java.util.Optional;

/**
 * The world-wide difficulty preset: one switch that sets the defaults of every mod's {@link
 * PresetOption options}. {@link Presets#active()} is the one in force.
 */
public enum Preset {
    /** The default: the gentler rules. */
    NORMAL,
    /** The harsher rules. */
    HARDCORE;

    /**
     * The lower-case name used in commands, such as {@code "hardcore"}.
     *
     * @return the name
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The preset a name stands for, ignoring case and surrounding spaces, as the preset file and
     * commands give it.
     *
     * @param name a name, such as {@code "hardcore"} or {@code "NORMAL"}
     * @return the preset, or empty if the name is none
     */
    public static Optional<Preset> byName(String name) {
        String trimmed = name.trim();
        for (Preset preset : values()) {
            if (preset.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }
}
