/**
 * Adapters for other mods: the loader that starts each one only when its target is installed in a
 * supported version, for every mod that nests the framework.
 *
 * <p>{@link io.github.west3436.ss13.compat.Adapters} is one mod's table and loads it; {@link
 * io.github.west3436.ss13.compat.PluginGate} gates a plugin that its target starts itself; {@link
 * io.github.west3436.ss13.compat.VersionGate} is the version check alone. The adapters themselves
 * live in the mods that have them, and nothing here names their classes or their targets'.
 */
package io.github.west3436.ss13.compat;
