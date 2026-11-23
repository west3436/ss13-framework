package io.github.west3436.ss13.api;

import java.util.Optional;

/**
 * Facts about the copy of the API that the game loaded.
 *
 * <p>Every mod that uses the framework nests its own copy of this API, and NeoForge loads only the
 * newest. {@link #version()} tells a mod which copy that was, which can differ from the one it
 * nested.
 */
public final class FrameworkApi {
    /** The mod id of the {@code ss13_framework} mod. */
    public static final String MOD_ID = "ss13_framework";

    private FrameworkApi() {}

    /**
     * The version of the API that the game loaded, read from the API jar's manifest.
     *
     * <p>May be called from any thread.
     *
     * @return the version, such as {@code 0.1.0}, or empty when the API isn't loaded from its jar,
     *     as in a development run that loads it from class directories
     */
    public static Optional<String> version() {
        return Optional.ofNullable(FrameworkApi.class.getPackage().getImplementationVersion());
    }
}
