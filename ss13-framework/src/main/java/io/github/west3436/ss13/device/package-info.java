/**
 * The device base: {@link io.github.west3436.ss13.device.DeviceBlockEntity} block entities, stepped
 * once per device tick by {@link io.github.west3436.ss13.device.DeviceTicker}, with what every
 * device shares: a save format version ({@link io.github.west3436.ss13.device.DeviceData}, {@link
 * io.github.west3436.ss13.device.ItemData}), a redstone setting ({@link
 * io.github.west3436.ss13.device.RedstoneControl}), comparator signals ({@link
 * io.github.west3436.ss13.device.Comparators}), turning with a structure ({@link
 * io.github.west3436.ss13.device.BlockTurns}), wall panels ({@link
 * io.github.west3436.ss13.device.WallPanelBlock}), reads that load nothing ({@link
 * io.github.west3436.ss13.device.LoadedBlocks}) and how hard it works ({@link
 * io.github.west3436.ss13.device.DeviceActivity}), which nearby clients hear as a loop.
 */
package io.github.west3436.ss13.device;
