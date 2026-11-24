package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.Row;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.ss13.compat.PluginGate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * The adapter loader in a running game, with the framework as the owning mod. Minecraft and
 * NeoForge stand in for targets, since they are always installed; the entry classes are this
 * class's own.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdapterTests {
    private static final String ABSENT = "ss13_framework_no_such_mod";

    private AdapterTests() {}

    /**
     * A table with a supported target, an unsupported one and an absent one: only the first
     * adapter starts, with the owning mod's bus and container, and one warning names the second.
     */
    @GameTest(template = "empty")
    public static void a_table_starts_only_the_adapters_whose_target_is_installed_in_a_supported_version(
            GameTestHelper h) {
        ModContainer framework = framework();
        Row supported = new Row("minecraft", "[1.21,1.22)", Started.class.getName());
        Row unsupported = new Row("neoforge", "[999,)", Never.class.getName());
        Row absent = new Row(ABSENT, "[1.0,)", Never.class.getName());
        Adapters table = Adapters.of(List.of(supported, unsupported, absent));
        int before = Started.calls.get();
        try (Heard log = new Heard()) {
            table.load(framework.getEventBus(), framework);
            h.assertValueEqual(log.at(Level.WARN, FrameworkApi.MOD_ID, "neoforge", "[999,)"), 1, "warnings");
            h.assertValueEqual(log.at(Level.INFO, FrameworkApi.MOD_ID, "minecraft", "is on"), 1, "lines saying on");
            h.assertValueEqual(log.events.size(), 2, "lines logged at info and above");
        }
        h.assertValueEqual(Started.calls.get(), before + 1, "calls of the supported adapter's init");
        h.assertTrue(Started.bus == framework.getEventBus(), "init wasn't given the owner's bus");
        h.assertTrue(Started.container == framework, "init wasn't given the owner's container");
        h.assertValueEqual(Never.calls.get(), 0, "calls of an adapter that should never start");

        String minecraft = Adapters.installedVersion("minecraft").orElseThrow();
        String neoforge = Adapters.installedVersion("neoforge").orElseThrow();
        h.assertValueEqual(
                table.statuses(),
                List.of(
                        new Status(supported, State.ACTIVE, minecraft),
                        new Status(unsupported, State.UNSUPPORTED, neoforge),
                        new Status(absent, State.ABSENT, null)),
                "the statuses");
        h.assertTrue(table.isActive("minecraft"), "the supported adapter isn't active");
        h.assertFalse(table.isActive("neoforge"), "the unsupported adapter is active");
        h.assertFalse(table.isActive(ABSENT), "the absent adapter is active");
        h.assertFalse(table.isActive("no_row"), "a mod with no row is active");
        h.assertValueEqual(table.status("neoforge").map(Status::state), Optional.of(State.UNSUPPORTED), "status");

        try {
            table.load(framework.getEventBus(), framework);
            h.fail("a table loaded twice");
        } catch (IllegalStateException expected) {
            // A table loads once.
        }
        h.succeed();
    }

    /**
     * An entry class that throws, is missing, has no {@code init} or fails to link leaves its
     * adapter off, with one error each, and the rows after it still load.
     */
    @GameTest(template = "empty")
    public static void an_adapter_that_fails_to_start_is_off_and_the_rest_still_load(GameTestHelper h) {
        ModContainer framework = framework();
        List<Row> rows = List.of(
                new Row("throws", "[1.0,)", Failing.class.getName()),
                new Row("missing", "[1.0,)", AdapterTests.class.getName() + "$NoSuchEntry"),
                new Row("no_init", "[1.0,)", NoInit.class.getName()),
                new Row("unlinked", "[1.0,)", Unlinked.class.getName()),
                new Row("works", "[1.0,)", Started.class.getName()));
        int before = Started.calls.get();
        try (Heard log = new Heard()) {
            List<Status> found = Adapters.load(rows, id -> Optional.of("1.0"), framework.getEventBus(), framework);
            h.assertValueEqual(
                    found.stream().map(Status::state).toList(),
                    List.of(State.FAILED, State.FAILED, State.FAILED, State.FAILED, State.ACTIVE),
                    "the states");
            for (String modId : List.of("throws", "missing", "no_init", "unlinked")) {
                h.assertValueEqual(log.at(Level.ERROR, FrameworkApi.MOD_ID, modId, "failed"), 1, "errors for " + modId);
            }
            h.assertValueEqual(log.at(Level.ERROR), 4, "errors");
        }
        h.assertValueEqual(Started.calls.get(), before + 1, "calls of the working adapter's init");
        h.succeed();
    }

    /**
     * With versions just outside the range, the adapter stays off, its entry class isn't loaded and
     * one warning names the owner, the target, the version and the range.
     */
    @GameTest(template = "empty")
    public static void an_unsupported_version_is_one_warning_and_nothing_else(GameTestHelper h) {
        ModContainer framework = framework();
        String range = "[1.16.26,1.17)";
        Row row = new Row("target", range, Never.class.getName());
        for (String version : List.of("1.16.25", "1.17", "1.17.0", "2.0.0", "1.21.1-1.16.26")) {
            try (Heard log = new Heard()) {
                List<Status> found =
                        Adapters.load(List.of(row), id -> Optional.of(version), framework.getEventBus(), framework);
                h.assertValueEqual(found, List.of(new Status(row, State.UNSUPPORTED, version)), "at " + version);
                h.assertValueEqual(
                        log.at(Level.WARN, FrameworkApi.MOD_ID, "target", version, range), 1, "warnings at " + version);
                h.assertValueEqual(log.events.size(), 1, "lines logged at " + version);
            }
        }
        h.assertValueEqual(Never.calls.get(), 0, "calls of an adapter that should never start");
        h.succeed();
    }

    /** A plugin gate reads the version the game has, and warns once outside its range. */
    @GameTest(template = "empty")
    public static void a_plugin_gate_reads_the_installed_version(GameTestHelper h) {
        h.assertTrue(new PluginGate(FrameworkApi.MOD_ID, "minecraft", "[1.21,1.22)").on(), "Minecraft 1.21.x");
        h.assertFalse(new PluginGate(FrameworkApi.MOD_ID, ABSENT, "[1.0,)").on(), "a mod that isn't installed");
        try (Heard log = new Heard(PluginGate.class)) {
            PluginGate gate = new PluginGate(FrameworkApi.MOD_ID, "neoforge", "[999,)");
            h.assertFalse(gate.on(), "an unsupported NeoForge");
            h.assertFalse(gate.on(), "an unsupported NeoForge, asked again");
            h.assertValueEqual(log.at(Level.WARN, FrameworkApi.MOD_ID, "neoforge", "[999,)"), 1, "warnings");
            h.assertValueEqual(gate.warnings(), 1, "the gate's count of warnings");
        }
        h.succeed();
    }

    private static ModContainer framework() {
        return ModList.get().getModContainerById(FrameworkApi.MOD_ID).orElseThrow();
    }

    /** An entry class that records its calls. */
    public static final class Started {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile IEventBus bus;
        static volatile ModContainer container;

        private Started() {}

        public static void init(IEventBus modBus, ModContainer owner) {
            bus = modBus;
            container = owner;
            calls.incrementAndGet();
        }
    }

    /** An entry class whose target is never supported, so it must never start. */
    public static final class Never {
        static final AtomicInteger calls = new AtomicInteger();

        private Never() {}

        public static void init(IEventBus modBus, ModContainer owner) {
            calls.incrementAndGet();
        }
    }

    /** An entry class whose {@code init} throws. */
    public static final class Failing {
        private Failing() {}

        public static void init(IEventBus modBus, ModContainer owner) {
            throw new IllegalStateException("the target isn't what this adapter was written for");
        }
    }

    /** An entry class with no {@code init}. */
    public static final class NoInit {
        private NoInit() {}
    }

    /** An entry class whose {@code init} fails as code compiled against another version of its target does. */
    public static final class Unlinked {
        private Unlinked() {}

        public static void init(IEventBus modBus, ModContainer owner) {
            throw new NoSuchMethodError("a method the target no longer has");
        }
    }

    /** What a class logs, at INFO and above, while this is open. */
    private static final class Heard extends AbstractAppender implements AutoCloseable {
        private final String logger;
        final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final Logger root = (Logger) LogManager.getRootLogger();

        Heard() {
            this(Adapters.class);
        }

        Heard(Class<?> source) {
            super("ss13_framework_gametest_log", null, null, true, Property.EMPTY_ARRAY);
            logger = source.getName();
            start();
            root.addAppender(this);
        }

        @Override
        public void append(LogEvent event) {
            if (logger.equals(event.getLoggerName()) && event.getLevel().isMoreSpecificThan(Level.INFO)) {
                events.add(event.toImmutable());
            }
        }

        /** How many lines were logged at exactly this level with all of these texts in them. */
        int at(Level level, String... texts) {
            return (int) events.stream()
                    .filter(e -> e.getLevel() == level)
                    .map(e -> e.getMessage().getFormattedMessage())
                    .filter(line -> List.of(texts).stream().allMatch(line::contains))
                    .count();
        }

        @Override
        public void close() {
            root.removeAppender(this);
            stop();
        }
    }
}
