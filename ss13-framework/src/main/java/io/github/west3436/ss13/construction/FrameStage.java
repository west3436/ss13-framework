package io.github.west3436.ss13.construction;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * How far a frame is built, as its {@code stage} block state property.
 *
 * <ul>
 *   <li>A machine frame goes {@link #EMPTY}, {@link #WIRED}, {@link #BOARD} (board in, parts going
 *       in), then becomes the machine.
 *   <li>A computer frame goes {@link #EMPTY}, {@link #BOARD}, {@link #SECURED} (board screwed in),
 *       {@link #WIRED}, {@link #SCREEN}, then becomes the computer.
 * </ul>
 */
public enum FrameStage implements StringRepresentable {
    /** Nothing in it. */
    EMPTY,
    /** Wired with a cable coil; a computer's board is secured as well. */
    WIRED,
    /** A board in it, loose in a computer frame. */
    BOARD,
    /** A computer's board, screwed in. */
    SECURED,
    /** A computer's screen on. */
    SCREEN;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
