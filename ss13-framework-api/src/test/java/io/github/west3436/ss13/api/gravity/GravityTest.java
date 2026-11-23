package io.github.west3436.ss13.api.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.api.gravity.Gravity.Registration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * How providers compose: from 1, in order of priority and then id, each scaling or replacing the
 * gravity so far. The level is never looked at by these providers, so none is made.
 */
class GravityTest {
    private static final BlockPos POS = BlockPos.ZERO;

    private static Registration reg(String id, int priority, GravityEffect effect) {
        return new Registration(ResourceLocation.fromNamespaceAndPath("test", id), priority, (level, pos) -> effect);
    }

    private static double compose(Registration... registrations) {
        return Gravity.compose(Gravity.sorted(List.of(registrations)), null, POS);
    }

    @Test
    void no_provider_gives_normal_gravity() {
        assertEquals(Gravity.NORMAL, compose());
    }

    @Test
    void scales_multiply() {
        assertEquals(
                0.38 * 0.5,
                compose(
                        reg("planet", Gravity.DIMENSION, GravityEffect.scale(0.38)),
                        reg("field", Gravity.FIELD, GravityEffect.scale(0.5))),
                1e-12);
    }

    @Test
    void a_later_set_replaces_what_came_before() {
        // Deck gravity in Space: Space sets 0, the deck sets 1.
        assertEquals(
                1.0,
                compose(
                        reg("space", Gravity.DIMENSION, GravityEffect.set(0)),
                        reg("deck", Gravity.FIELD, GravityEffect.set(1))));
    }

    @Test
    void a_later_scale_applies_to_what_was_set() {
        assertEquals(
                0.5,
                compose(
                        reg("space", Gravity.DIMENSION, GravityEffect.set(0)),
                        reg("deck", Gravity.FIELD, GravityEffect.set(1)),
                        reg("damper", Gravity.FIELD + 1, GravityEffect.scale(0.5))));
    }

    @Test
    void a_scale_before_a_set_is_lost() {
        assertEquals(
                0.38,
                compose(
                        reg("field", Gravity.FIELD, GravityEffect.set(0.38)),
                        reg("planet", Gravity.DIMENSION, GravityEffect.scale(3))));
    }

    @Test
    void equal_priorities_apply_in_order_of_id() {
        // "a" sets 2 and "b" sets 3: b applies last whatever order they are given in.
        Registration a = reg("a", Gravity.FIELD, GravityEffect.set(2));
        Registration b = reg("b", Gravity.FIELD, GravityEffect.set(3));
        assertEquals(3.0, compose(a, b));
        assertEquals(3.0, compose(b, a));
    }

    @Test
    void none_changes_nothing() {
        assertEquals(
                0.38,
                compose(
                        reg("planet", Gravity.DIMENSION, GravityEffect.set(0.38)),
                        reg("idle", Gravity.FIELD, GravityEffect.NONE)));
        assertSame(GravityEffect.NONE, GravityEffect.scale(1));
        assertTrue(GravityEffect.NONE.isNone());
        assertFalse(GravityEffect.set(1).isNone());
    }

    @Test
    void a_provider_that_answers_null_is_named() {
        Registration broken =
                new Registration(ResourceLocation.fromNamespaceAndPath("test", "broken"), 0, (level, pos) -> null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> compose(broken));
        assertTrue(e.getMessage().contains("test:broken"), e.getMessage());
    }

    @Test
    void effects_refuse_negative_and_non_finite_values() {
        for (double bad : new double[] {-0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> GravityEffect.scale(bad));
            assertThrows(IllegalArgumentException.class, () -> GravityEffect.set(bad));
        }
        assertEquals(0.0, GravityEffect.set(0).applyTo(5));
        assertEquals(0.0, GravityEffect.scale(0).applyTo(5));
    }

    @Test
    void an_id_registers_once() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "once");
        assertFalse(Gravity.isRegistered(id));
        Gravity.register(id, Gravity.FIELD, (level, pos) -> GravityEffect.NONE);
        assertTrue(Gravity.isRegistered(id));
        assertThrows(
                IllegalArgumentException.class,
                () -> Gravity.register(id, Gravity.DIMENSION, (level, pos) -> GravityEffect.NONE));
    }
}
