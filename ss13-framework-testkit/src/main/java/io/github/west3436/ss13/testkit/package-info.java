/**
 * Helpers for the tests of mods that use the framework. Add the {@code ss13-framework-testkit} jar
 * to a mod's {@code gametest} source set, and to its {@code test} source set for {@link
 * io.github.west3436.ss13.testkit.PlayerTextScan}; never nest it or put it in a mod jar.
 *
 * <ul>
 *   <li>{@link io.github.west3436.ss13.testkit.TestPlayers}: a fake player that damage reaches, and
 *       a payload context from a player.
 *   <li>{@link io.github.west3436.ss13.testkit.ListeningPlayer}: a player that keeps every packet the
 *       server sends it.
 *   <li>{@link io.github.west3436.ss13.testkit.SoundLog}: the sounds the server played, by position.
 *   <li>{@link io.github.west3436.ss13.testkit.FaultLog}: the errors logged while it is open.
 *   <li>{@link io.github.west3436.ss13.testkit.PlayerTextScan}: the strings in compiled classes that
 *       can reach a player as text, and the translation keys they name.
 * </ul>
 */
package io.github.west3436.ss13.testkit;
