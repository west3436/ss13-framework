package io.github.west3436.ss13.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One mod's table of adapters for other mods, and the loader that starts them.
 *
 * <p>An adapter is code that names another mod's classes, so it may only be loaded when that mod
 * is installed, in a version the adapter was written for. A mod that has adapters lists them in a
 * table, one {@link Row} per target, made with {@link #of}, and calls {@link #load} once, last in
 * its constructor. For each row:
 *
 * <ul>
 *   <li>target not installed: nothing is loaded, and nothing is logged above debug;
 *   <li>target installed in a version outside the row's range: the adapter stays off, and one
 *       warning names the owning mod, the target, its version and the range;
 *   <li>otherwise the row's entry class is loaded by name and its {@code public static void
 *       init(IEventBus modBus, ModContainer container)} is called, with the owning mod's bus and
 *       container.
 * </ul>
 *
 * <p>An entry class that can't be loaded, or whose {@code init} throws, doesn't stop the game: the
 * fault is logged once with its stack trace and that adapter is off. The other rows still load.
 * What it had already registered stays registered, so an {@code init} should do anything that can
 * fail first.
 *
 * <p>No {@code neoforge.mods.toml} needs to name a target, so the target may be constructed after
 * the owning mod. An {@code init} therefore only registers: listeners, deferred registers,
 * capabilities. It calls into the target later, from those.
 *
 * <p>Several mods may each have a table, with rows for the same target; each table answers for
 * its own adapters. An adapter that its target starts itself, such as a Jade or JEI plugin, has no
 * row: it asks a {@link PluginGate} instead.
 */
public final class Adapters {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<Row> rows;

    /** What {@link #load} found, in the table's order. Empty until it has run. */
    private volatile List<Status> statuses = List.of();

    private boolean loaded;

    private Adapters(List<Row> rows) {
        this.rows = rows;
    }

    /**
     * A table. Nothing is loaded until {@link #load}, so a mod may keep its table in a static field
     * and a unit test may read its rows.
     *
     * @param rows one row per target, in the order they load
     * @return the table
     * @throws IllegalArgumentException if two rows name one target, so that one warning and one
     *     answer to {@link #isActive} are all a target can have
     */
    public static Adapters of(List<Row> rows) {
        return new Adapters(table(rows));
    }

    /**
     * One adapter.
     *
     * @param modId the target's mod id
     * @param range the versions of the target the adapter supports, written as in {@code
     *     neoforge.mods.toml} (see {@link VersionGate})
     * @param entryClass the binary name of the class whose {@code init} starts the adapter, as text
     *     so that nothing loads the class before the gate has passed it. It is public and has a
     *     {@code public static void init(IEventBus modBus, ModContainer container)}.
     */
    public record Row(String modId, String range, String entryClass) {
        /** @throws IllegalArgumentException if the range isn't one with bounds */
        public Row {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(entryClass, "entryClass");
            VersionGate.range(Objects.requireNonNull(range, "range"));
        }
    }

    /** How an adapter stands once its table has loaded. */
    public enum State {
        /** Its target isn't installed. */
        ABSENT,
        /** Its target is installed in a version outside the row's range, so it is off. */
        UNSUPPORTED,
        /** Its entry class couldn't be loaded or threw, so it is off. */
        FAILED,
        /** Its entry class's {@code init} returned. */
        ACTIVE
    }

    /**
     * An adapter and how it stands.
     *
     * @param row its row
     * @param state how it stands
     * @param version the installed version of its target, or null if the target is absent
     */
    public record Status(Row row, State state, @Nullable String version) {}

    /**
     * Starts every adapter in the table whose target is installed in a supported version. Call it
     * once, last in the owning mod's constructor, so that an entry class finds the mod set up.
     *
     * @param modBus the owning mod's bus, handed to each entry class
     * @param container the owning mod's container, handed to each entry class
     * @throws IllegalStateException if the table has already loaded
     */
    public void load(IEventBus modBus, ModContainer container) {
        synchronized (this) {
            if (loaded) {
                throw new IllegalStateException("The adapter table of " + container.getModId() + " has already loaded");
            }
            loaded = true;
        }
        statuses = load(rows, Adapters::installedVersion, modBus, container);
    }

