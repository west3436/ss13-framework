package io.github.west3436.ss13.api.construction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * What a circuit board builds: the frame it goes in, the machine it makes and the parts that
 * machine needs. A mod registers one for each of its boards with {@link MachineBoards#register}.
 *
 * @param frame the frame the board goes in
 * @param machine the id of the block the finished frame becomes, such as {@code
 *     expedition:shuttle_computer}
 * @param parts the parts the machine needs, one requirement per kind; always empty for a {@link
 *     FrameType#COMPUTER computer}
 */
public record MachineBoard(FrameType frame, ResourceLocation machine, List<PartRequirement> parts) {
    /**
     * Checks the board.
     *
     * @param frame the frame the board goes in
     * @param machine the id of the block the finished frame becomes
     * @param parts the parts the machine needs
     * @throws IllegalArgumentException if a computer's board names parts, or two requirements name
     *     the same kind
     */
    public MachineBoard {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(machine, "machine");
        parts = List.copyOf(parts);
        if (frame == FrameType.COMPUTER && !parts.isEmpty()) {
            throw new IllegalArgumentException("computer " + machine + "'s board names parts; a computer takes none");
        }
        Set<TagKey<Item>> kinds = new HashSet<>();
        for (PartRequirement part : parts) {
            if (!kinds.add(part.kind())) {
                throw new IllegalArgumentException(
                        machine + "'s board names " + part.kind().location() + " twice");
            }
        }
    }

    /**
     * A machine frame's board.
     *
     * @param machine the id of the machine's block
     * @param parts the parts it needs, one requirement per kind
     * @return the board
     * @throws IllegalArgumentException if two requirements name the same kind
     */
    public static MachineBoard machine(ResourceLocation machine, PartRequirement... parts) {
        return new MachineBoard(FrameType.MACHINE, machine, List.of(parts));
    }

    /**
     * A computer frame's board.
     *
     * @param machine the id of the computer's block
     * @return the board
     */
    public static MachineBoard computer(ResourceLocation machine) {
        return new MachineBoard(FrameType.COMPUTER, machine, List.of());
    }

    /**
     * How many parts the machine needs in all.
     *
     * @return the sum of the requirements' counts
     */
    public int partCount() {
        return parts.stream().mapToInt(PartRequirement::count).sum();
    }
}
