package io.github.west3436.ss13.compat;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The version gate of an adapter that its target starts itself. Jade, JEI, EMI and KubeJS find
 * their plugins by annotation or by a file and create them whatever their own version, and Ponder
 * gathers scenes when it likes, so no {@link Adapters} row stands in front of such a plugin: it
 * asks its gate before it registers anything.
 *
 * <p>The gate decides the first time it is asked and keeps the answer, so a target that calls its
 * plugins again on every reload gets the same answer and the log gets one line: an info line if
 * the plugin is on, one warning if the installed version is outside the range, and nothing above
 * debug if the target isn't installed.
 *
 * <p>Keep the gate in a class that names nothing of the target's, so that a game without the
 * target may load it.
 */
public final class PluginGate {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String owner;
    private final String modId;
    private final String range;
    private final Supplier<String> installed;
    private @Nullable Boolean on;
    private int warnings;

    /**
     * A gate on the installed version of a mod.
     *
     * @param owner the mod id of the mod the plugin is part of, for the log
     * @param modId the target's mod id
     * @param range the versions of the target the plugin supports, written as in {@code
     *     neoforge.mods.toml} (see {@link VersionGate})
     * @throws IllegalArgumentException if {@code range} isn't a range with bounds
     */
    public PluginGate(String owner, String modId, String range) {
        this(owner, modId, range, () -> Adapters.installedVersion(modId).orElse(null));
    }

    /**
     * A gate on a version read some other way, such as a test's.
     *
     * @param owner the mod id of the mod the plugin is part of, for the log
     * @param modId the target's mod id
     * @param range the versions of the target the plugin supports
     * @param installed gives the installed version, or null if the target isn't installed. It is
     *     called once, when the gate is first asked.
     * @throws IllegalArgumentException if {@code range} isn't a range with bounds
     */
    public PluginGate(String owner, String modId, String range, Supplier<String> installed) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.modId = Objects.requireNonNull(modId, "modId");
        this.range = Objects.requireNonNull(range, "range");
        this.installed = Objects.requireNonNull(installed, "installed");
        // A range that can't be read fails here, when the plugin is made, not on some later reload.
        VersionGate.range(range);
    }

    /**
     * Whether the plugin may register: the target is installed in a supported version. Decided the
     * first time it is asked. Any thread.
     *
     * @return true if the plugin is on
     */
    public synchronized boolean on() {
        if (on == null) {
            on = decide(installed.get());
        }
        return on;
    }

    /**
     * How many warnings this gate has logged: at most one.
     *
     * @return the count
     */
    public synchronized int warnings() {
        return warnings;
    }

    /**
     * The target's mod id.
     *
     * @return the mod id
     */
    public String modId() {
        return modId;
    }

    /**
     * The versions of the target the plugin supports.
     *
     * @return the range, as it was given
     */
    public String range() {
        return range;
    }

    private boolean decide(@Nullable String version) {
        switch (VersionGate.check(version, range)) {
            case SUPPORTED -> {
                LOGGER.info("The {} adapter for {} {} is on", owner, modId, version);
                return true;
            }
            case UNSUPPORTED -> {
                warnings++;
                LOGGER.warn(
                        "The {} adapter for {} is off: it supports versions {}, and {} is installed",
                        owner,
                        modId,
                        range,
                        version);
                return false;
            }
            default -> {
                LOGGER.debug("{} isn't installed, so the {} adapter for it stays off", modId, owner);
                return false;
            }
        }
    }
}
