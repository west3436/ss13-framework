package io.github.west3436.ss13.compat;

import java.util.Objects;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;

/**
 * Whether an adapter supports the version of its target that is installed. This is the decision
 * alone: it is given text and reads nothing from the game, so anything may ask it at any time:
 * {@link Adapters}, a {@link PluginGate}, or a mixin plugin before the game's classes load.
 *
 * <p>Ranges are written as in {@code neoforge.mods.toml}, and versions compare as they do there,
 * since both use Maven's rules: {@code [1.16.26,1.17)} is 1.16.26 and up but below 1.17, and {@code
 * [10.7.15,)} is 10.7.15 and up. Numbers compare as numbers, so 1.16.100 is above 1.16.26. A
 * pre-release such as {@code 1.17-beta.1} is below 1.17, so a range that ends at {@code 1.17)}
 * still holds it; end the range at the last release it should hold if that matters.
 */
public final class VersionGate {
    /** What the gate makes of a target. */
    public enum Verdict {
        /** The target isn't installed. */
        ABSENT,
        /** The target is installed, in a version outside the range. */
        UNSUPPORTED,
        /** The target is installed, in a version inside the range. */
        SUPPORTED
    }

    private VersionGate() {}

    /**
     * Decides for one target.
     *
     * @param installed the target's version as its mod metadata gives it, or null if the target
     *     isn't installed
     * @param supported the range of versions the adapter supports
     * @return the verdict
     * @throws IllegalArgumentException if {@code supported} isn't a range with bounds, whether or
     *     not the target is installed
     */
    public static Verdict check(@Nullable String installed, String supported) {
        VersionRange range = range(supported);
        if (installed == null) {
            return Verdict.ABSENT;
        }
        return range.containsVersion(new DefaultArtifactVersion(installed)) ? Verdict.SUPPORTED : Verdict.UNSUPPORTED;
    }

    /**
     * Whether a version is in a range: {@link #check} for a target known to be installed.
     *
     * @param installed the target's version as its mod metadata gives it
     * @param supported the range of versions the adapter supports
     * @return true if the version is in the range
     * @throws IllegalArgumentException if {@code supported} isn't a range with bounds
     */
    public static boolean supports(String installed, String supported) {
        return check(installed, supported) == Verdict.SUPPORTED;
    }

    /**
     * Reads a range.
     *
     * @throws IllegalArgumentException if it isn't one. A bare version such as {@code 1.2} is
     *     refused too: Maven reads it as a preference that every version satisfies, so it would
     *     gate nothing.
     */
    static VersionRange range(String spec) {
        Objects.requireNonNull(spec, "range");
        VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec(spec);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("Not a version range: " + spec, e);
        }
        if (!range.hasRestrictions()) {
            throw new IllegalArgumentException("Not a version range, as it has no bounds: " + spec);
        }
        return range;
    }
}
