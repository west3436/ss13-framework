package io.github.west3436.ss13.api.gravity;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The gravity at a position, as a multiple of the Overworld's, composed from the registered {@link
 * GravityProvider providers}.
 *
 * <p><b>How it composes.</b> {@link #at} starts from {@link #NORMAL} and applies each provider's
 * {@link GravityEffect} in order of priority, lowest first: a scaling effect multiplies the gravity
 * so far, a setting one replaces it. So a later provider overrides what an earlier one set, and
 * scales what it was given. Providers of equal priority go in order of their ids. The usual
 * priorities:
 *
 * <ul>
 *   <li>{@link #DIMENSION}: a whole level's gravity, such as a planet's, or Space's 0.
 *   <li>{@link #FIELD}: a field over part of a level, such as a gravity generator or a ship's deck
 *       gravity, which sets 1 even in Space.
 * </ul>
 *
 * <p><b>How the framework applies it</b>, wherever the {@code ss13_framework} mod is loaded:
 *
 * <ul>
 *   <li>A living entity on the server carries three transient modifiers of operation {@code
 *       add_multiplied_total}, so a gravity {@code g} multiplies each attribute: {@link
 *       #MODIFIER_ID} on {@code minecraft:generic.gravity} by {@code g}, {@link #SAFE_FALL_ID} on
 *       the safe fall distance by {@code 1 / g}, and {@link #FALL_DAMAGE_ID} on the fall damage
 *       multiplier by {@code g}. A fall of {@code h} blocks then hurts as much as one of {@code g ×
 *       h} on the Overworld, and landing from a jump never hurts. They are set as the entity joins
 *       a level, again whenever it moves and once a second, so it notices fields that switch on or
 *       off, and removed as it leaves the level. A gravity of 1 leaves none. The attributes sync,
 *       so clients see them.
 *   <li>Every other entity (dropped items, falling blocks, projectiles, minecarts, boats) falls
 *       with its usual gravity times the gravity at its position, on both sides.
 * </ul>
 *
 * <p>Thread: registering may happen on any thread, and {@link #at} runs on the level's own thread.
 */
public final class Gravity {
    /** The Overworld's gravity, and the gravity wherever no provider has a say. */
    public static final double NORMAL = 1.0;

    /** The priority of a provider for whole levels, such as planets: applied first. */
    public static final int DIMENSION = 0;

    /** The priority of a provider for fields over part of a level, such as a gravity generator. */
    public static final int FIELD = 1000;

    /** {@code ss13_framework:gravity}: the framework's modifier on the gravity attribute. */
    public static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "gravity");

    /** {@code ss13_framework:gravity_safe_fall_distance}: its modifier on the safe fall distance. */
    public static final ResourceLocation SAFE_FALL_ID =
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "gravity_safe_fall_distance");

    /** {@code ss13_framework:gravity_fall_damage}: its modifier on the fall damage multiplier. */
    public static final ResourceLocation FALL_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "gravity_fall_damage");

    /** A registered provider. */
    record Registration(ResourceLocation id, int priority, GravityProvider provider) {}

    private static final Comparator<Registration> ORDER =
            Comparator.comparingInt(Registration::priority).thenComparing(Registration::id);

    /** The providers in the order they apply, replaced whole on every registration. */
    private static volatile List<Registration> providers = List.of();

    private Gravity() {}

    /**
     * Registers a provider, usually during mod construction.
     *
     * @param id the provider's id, in its mod's namespace
     * @param priority when it applies: {@link #DIMENSION}, {@link #FIELD}, or any other number,
     *     lower first
     * @param provider the provider
     * @throws IllegalArgumentException if a provider with this id is already registered
     */
    public static synchronized void register(ResourceLocation id, int priority, GravityProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        List<Registration> next = new ArrayList<>(providers);
        if (next.stream().anyMatch(r -> r.id().equals(id))) {
            throw new IllegalArgumentException("a gravity provider is already registered as " + id);
        }
        next.add(new Registration(id, priority, provider));
        next.sort(ORDER);
        providers = List.copyOf(next);
    }

    /**
     * Whether a provider is registered under an id.
     *
     * @param id the id
     * @return true if one is
     */
    public static boolean isRegistered(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return providers.stream().anyMatch(r -> r.id().equals(id));
    }

    /**
     * The gravity at a position.
     *
     * @param level a server or client level
     * @param pos the position
     * @return the gravity, a multiple of the Overworld's, 0 or more; {@link #NORMAL} where no
     *     provider has a say
     */
    public static double at(Level level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        return compose(providers, level, pos);
    }

    /** Applies each provider's effect in order, from {@link #NORMAL}. */
    static double compose(List<Registration> registrations, Level level, BlockPos pos) {
        double gravity = NORMAL;
        for (Registration registration : registrations) {
            GravityEffect effect = registration.provider().at(level, pos);
            if (effect == null) {
                throw new IllegalStateException("gravity provider " + registration.id() + " answered null");
            }
            gravity = effect.applyTo(gravity);
        }
        return gravity;
    }

    /** The registrations in order, sorted as {@link #register} sorts them; for tests. */
    static List<Registration> sorted(List<Registration> registrations) {
        return registrations.stream().sorted(ORDER).toList();
    }
}