    /**
     * Goes through a table as {@link #load(IEventBus, ModContainer)} does, with the installed
     * versions given, and returns what it found without recording it. This is how a test loads
     * rows of its own, or mocks a version.
     *
     * @param rows the table
     * @param versions the installed version of a mod id, empty if that mod is absent
     * @param modBus handed to each entry class
     * @param container the owning mod's container, handed to each entry class
     * @return each row and how it stands, in the table's order
     * @throws IllegalArgumentException if two rows name one target
     */
    public static List<Status> load(
            List<Row> rows, Function<String, Optional<String>> versions, IEventBus modBus, ModContainer container) {
        table(rows);
        List<Status> found = new ArrayList<>(rows.size());
        for (Row row : rows) {
            String version = versions.apply(row.modId()).orElse(null);
            found.add(new Status(row, start(row, version, modBus, container), version));
        }
        return List.copyOf(found);
    }

    /**
     * The installed version of a mod, as its metadata gives it.
     *
     * @param modId the mod
     * @return its version, or empty if no such mod is installed
     */
    public static Optional<String> installedVersion(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString());
    }

    /**
     * Whether this table's adapter for a mod is running: its target is installed in a supported
     * version and its entry class started. False for a mod with no row, and for every mod before
     * {@link #load} has run. Any thread.
     *
     * @param modId the target's mod id
     * @return true if the adapter is on
     */
    public boolean isActive(String modId) {
        return status(modId).map(status -> status.state() == State.ACTIVE).orElse(false);
    }

    /**
     * How this table's adapter for a mod stands. Any thread.
     *
     * @param modId the target's mod id
     * @return its status, or empty if the table has no row for that mod or hasn't loaded
     */
    public Optional<Status> status(String modId) {
        for (Status status : statuses) {
            if (status.row().modId().equals(modId)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /**
     * Every adapter in the table and how it stands, in the table's order. Empty before {@link
     * #load} has run. Any thread.
     *
     * @return the statuses
     */
    public List<Status> statuses() {
        return statuses;
    }

    /**
     * The table's rows.
     *
     * @return the rows, in the order they load
     */
    public List<Row> rows() {
        return rows;
    }

    private static List<Row> table(List<Row> rows) {
        Set<String> targets = new HashSet<>();
        for (Row row : rows) {
            if (!targets.add(row.modId())) {
                throw new IllegalArgumentException("Two adapter rows for " + row.modId());
            }
        }
        return List.copyOf(rows);
    }

    private static State start(Row row, @Nullable String version, IEventBus modBus, ModContainer container) {
        String owner = container.getModId();
        switch (VersionGate.check(version, row.range())) {
            case ABSENT -> {
                LOGGER.debug("{} isn't installed, so the {} adapter for it stays off", row.modId(), owner);
                return State.ABSENT;
            }
            case UNSUPPORTED -> {
                LOGGER.warn(
                        "The {} adapter for {} is off: it supports versions {}, and {} is installed",
                        owner,
                        row.modId(),
                        row.range(),
                        version);
                return State.UNSUPPORTED;
            }
            case SUPPORTED -> {}
        }
        try {
            enter(row.entryClass(), modBus, container);
        } catch (Exception | LinkageError e) {
            LOGGER.error("The {} adapter for {} {} failed to start, so it is off", owner, row.modId(), version, e);
            return State.FAILED;
        }
        LOGGER.info("The {} adapter for {} {} is on", owner, row.modId(), version);
        return State.ACTIVE;
    }

    /** Loads an entry class and calls its {@code init}, throwing what that threw. */
    private static void enter(String entryClass, IEventBus modBus, ModContainer container) throws Exception {
        try {
            // Every mod is in the game's one module layer, which this class's loader serves.
            Class.forName(entryClass, true, Adapters.class.getClassLoader())
                    .getMethod("init", IEventBus.class, ModContainer.class)
                    .invoke(null, modBus, container);
        } catch (InvocationTargetException e) {
            // What init threw, without reflection's wrapper around it.
            if (e.getCause() instanceof Exception thrown) {
                throw thrown;
            }
            if (e.getCause() instanceof Error thrown) {
                throw thrown;
            }
            throw e;
        }
    }
}
