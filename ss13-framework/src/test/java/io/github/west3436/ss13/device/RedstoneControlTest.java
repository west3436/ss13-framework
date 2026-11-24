package io.github.west3436.ss13.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.device.RedstoneControl.Mode;
import org.junit.jupiter.api.Test;

/** A redstone setting as a window's menu data and buttons carry it. */
class RedstoneControlTest {
    private static int data(Mode mode, boolean signal) {
        return mode.ordinal() | (signal ? 4 : 0);
    }

    @Test
    void menu_data_carries_the_mode_and_the_signal() {
        for (Mode mode : Mode.values()) {
            for (boolean signal : new boolean[] {false, true}) {
                int data = data(mode, signal);
                assertEquals(mode, RedstoneControl.mode(data));
                assertEquals(signal, RedstoneControl.signal(data));
            }
        }
    }

    @Test
    void only_a_mode_that_heeds_the_signal_holds_a_device() {
        assertFalse(RedstoneControl.holds(data(Mode.IGNORED, false)));
        assertFalse(RedstoneControl.holds(data(Mode.IGNORED, true)));
        assertTrue(RedstoneControl.holds(data(Mode.HIGH, false)));
        assertFalse(RedstoneControl.holds(data(Mode.HIGH, true)));
        assertFalse(RedstoneControl.holds(data(Mode.LOW, false)));
        assertTrue(RedstoneControl.holds(data(Mode.LOW, true)));
    }

    @Test
    void anything_but_a_mode_reads_as_ignored() {
        assertEquals(Mode.IGNORED, Mode.of(-1));
        assertEquals(Mode.IGNORED, Mode.of(3));
        assertEquals(Mode.LOW, Mode.of(2));
    }

    @Test
    void its_buttons_are_one_per_mode_from_100() {
        assertFalse(RedstoneControl.isButton(RedstoneControl.FIRST_BUTTON - 1));
        for (Mode mode : Mode.values()) {
            assertTrue(RedstoneControl.isButton(RedstoneControl.FIRST_BUTTON + mode.ordinal()));
        }
        assertFalse(RedstoneControl.isButton(RedstoneControl.LAST_BUTTON + 1));
        assertTrue(RedstoneControl.LAST_BUTTON < 128, "a window's button id is a byte");
    }

    @Test
    void its_words_are_its_names_in_lower_case() {
        assertEquals("ignored", Mode.IGNORED.word());
        assertEquals("high", Mode.HIGH.word());
        assertEquals("low", Mode.LOW.word());
    }
}
