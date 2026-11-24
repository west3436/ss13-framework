package io.github.west3436.ss13.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a comparator reads from a device. */
class ComparatorsTest {
    @Test
    void a_container_reads_0_only_when_empty() {
        assertEquals(0, Comparators.fill(0));
        assertEquals(0, Comparators.fill(-1));
        assertEquals(0, Comparators.fill(Double.NaN));
        assertEquals(1, Comparators.fill(1e-9), "anything at all reads at least 1");
        assertEquals(1, Comparators.fill(1.0 / 15));
        assertEquals(2, Comparators.fill(1.0 / 15 + 1e-6));
        assertEquals(14, Comparators.fill(14.0 / 15));
        assertEquals(15, Comparators.fill(14.0 / 15 + 1e-6), "more than 14/15 full is 15");
        assertEquals(15, Comparators.fill(1));
        assertEquals(15, Comparators.fill(3), "above full is full");
    }

    @Test
    void a_reader_reads_where_its_dial_points() {
        assertEquals(0, Comparators.dial(0));
        assertEquals(0, Comparators.dial(-0.5));
        assertEquals(0, Comparators.dial(Double.NaN));
        assertEquals(0, Comparators.dial(0.03), "under half a step rounds down");
        assertEquals(1, Comparators.dial(0.034));
        assertEquals(8, Comparators.dial(0.5));
        assertEquals(15, Comparators.dial(1));
        assertEquals(15, Comparators.dial(2), "past the top is the top");
    }

    @Test
    void an_output_tells_only_of_a_change() {
        Comparators.Output out = new Comparators.Output();
        assertTrue(out.changes(0), "the first signal is always told");
        assertFalse(out.changes(0));
        assertTrue(out.changes(7));
        assertFalse(out.changes(7));
        assertTrue(out.changes(0));
    }
}
