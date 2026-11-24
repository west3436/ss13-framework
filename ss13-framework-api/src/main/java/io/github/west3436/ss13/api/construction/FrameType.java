package io.github.west3436.ss13.api.construction;

/** The kind of frame a board goes in. */
public enum FrameType {
    /**
     * A machine frame, {@code ss13_framework:machine_frame}: anchor it, wire it, insert the board,
     * add the parts it names, then close it with a screwdriver.
     */
    MACHINE,

    /**
     * A computer frame, {@code ss13_framework:computer_frame}: anchor it, insert the board, secure
     * it with a screwdriver, wire it, add a screen, then close it with a screwdriver. A computer's
     * board names no parts.
     */
    COMPUTER
}
