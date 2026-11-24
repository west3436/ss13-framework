package io.github.west3436.ss13.api.preset;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Where the world's preset and overrides are kept. The {@code ss13_framework} mod installs the one
 * that keeps them in the preset file ({@link Presets#FILE_NAME}); mods read and change them through
 * {@link Presets} and {@link PresetOption} instead, and never install another, except in tests.
 *
 * <p>Reads may happen on any thread; changes happen on the server thread.
 */
public interface PresetStore {
    /** The store while none is installed: nothing loaded. */
    PresetStore NONE = new PresetStore() {
        @Override
        public boolean isLoaded() {
            return false;
        }

        @Override
        public Preset preset() {
            return Preset.NORMAL;
        }

        @Override
        public Optional<Object> override(ResourceLocation option) {
            return Optional.empty();
        }

        @Override
        public void switchTo(Preset preset) {
            throw new IllegalStateException("no world's presets are loaded");
        }

        @Override
        public void setOverride(ResourceLocation option, Object stored) {
            throw new IllegalStateException("no world's presets are loaded");
        }

        @Override
        public void clearOverride(ResourceLocation option) {
            throw new IllegalStateException("no world's presets are loaded");
        }
    };

    /**
     * Whether a world's settings are loaded: on a server, from its start until it stops.
     *
     * @return true if they are
     */
    boolean isLoaded();

    /**
     * The active preset.
     *
     * @return the preset; {@link Preset#NORMAL} while nothing is loaded
     */
    Preset preset();

    /**
     * An option's stored override, as the file holds it: a value of its {@link PresetType}'s stored
     * form, which may not be valid, or {@link PresetOption#FOLLOW_PRESET}.
     *
     * @param option the option's id, which needn't be registered
     * @return the stored value, or empty if there is none
     */
    Optional<Object> override(ResourceLocation option);

    /**
     * Switches the preset, keeping every override, and saves the change.
     *
     * @param preset the preset
     * @throws IllegalStateException if nothing is loaded
     */
    void switchTo(Preset preset);

    /**
     * Overrides an option, and saves the change.
     *
     * @param option the option's id
     * @param stored the value in its stored form
     * @throws IllegalStateException if nothing is loaded
     */
    void setOverride(ResourceLocation option, Object stored);

    /**
     * Removes an option's override, and saves the change.
     *
     * @param option the option's id
     * @throws IllegalStateException if nothing is loaded
     */
    void clearOverride(ResourceLocation option);
}
