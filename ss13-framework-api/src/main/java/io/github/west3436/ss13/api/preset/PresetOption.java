package io.github.west3436.ss13.api.preset;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One option the {@link Preset} sets: a default under each preset, and an optional override that
 * holds under either preset, so it survives a switch. Made by {@link Presets#register}.
 *
 * <p>{@link #get()} gives the effective value and {@link #source()} says where it comes from. Both
 * read the world's settings on each call, so a switch, an override or a hand edit of the preset
 * file applies from the next read: read the option whenever the value is needed rather than keeping
 * it. While no world is loaded, as on a client or before a server starts, every option has its
 * {@link Preset#NORMAL} default. Reading may happen on any thread; changing it, on the server
 * thread.
 *
 * @param <T> the type of the option's values
 */
public final class PresetOption<T> {
    /** In the preset file, the value of an option with no override: the active preset's applies. */
    public static final String FOLLOW_PRESET = "preset";

    /** Where an option's effective value comes from. */
    public enum Source {
        /** The active preset's default. */
        PRESET,
        /** An override, which holds under either preset. */
        OVERRIDE;

        /**
         * The lower-case name commands show, such as {@code "override"}.
         *
         * @return the name
         */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ResourceLocation id;
    private final PresetType<T> type;
    private final T normal;
    private final T hardcore;
    private final String description;

    PresetOption(ResourceLocation id, PresetType<T> type, T normal, T hardcore, String description) {
        this.id = id;
        this.type = type;
        this.normal = normal;
        this.hardcore = hardcore;
        this.description = description;
    }

    /**
     * The option's id: its mod's namespace and its key in the preset file, such as {@code
     * zas:combustion_forms_zones}.
     *
     * @return the id
     */
    public ResourceLocation id() {
        return id;
    }

    /**
     * How its values are checked, stored and shown.
     *
     * @return the type
     */
    public PresetType<T> type() {
        return type;
    }

    /**
     * What it controls, one sentence, for the preset file's comment.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * The default under a preset, ignoring any override.
     *
     * @param preset the preset
     * @return its default
     */
    public T defaultFor(Preset preset) {
        return Objects.requireNonNull(preset, "preset") == Preset.HARDCORE ? hardcore : normal;
    }

    /**
     * The effective value: the override if there is one, else the active preset's default.
     *
     * @return the value
     */
    public T get() {
        return get(Presets.active());
    }

    /**
     * The value under a preset: the override if there is one, else that preset's default.
     *
     * @param preset the preset
     * @return the value
     */
    public T get(Preset preset) {
        return override().orElseGet(() -> defaultFor(preset));
    }

    /**
     * The override, if any. A stored value the type doesn't accept counts as none.
     *
     * @return the override; empty while no world is loaded
     */
    public Optional<T> override() {
        PresetStore store = Presets.store();
        if (!store.isLoaded()) {
            return Optional.empty();
        }
        return store.override(id).filter(raw -> !followsPreset(raw)).flatMap(type::parse);
    }

    /**
     * Where the effective value comes from.
     *
     * @return the source
     */
    public Source source() {
        return override().isPresent() ? Source.OVERRIDE : Source.PRESET;
    }

    /**
     * Overrides the option under either preset, and saves the change to the world's preset file.
     * Server thread.
     *
     * @param value the value
     * @throws IllegalStateException if no world is loaded
     */
    public void setOverride(T value) {
        Objects.requireNonNull(value, "value");
        Presets.loadedStore().setOverride(id, type.write(value));
    }

    /**
     * Removes the override, so the preset's default applies again, and saves the change to the
     * world's preset file. Server thread.
     *
     * @throws IllegalStateException if no world is loaded
     */
    public void clearOverride() {
        Presets.loadedStore().clearOverride(id);
    }

    /**
     * A value as commands and the preset file's comment show it.
     *
     * @param value the value
     * @return its text
     */
    public String format(T value) {
        return type.format(value);
    }

    @Override
    public String toString() {
        return "PresetOption[" + id + "]";
    }

    /**
     * Whether a stored value says to follow the preset: {@value #FOLLOW_PRESET}, ignoring case.
     *
     * @param raw a stored value; may be null
     * @return true if it does
     */
    public static boolean followsPreset(@Nullable Object raw) {
        return raw instanceof String s && s.trim().equalsIgnoreCase(FOLLOW_PRESET);
    }
}
