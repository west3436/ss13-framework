package io.github.west3436.ss13.api.preset;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The world-wide Normal/Hardcore switch, and the options each mod registers with it.
 *
 * <p><b>Registering.</b> A mod registers each of its options once, usually as a static field set
 * during mod construction, with {@link #register}: an id in the mod's namespace, a {@link
 * PresetType}, and its default under each {@link Preset}. It reads the option's {@link
 * PresetOption#get()} whenever it needs the value. One switch then sets every mod's options at
 * once, and an override set on one option holds under either preset.
 *
 * <p><b>Where it is kept.</b> The {@code ss13_framework} mod keeps a world's preset and overrides
 * in a TOML file, {@value #FILE_NAME}, read as the server starts: the world's own {@code
 * serverconfig/}{@value #FILE_NAME} if it has one, else the shared {@code config/}{@value
 * #FILE_NAME}, which is written with the defaults if it doesn't exist. A change is saved at once:
 * on a dedicated server to the file that was read; in a singleplayer world to the world's own copy,
 * which the first change makes, leaving the shared file to the other worlds. The file has the
 * preset under {@code preset} and each option under {@code [options.<namespace>]}, as {@value
 * PresetOption#FOLLOW_PRESET} or an override. A hand edit of the file applies within a second.
 *
 * <p><b>The command.</b> {@code /ss13 preset} shows the preset and every option, {@code /ss13
 * preset normal|hardcore} switches, {@code /ss13 preset override <option> <value>} overrides an
 * option and {@code /ss13 preset clear <option>} removes the override. An option is named by its
 * id, or by its key alone where no other mod's option has that key. It needs permission level 2. A
 * mod can offer the same command under its own command root with {@link #addCommandAlias}.
 *
 * <p>Thread: registering may happen on any thread; reading too. Switching and overriding happen on
 * the server thread.
 */
public final class Presets {
    /** The preset file's name, in {@code config/} and in a world's {@code serverconfig/}. */
    public static final String FILE_NAME = "ss13_framework-presets.toml";

    /** The registered options in order, replaced whole on every registration. */
    private static volatile List<PresetOption<?>> options = List.of();

    /** The namespaces whose command roots offer the preset command, in order. */
    private static volatile List<String> aliases = List.of();

    private static volatile PresetStore store = PresetStore.NONE;

    private Presets() {}

    /**
     * Registers an option, usually during mod construction.
     *
     * @param id the option's id: the mod's namespace, and its key in the preset file and commands,
     *     such as {@code zas:combustion_forms_zones}; never in the {@code minecraft} namespace
     * @param type how its values are checked, stored and shown
     * @param normal its default under {@link Preset#NORMAL}
     * @param hardcore its default under {@link Preset#HARDCORE}
     * @param description what it controls, one sentence, for the preset file's comment
     * @param <T> the type of its values
     * @return the option, to keep and read
     * @throws IllegalArgumentException if an option with this id is already registered, if the id
     *     is in the {@code minecraft} namespace, or if the type doesn't accept a default as it
     *     stores it
     */
    public static synchronized <T> PresetOption<T> register(
            ResourceLocation id, PresetType<T> type, T normal, T hardcore, String description) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(hardcore, "hardcore");
        Objects.requireNonNull(description, "description");
        if (id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            throw new IllegalArgumentException("a preset option needs its mod's namespace, not " + id);
        }
        for (T value : List.of(normal, hardcore)) {
            if (type.parse(type.write(value)).isEmpty()) {
                throw new IllegalArgumentException(
                        "preset option " + id + "'s type doesn't accept its own default " + value);
            }
        }
        if (options.stream().anyMatch(o -> o.id().equals(id))) {
            throw new IllegalArgumentException("a preset option is already registered as " + id);
        }
        PresetOption<T> option = new PresetOption<>(id, type, normal, hardcore, description);
        List<PresetOption<?>> next = new ArrayList<>(options);
        next.add(option);
        options = List.copyOf(next);
        return option;
    }

    /**
     * Every registered option, in the order they were registered.
     *
     * @return the options
     */
    public static List<PresetOption<?>> options() {
        return options;
    }

    /**
     * The option with an id.
     *
     * @param id the id
     * @return the option, or empty if none is registered as it
     */
    public static Optional<PresetOption<?>> option(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        for (PresetOption<?> option : options) {
            if (option.id().equals(id)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a world's preset is loaded: on a server, from its start until it stops. While it isn't,
     * the preset is {@link Preset#NORMAL} and no option is overridden.
     *
     * @return true if it is
     */
    public static boolean isLoaded() {
        return store.isLoaded();
    }

    /**
     * The active preset.
     *
     * @return the preset; {@link Preset#NORMAL} while no world is loaded
     */
    public static Preset active() {
        PresetStore s = store;
        return s.isLoaded() ? s.preset() : Preset.NORMAL;
    }

    /**
     * Switches the preset for every mod, keeping every override, and saves the change to the
     * world's preset file. Server thread.
     *
     * @param preset the preset
     * @throws IllegalStateException if no world is loaded
     */
    public static void switchTo(Preset preset) {
        Objects.requireNonNull(preset, "preset");
        loadedStore().switchTo(preset);
    }

    /**
     * How many registered options are overridden.
     *
     * @return the count; 0 while no world is loaded
     */
    public static int overridden() {
        int n = 0;
        for (PresetOption<?> option : options) {
            if (option.source() == PresetOption.Source.OVERRIDE) {
                n++;
            }
        }
        return n;
    }

    /**
     * Offers the preset command under a mod's own command root as well, as {@code /<namespace>
     * preset}, where an option's key alone names that mod's option first. The mod registers the
     * root itself, in a {@code RegisterCommandsEvent} listener of any priority but the lowest; the
     * framework adds {@code preset} under it at the lowest priority. The alias has no permission
     * check of its own, so the root's applies. Call it during mod construction.
     *
     * @param namespace the mod's namespace, which is also the command root, such as {@code "zas"}
     * @throws IllegalArgumentException if the namespace isn't a valid one
     */
    public static synchronized void addCommandAlias(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isEmpty() || !ResourceLocation.isValidNamespace(namespace)) {
            throw new IllegalArgumentException("not a namespace: " + namespace);
        }
        Set<String> next = new LinkedHashSet<>(aliases);
        next.add(namespace);
        aliases = List.copyOf(next);
    }

    /**
     * The namespaces whose command roots offer the preset command, in the order they were added.
     *
     * @return the namespaces
     */
    public static List<String> commandAliases() {
        return aliases;
    }

    /**
     * Installs where the preset is kept. The {@code ss13_framework} mod does this once, as it is
     * constructed; mods never do, except in tests.
     *
     * @param next the store
     * @return the store it replaces, {@link PresetStore#NONE} if none was installed
     */
    public static synchronized PresetStore install(PresetStore next) {
        Objects.requireNonNull(next, "next");
        PresetStore previous = store;
        store = next;
        return previous;
    }

    static PresetStore store() {
        return store;
    }

    static PresetStore loadedStore() {
        PresetStore s = store;
        if (!s.isLoaded()) {
            throw new IllegalStateException("no world's presets are loaded");
        }
        return s;
    }
}
