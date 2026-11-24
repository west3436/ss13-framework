package io.github.west3436.ss13.api.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Options registered by two mods follow one switch, each keeping its override, with a store kept in
 * memory in place of the framework's preset file. The registry is global, so each test registers
 * options under ids of its own.
 */
class PresetsTest {
    private enum Air {
        THICK,
        THIN
    }

    /** A store kept in memory, as the framework's is between saves. */
    private static final class MemoryStore implements PresetStore {
        Preset preset = Preset.NORMAL;
        final Map<ResourceLocation, Object> overrides = new HashMap<>();
        int saves;

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public Preset preset() {
            return preset;
        }

        @Override
        public Optional<Object> override(ResourceLocation option) {
            return Optional.ofNullable(overrides.get(option));
        }

        @Override
        public void switchTo(Preset next) {
            preset = next;
            saves++;
        }

        @Override
        public void setOverride(ResourceLocation option, Object stored) {
            overrides.put(option, stored);
            saves++;
        }

        @Override
        public void clearOverride(ResourceLocation option) {
            overrides.remove(option);
            saves++;
        }
    }

    private static final AtomicInteger NEXT = new AtomicInteger();

    private MemoryStore store;
    private PresetStore previous;

    /** An id no other test uses. */
    private static ResourceLocation id(String namespace, String key) {
        return ResourceLocation.fromNamespaceAndPath(namespace, key + "_" + NEXT.incrementAndGet());
    }

    @BeforeEach
    void install() {
        store = new MemoryStore();
        previous = Presets.install(store);
    }

    @AfterEach
    void restore() {
        Presets.install(previous);
    }

    @Test
    void one_switch_sets_two_mods_options() {
        PresetOption<Boolean> fire = Presets.register(
                id("mod_a", "fire_forms_zones"), PresetType.BOOLEAN, false, true, "Whether fire forms zones");
        PresetOption<Double> burn = Presets.register(
                id("mod_b", "fuel_burn"), PresetType.number(0, 10, "x"), 1.0, 2.5, "How fast fuel burns");
        PresetOption<Air> air =
                Presets.register(id("mod_b", "air"), PresetType.ofEnum(Air.class), Air.THICK, Air.THIN, "The air");

        assertEquals(Preset.NORMAL, Presets.active());
        assertFalse(fire.get());
        assertEquals(1.0, burn.get());
        assertEquals(Air.THICK, air.get());

        Presets.switchTo(Preset.HARDCORE);

        assertEquals(Preset.HARDCORE, Presets.active());
        assertTrue(fire.get());
        assertEquals(2.5, burn.get());
        assertEquals(Air.THIN, air.get());
        assertEquals(1, store.saves);
    }

    @Test
    void an_override_holds_under_either_preset() {
        PresetOption<Double> burn = Presets.register(
                id("mod_b", "fuel_burn"), PresetType.number(0, 10, "x"), 1.0, 2.5, "How fast fuel burns");
        PresetOption<Boolean> other =
                Presets.register(id("mod_a", "other"), PresetType.BOOLEAN, false, true, "Something else");

        burn.setOverride(4.0);
        assertEquals(PresetOption.Source.OVERRIDE, burn.source());
        assertEquals(4.0, burn.get());
        // Stored as the file would store it.
        assertEquals(Optional.of(4.0), store.override(burn.id()));

        Presets.switchTo(Preset.HARDCORE);
        assertEquals(4.0, burn.get(), "the override after the switch");
        assertTrue(other.get(), "an option without an override follows the switch");
        assertEquals(4.0, burn.get(Preset.NORMAL));

        burn.clearOverride();
        assertEquals(PresetOption.Source.PRESET, burn.source());
        assertEquals(2.5, burn.get());
    }

    @Test
    void a_stored_value_the_type_refuses_follows_the_preset() {
        PresetOption<Double> burn = Presets.register(
                id("mod_b", "fuel_burn"), PresetType.number(0, 10, "x"), 1.0, 2.5, "How fast fuel burns");
        store.overrides.put(burn.id(), 99.0);
        assertEquals(Optional.empty(), burn.override());
        assertEquals(1.0, burn.get());
        store.overrides.put(burn.id(), "PRESET");
        assertEquals(PresetOption.Source.PRESET, burn.source());
        store.overrides.put(burn.id(), "3");
        assertEquals(3.0, burn.get(), "a number given as text");
    }

