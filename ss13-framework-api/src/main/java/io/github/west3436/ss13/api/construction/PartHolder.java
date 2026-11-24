package io.github.west3436.ss13.api.construction;

/**
 * A machine built from a frame: the block entity of a machine some {@link MachineBoard} names
 * implements this, so the framework can put its board and parts in, take them out, and swap them.
 * The machine keeps both, saves both (with {@link InstalledParts#save} and {@link
 * InstalledParts#load}) and sets its stats from its parts.
 *
 * <p>What the framework does with a holder, on the server:
 *
 * <ul>
 *   <li><b>Built.</b> When a frame is closed, the frame becomes the machine's block (facing the way
 *       the frame faced, if it has a facing property) and the framework calls {@link #installParts}
 *       with the frame's board and parts.
 *   <li><b>Placed.</b> When a player places the machine from its item, which its plain crafting
 *       recipe makes, the framework installs the parts the item carries, if it was a mined machine,
 *       or else the board and a tier-1 part for each one the board needs.
 *   <li><b>Mined.</b> The machine's item keeps its board and parts, so placing it again puts them
 *       back. If its loot doesn't drop its item, the board and parts drop instead.
 *   <li><b>Machine frames.</b> A screwdriver opens and closes the maintenance panel ({@link
 *       #setPanelOpen}). With it open, a better part of a kind the board needs replaces the
 *       lowest-tier part of that kind, which goes to the player, and a crowbar takes the machine
 *       apart: the block becomes a wired machine frame and the board and parts drop.
 *   <li><b>Computer frames.</b> A screwdriver takes the screen off: the block becomes a computer
 *       frame with its board, wiring and screen, which a screwdriver closes again.
 * </ul>
 *
 * <p>A machine that has nothing installed ({@link InstalledParts#EMPTY}) counts as holding tier-1
 * parts.
 */
public interface PartHolder {
    /**
     * The board and parts in the machine.
     *
     * @return them, {@link InstalledParts#EMPTY} if none have been installed
     */
    InstalledParts installedParts();

    /**
     * Replaces the board and parts in the machine and sets its stats from them. The machine saves
     * them and marks itself changed. Server thread.
     *
     * @param parts the board and parts; {@link InstalledParts#EMPTY} as the framework takes the
     *     machine apart
     */
    void installParts(InstalledParts parts);

    /**
     * Whether the maintenance panel is open. Only machine frames' machines have a panel; a
     * computer's is never open.
     *
     * @return true if it is
     */
    boolean panelOpen();

    /**
     * Opens or closes the maintenance panel. The machine saves it and marks itself changed. A
     * machine may refuse to work while it is open. Server thread.
     *
     * @param open true to open it
     */
    void setPanelOpen(boolean open);
}
