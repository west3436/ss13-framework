package io.github.west3436.ss13.device;

/** A device with a redstone setting: see {@link RedstoneControl} for how a device takes one. */
public interface RedstoneControlled {
    /** Its redstone setting. */
    RedstoneControl redstone();
}