    @Test
    void with_nothing_loaded_every_option_has_its_normal_default() {
        PresetOption<Boolean> fire =
                Presets.register(id("mod_a", "fire"), PresetType.BOOLEAN, false, true, "Whether fire forms zones");
        store.overrides.put(fire.id(), true);
        store.preset = Preset.HARDCORE;
        Presets.install(PresetStore.NONE);

        assertFalse(Presets.isLoaded());
        assertEquals(Preset.NORMAL, Presets.active());
        assertFalse(fire.get());
        assertEquals(0, Presets.overridden());
        assertThrows(IllegalStateException.class, () -> Presets.switchTo(Preset.HARDCORE));
        assertThrows(IllegalStateException.class, () -> fire.setOverride(true));
        assertThrows(IllegalStateException.class, fire::clearOverride);
    }

    @Test
    void registering_checks_the_id_and_defaults() {
        ResourceLocation id = id("mod_a", "twice");
        PresetOption<Boolean> first = Presets.register(id, PresetType.BOOLEAN, false, true, "Once");
        assertThrows(
                IllegalArgumentException.class, () -> Presets.register(id, PresetType.BOOLEAN, false, true, "Twice"));
        assertSame(first, Presets.option(id).orElseThrow());
        assertTrue(Presets.options().contains(first));
        assertThrows(
                IllegalArgumentException.class,
                () -> Presets.register(id("minecraft", "vanilla"), PresetType.BOOLEAN, false, true, "No namespace"));
        assertThrows(
                IllegalArgumentException.class,
                () -> Presets.register(id("mod_a", "out_of_range"), PresetType.number(0, 1, ""), 0.5, 2.0, "Too much"));
    }

    @Test
    void command_aliases_keep_their_order_once_each() {
        Presets.addCommandAlias("alias_b");
        Presets.addCommandAlias("alias_a");
        Presets.addCommandAlias("alias_b");
        List<String> aliases = Presets.commandAliases();
        assertTrue(aliases.indexOf("alias_b") < aliases.indexOf("alias_a"));
        assertEquals(1, aliases.stream().filter("alias_b"::equals).count());
        assertThrows(IllegalArgumentException.class, () -> Presets.addCommandAlias("Not A Namespace"));
    }

    @Test
    void types_read_what_they_write_and_text() {
        assertEquals(Optional.of(true), PresetType.BOOLEAN.parse(" TRUE "));
        assertEquals(Optional.empty(), PresetType.BOOLEAN.parse("yes"));
        assertEquals(Optional.empty(), PresetType.BOOLEAN.parse(null));

        PresetType<Air> air = PresetType.ofEnum(Air.class);
        assertEquals(Optional.of(Air.THIN), air.parse("thin"));
        assertEquals("THIN", air.write(Air.THIN));
        assertEquals("THICK or THIN", air.describe());
        assertEquals(List.of("THICK", "THIN"), air.suggestions());

        PresetType<Double> rate = PresetType.number(0, 100, "mol/h");
        assertEquals(Optional.of(0.125), rate.parse(0.125));
        assertEquals(Optional.of(3.0), rate.parse(3L), "a TOML integer");
        assertEquals(Optional.empty(), rate.parse(Double.NaN));
        assertEquals(Optional.empty(), rate.parse(-1));
        assertEquals("0.0625", rate.format(0.0625));
        assertEquals("2", rate.format(2.0));
        assertEquals("a number from 0 to 100 mol/h", rate.describe());
        assertThrows(IllegalArgumentException.class, () -> PresetType.number(2, 1, ""));
    }

    @Test
    void presets_read_their_names_ignoring_case() {
        assertEquals(Optional.of(Preset.HARDCORE), Preset.byName(" hardcore"));
        assertEquals(Optional.of(Preset.NORMAL), Preset.byName("NORMAL"));
        assertEquals(Optional.empty(), Preset.byName("easy"));
        assertEquals("hardcore", Preset.HARDCORE.id());
    }
}
