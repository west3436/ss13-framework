package io.github.west3436.ss13.api.gravity;

/**
 * What one {@link GravityProvider} does to the gravity at a position: nothing, scale it, or set it
 * outright. Gravities are multiples of the Overworld's: 1 is normal, 0 is none.
 *
 * <p>Scaling alone can't describe every field. Space's gravity is 0, so deck gravity aboard a ship
 * or a gravity generator in Space has to {@linkplain #set(double) set} 1 rather than multiply.
 *
 * <p>Immutable, and may be used from any thread.
 */
public final class GravityEffect {
    /** Leaves the gravity as it is: what a provider answers where it has no say. */
    public static final GravityEffect NONE = new GravityEffect(Kind.NONE, 1.0);

    private enum Kind {
        NONE,
        SCALE,
        SET
    }

    private final Kind kind;
    private final double value;

    private GravityEffect(Kind kind, double value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Multiplies the gravity so far by a factor.
     *
     * @param factor the factor, finite and 0 or more
     * @return the effect; {@link #NONE} for a factor of 1
     * @throws IllegalArgumentException if the factor is negative, infinite or NaN
     */
    public static GravityEffect scale(double factor) {
        check(factor, "factor");
        return factor == 1.0 ? NONE : new GravityEffect(Kind.SCALE, factor);
    }

    /**
     * Replaces the gravity so far, whatever it was.
     *
     * @param gravity the gravity, a multiple of the Overworld's, finite and 0 or more
     * @return the effect
     * @throws IllegalArgumentException if the gravity is negative, infinite or NaN
     */
    public static GravityEffect set(double gravity) {
        check(gravity, "gravity");
        return new GravityEffect(Kind.SET, gravity);
    }

    /**
     * Applies this effect to the gravity that the providers before it gave.
     *
     * @param gravity the gravity so far
     * @return the gravity after this effect
     */
    public double applyTo(double gravity) {
        return switch (kind) {
            case NONE -> gravity;
            case SCALE -> gravity * value;
            case SET -> value;
        };
    }

    /**
     * Whether this effect changes nothing.
     *
     * @return true for {@link #NONE}
     */
    public boolean isNone() {
        return kind == Kind.NONE;
    }

    /**
     * Whether this effect replaces the gravity rather than scaling it.
     *
     * @return true for an effect from {@link #set(double)}
     */
    public boolean isSet() {
        return kind == Kind.SET;
    }

    /**
     * The factor of a scaling effect, or the gravity of a setting one.
     *
     * @return the value; 1 for {@link #NONE}
     */
    public double value() {
        return value;
    }

    private static void check(double value, String what) {
        if (!(value >= 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("a gravity " + what + " must be finite and 0 or more: " + value);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GravityEffect that && kind == that.kind && Double.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + Double.hashCode(value);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case NONE -> "GravityEffect[none]";
            case SCALE -> "GravityEffect[scale " + value + "]";
            case SET -> "GravityEffect[set " + value + "]";
        };
    }
}
