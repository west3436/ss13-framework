package io.github.west3436.ss13.preset;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One world's preset and overrides, kept in a {@link PresetFile}: the world's own copy if it has
 * one, else the shared file.
 *
 * <p>{@link #load} reads the world's copy if it exists, else the shared file, which it writes with
 * the defaults if it doesn't exist; it writes the file it read again when the file lacks a
 * registered option, so every option is listed with its comment. A change is saved at once (see
 * {@link #target}): on a dedicated server to the file that was read, whichever it is; in a
 * singleplayer world to the world's copy, which the first change makes, leaving the shared file to
 * the other worlds. From then on the copy is the file this world reads and saves.
 *
 * <p>{@link #pickUpEdits} applies a hand edit of the file, looking at its time once a call.
 *
 * <p>Reads may happen on any thread; loading, changes and {@link #pickUpEdits} on the server thread.
 */
public final class WorldPresets implements PresetStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Where a save goes. */
    public enum Target {
        /** The file that was read. */
        IN_PLACE,
        /** A new copy in the world's {@code serverconfig/}. */
        NEW_COPY
    }

    /**
     * What a save did.
     *
     * @param file the file written
     * @param newCopy whether it made the world's copy
     */
    public record Saved(Path file, boolean newCopy) {}

    /** The preset and overrides; replaced whole on every change. */
    private record State(Preset preset, Map<ResourceLocation, Object> overrides) {}

    private final Path shared;
    private final Path worldCopy;
    private final boolean dedicated;

    private volatile @Nullable State state;

    /** The file this world reads and saves. Set by {@link #load}. */
    private volatile Path file;

    /** When {@link #file} last changed, as of this class's last read or write of it. */
    private @Nullable FileTime modified;

    /**
     * A world's presets, not yet loaded.
     *
     * @param shared the shared file, {@code config/}{@value io.github.west3436.ss13.api.preset.Presets#FILE_NAME}
     * @param worldCopy the world's own copy, in its {@code serverconfig/}
     * @param dedicated whether the server is dedicated
     */
    public WorldPresets(Path shared, Path worldCopy, boolean dedicated) {
        this.shared = Objects.requireNonNull(shared, "shared");
        this.worldCopy = Objects.requireNonNull(worldCopy, "worldCopy");
        this.dedicated = dedicated;
        this.file = shared;
    }

    /**
     * Where a save goes: in place on a dedicated server, which has one world, and for a world that
     * already reads its own copy; else to a new copy.
     *
     * @param dedicated whether the server is dedicated
     * @param file the file that was read
     * @param worldCopy the world's own copy
     * @return the target
     */
    public static Target target(boolean dedicated, Path file, Path worldCopy) {
        return dedicated || same(file, worldCopy) ? Target.IN_PLACE : Target.NEW_COPY;
    }

    /** Reads the world's presets; see the class doc. A file that can't be read gives the defaults. */
    public void load() {
        file = Files.exists(worldCopy) ? worldCopy : shared;
        PresetFile.Contents contents = PresetFile.Contents.DEFAULTS;
        boolean readable = false;
        if (Files.exists(file)) {
            Optional<PresetFile.Contents> read = read(file);
            readable = read.isPresent();
            contents = read.orElse(PresetFile.Contents.DEFAULTS);
        }
        state = new State(contents.preset(), contents.overrides());
        if (!Files.exists(file) || (readable && !contents.listsEveryOption())) {
            // Lists every option with its comment, so the file shows what can be set.
            try {
                write(file);
            } catch (UncheckedIOException e) {
                LOGGER.warn("Couldn't write {}: {}", file, e.getCause().toString());
            }
        }
        modified = modified(file);
        LOGGER.info(
                "Preset {} from {}, {} option(s) overridden",
                contents.preset(),
                file,
                contents.overrides().size());
    }

    /** Forgets the world's presets, as its server stops. */
    public void unload() {
        state = null;
    }

    /**
     * The file this world reads and saves: its own copy if it has one, including one made this
     * session, else the shared file.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }

    /**
     * Whether this world reads its own copy rather than the shared file.
     *
     * @return true if it does
     */
    public boolean hasOwnCopy() {
        return same(file, worldCopy);
    }

    /**
     * The shared file.
     *
     * @return the file
     */
    public Path sharedFile() {
        return shared;
    }

    @Override
    public boolean isLoaded() {
        return state != null;
    }

    @Override
    public Preset preset() {
        State s = state;
        return s == null ? Preset.NORMAL : s.preset();
    }

    @Override
    public Optional<Object> override(ResourceLocation option) {
        State s = state;
        return s == null ? Optional.empty() : Optional.ofNullable(s.overrides().get(option));
    }

    @Override
    public void switchTo(Preset preset) {
        saveLogged(() -> switchAndSave(preset));
    }

    @Override
    public void setOverride(ResourceLocation option, Object stored) {
        saveLogged(() -> overrideAndSave(option, stored));
    }

    @Override
    public void clearOverride(ResourceLocation option) {
        saveLogged(() -> clearAndSave(option));
    }

    /**
     * Switches the preset, keeping the overrides, and saves.
     *
     * @param preset the preset
     * @return what the save did
     * @throws IllegalStateException if nothing is loaded
     * @throws UncheckedIOException if the file can't be written; the change applies all the same
     */
    public Saved switchAndSave(Preset preset) {
        Objects.requireNonNull(preset, "preset");
        State s = loaded();
        state = new State(preset, s.overrides());
        return save();
    }

    /**
     * Overrides an option and saves.
     *
     * @param option the option's id
     * @param stored its value, as the file stores it
     * @return what the save did
     * @throws IllegalStateException if nothing is loaded
     * @throws UncheckedIOException if the file can't be written; the change applies all the same
     */
    public Saved overrideAndSave(ResourceLocation option, Object stored) {
        Objects.requireNonNull(option, "option");
        Objects.requireNonNull(stored, "stored");
        State s = loaded();
        Map<ResourceLocation, Object> next = new LinkedHashMap<>(s.overrides());
        next.put(option, stored);
        state = new State(s.preset(), Map.copyOf(next));
        return save();
    }

    /**
     * Removes an option's override and saves.
     *
     * @param option the option's id
     * @return what the save did
     * @throws IllegalStateException if nothing is loaded
     * @throws UncheckedIOException if the file can't be written; the change applies all the same
     */
    public Saved clearAndSave(ResourceLocation option) {
        Objects.requireNonNull(option, "option");
        State s = loaded();
        Map<ResourceLocation, Object> next = new LinkedHashMap<>(s.overrides());
        next.remove(option);
        state = new State(s.preset(), Map.copyOf(next));
        return save();
    }

    /**
     * Applies a hand edit of the file this world reads. A file that can't be read, as can happen
     * half way through an editor's save, leaves the presets as they are, and is read again when it
     * next changes.
     */
    public void pickUpEdits() {
        if (state == null) {
            return;
        }
        Path f = file;
        FileTime now = modified(f);
        if (now == null || now.equals(modified)) {
            return;
        }
        modified = now;
        read(f).ifPresent(contents -> {
            state = new State(contents.preset(), contents.overrides());
            LOGGER.info("Applied the edit of {}: preset {}", f, contents.preset());
        });
    }

    private Saved save() {
        State s = loaded();
        if (target(dedicated, file, worldCopy) == Target.NEW_COPY) {
            write(worldCopy, s);
            Path from = file;
            file = worldCopy;
            LOGGER.info("Wrote this world's own preset file, {}; {} is unchanged", worldCopy, from);
            return new Saved(worldCopy, true);
        }
        write(file, s);
        return new Saved(file, false);
    }

    private void write(Path to) {
        write(to, loaded());
    }

    private void write(Path to, State s) {
        PresetFile.write(to, s.preset(), s.overrides());
        modified = modified(to);
    }

    private static void saveLogged(Supplier<Saved> change) {
        try {
            change.get();
        } catch (UncheckedIOException e) {
            LOGGER.warn(
                    "Couldn't save the preset ({}); the change applies until the server stops",
                    e.getCause().toString());
        }
    }

    private State loaded() {
        State s = state;
        if (s == null) {
            throw new IllegalStateException("no world's presets are loaded");
        }
        return s;
    }

    private static Optional<PresetFile.Contents> read(Path file) {
        try {
            PresetFile.Contents contents = PresetFile.read(file);
            for (String warning : contents.warnings()) {
                LOGGER.warn("{}: {}", file, warning);
            }
            return Optional.of(contents);
        } catch (IOException | ParsingException e) {
            LOGGER.warn("Couldn't read {}, so the preset is as it was: {}", file, e.toString());
            return Optional.empty();
        }
    }

    private static @Nullable FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException gone) {
            return null;
        }
    }

    private static boolean same(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
