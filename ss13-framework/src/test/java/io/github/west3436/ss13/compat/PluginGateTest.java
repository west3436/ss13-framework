package io.github.west3436.ss13.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** A plugin its target starts itself: it gates itself, and warns once. */
class PluginGateTest {
    /** A range that starts at a build, the fourth number of the version, as a JEI plugin's does. */
    private static final String JEI = "[19.21.0.247,20)";

    private static final String EMI = "[1.1.13,2)";

    @Test
    void a_target_outside_the_range_is_off_with_one_warning_however_often_it_is_asked() {
        AtomicInteger asked = new AtomicInteger();
        PluginGate gate = new PluginGate("owner", "jei", JEI, () -> {
            asked.incrementAndGet();
            return "19.8.2.99";
        });
        assertEquals(0, gate.warnings(), "nothing is decided before the target starts the plugin");
        // A viewer calls its plugin several times each time it loads, and loads again on each reload.
        for (int i = 0; i < 5; i++) {
            assertFalse(gate.on());
        }
        assertEquals(1, gate.warnings());
        assertEquals(1, asked.get(), "the version is read once");
    }

    @Test
    void a_target_inside_the_range_is_on_without_a_warning() {
        PluginGate gate = new PluginGate("owner", "jei", JEI, () -> "19.21.0.247");
        assertTrue(gate.on());
        assertTrue(gate.on());
        assertEquals(0, gate.warnings());
        assertEquals(JEI, gate.range());
        assertEquals("jei", gate.modId());
    }

    @Test
    void a_target_that_is_not_installed_is_off_without_a_warning() {
        PluginGate gate = new PluginGate("owner", "emi", EMI, () -> null);
        assertFalse(gate.on());
        assertEquals(0, gate.warnings());
    }

    @Test
    void a_range_without_bounds_is_refused_when_the_gate_is_made() {
        assertThrows(
                IllegalArgumentException.class, () -> new PluginGate("owner", "jei", "19.21", () -> "19.21.0.247"));
    }

    @Test
    void a_range_that_starts_at_a_build_number_leaves_out_the_builds_before_it() {
        for (String version : List.of("19.21.0.243", "19.21.0.246", "19.20.0.242", "19.21.0.9")) {
            assertFalse(new PluginGate("owner", "jei", JEI, () -> version).on(), version);
        }
        for (String version : List.of("19.21.0.247", "19.21.0.248", "19.21.0.1000", "19.21.1.312")) {
            assertTrue(new PluginGate("owner", "jei", JEI, () -> version).on(), version);
        }
    }

    /** EMI's version carries the game's and the loader's after a +. */
    @Test
    void a_version_with_build_metadata_is_gated_by_its_numbers() {
        for (String version : List.of("1.1.13+1.21.1+neoforge", "1.1.24+1.21.1+neoforge")) {
            assertTrue(new PluginGate("owner", "emi", EMI, () -> version).on(), version);
        }
        for (String version : List.of("1.1.12+1.21+neoforge", "2.0.0+1.21.1+neoforge")) {
            assertFalse(new PluginGate("owner", "emi", EMI, () -> version).on(), version);
        }
    }
}
