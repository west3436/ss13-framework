/**
 * SS13's computers as consoles 2 blocks wide, 2 tall and 1 deep. A mod's computer extends {@link
 * io.github.west3436.ss13.api.console.ConsoleBlock}, which places, breaks, shapes, turns and
 * mirrors the four blocks as one, and supplies only its block entity, which only the lower left
 * block has, and what using it does. The framework's computer frame is one, and a computer frame
 * whose board builds a {@code ConsoleBlock} becomes the whole console.
 */
package io.github.west3436.ss13.api.console;
