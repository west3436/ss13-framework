package io.github.west3436.ss13.api.label;

import org.jetbrains.annotations.Nullable;

/**
 * The window of a device that takes a label. Its title bar is where a player types one, and the
 * server sets it on the device this names: the {@code ss13_framework} mod's label payload reaches
 * only the device whose window the sender has open.
 */
public interface LabelledMenu {
    /**
     * The device the window is of.
     *
     * @return the device, on the server; null on the client
     */
    @Nullable
    Labelled labelled();
}
