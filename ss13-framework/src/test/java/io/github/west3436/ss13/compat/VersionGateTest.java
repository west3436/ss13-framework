package io.github.west3436.ss13.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.compat.Adapters.Row;
import io.github.west3436.ss13.compat.VersionGate.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The version check, which needs no game, and the shape of an adapter table. */
class VersionGateTest {
    /** A range with a top. */
    private static final String BOUNDED = "[1.16.26,1.17)";
    /** 10.7.15 or newer. */
    private static final String OPEN_ENDED = "[10.7.15,)";

    @Test
    void a_target_that_is_not_installed_is_absent() {
        assertEquals(Verdict.ABSENT, VersionGate.check(null, BOUNDED));
        assertEquals(Verdict.ABSENT, VersionGate.check(null, OPEN_ENDED));
    }

    @Test
    void a_version_inside_the_range_is_supported() {
        for (String version : List.of("1.16.26", "1.16.27", "1.16.26.1")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, BOUNDED), version);
            assertTrue(VersionGate.supports(version, BOUNDED), version);
        }
        for (String version : List.of("10.7.15", "10.7.15.81", "10.8.0", "11.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, OPEN_ENDED), version);
        }
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.21.1", "[1.21.1]"));
    }

    @Test
    void a_version_outside_the_range_is_unsupported() {
        for (String version : List.of("1.16.25", "1.15", "1.17", "1.17.0", "1.17.1", "2.0")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, BOUNDED), version);
            assertFalse(VersionGate.supports(version, BOUNDED), version);
        }
        for (String version : List.of("10.7.14", "10.6.9", "9.99")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, OPEN_ENDED), version);
        }
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.21.2", "[1.21.1]"));
    }

    @Test
    void versions_compare_as_numbers_not_as_text() {
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.16.100", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.16.3", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("10.7.9", OPEN_ENDED));
        assertEquals(Verdict.SUPPORTED, VersionGate.check("10.10.0", OPEN_ENDED));
    }

    /** Maven's order, which mod metadata uses too: a pre-release comes before its release. */
    @Test
    void a_pre_release_of_the_upper_bound_is_below_it() {
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.17-beta.1", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.16.26-beta.1", BOUNDED));
    }

    /** Versions as mods really write them: with the game's version or the loader after a +. */
    @Test
    void build_metadata_after_a_plus_does_not_move_a_version_out_of_its_range() {
        String curios = "[9.2.0,)";
        for (String version : List.of("9.2.0+1.21.1", "9.5.1+1.21.1", "10.0.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, curios), version);
        }
        for (String version : List.of("9.1.4+1.21.1", "8.1.0+1.20.6")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, curios), version);
        }
        String jade = "[15.1.6,16)";
        for (String version : List.of("15.1.6+neoforge", "15.10.6+neoforge", "15.100.0+neoforge")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, jade), version);
        }
        for (String version : List.of("15.1.5+neoforge", "14.3.4+neoforge", "16.0.0+neoforge")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, jade), version);
        }
    }

    /** A version that isn't numbers still gets a verdict, never an exception. */
    @Test
    void a_version_that_is_not_a_number_is_unsupported() {
        for (String version : List.of("", "NONE", "${file.jarVersion}", "dev")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, BOUNDED), version);
        }
    }

    /**
     * A bare version isn't a range. Maven would let every version through one, so a row written
     * with one by mistake would gate nothing.
     */
    @Test
    void a_range_needs_bounds() {
        for (String spec : List.of("1.16.26", "", "[1.16.26", "1.16.26,1.17", "[1.17,1.16]")) {
            assertThrows(IllegalArgumentException.class, () -> VersionGate.check("1.16.26", spec), spec);
            assertThrows(IllegalArgumentException.class, () -> VersionGate.check(null, spec), spec);
            assertThrows(IllegalArgumentException.class, () -> new Row("target", spec, "some.Entry"), spec);
        }
    }

    @Test
    void a_target_has_one_row_in_a_table() {
        Row first = new Row("target", BOUNDED, "some.Entry");
        Row other = new Row("other", OPEN_ENDED, "some.OtherEntry");
        assertEquals(List.of(first, other), Adapters.of(List.of(first, other)).rows());
        Row again = new Row("target", OPEN_ENDED, "some.LaterEntry");
        assertThrows(IllegalArgumentException.class, () -> Adapters.of(List.of(first, other, again)));
    }

    /** A table can be made and read outside the game, and says nothing has loaded. */
    @Test
    void a_table_that_has_not_loaded_has_nothing_active() {
        Adapters table = Adapters.of(List.of(new Row("target", BOUNDED, "some.Entry")));
        assertEquals(List.of(), table.statuses());
        assertFalse(table.isActive("target"));
        assertTrue(table.status("target").isEmpty());
    }
}
