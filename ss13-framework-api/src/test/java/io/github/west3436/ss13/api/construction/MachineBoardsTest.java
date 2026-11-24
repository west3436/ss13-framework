package io.github.west3436.ss13.api.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MachineBoardsTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ss13_unit_test", path);
    }

    @Test
    void a_board_is_found_by_its_item_and_by_its_machine() {
        MachineBoard board = MachineBoard.machine(
                id("lathe"), PartRequirement.of(PartTags.MATTER_BINS, 2), PartRequirement.of(PartTags.MANIPULATORS, 1));
        MachineBoards.register(id("lathe_board"), board);
        assertEquals(Optional.of(board), MachineBoards.get(id("lathe_board")));
        assertEquals(Optional.of(id("lathe_board")), MachineBoards.boardFor(id("lathe")));
        assertEquals(3, board.partCount());
        assertEquals(Optional.empty(), MachineBoards.boardFor(id("nothing")));
        assertTrue(MachineBoards.all().containsKey(id("lathe_board")));
    }

    @Test
    void a_board_registers_once() {
        MachineBoards.register(id("once_board"), MachineBoard.computer(id("once")));
        assertThrows(
                IllegalArgumentException.class,
                () -> MachineBoards.register(id("once_board"), MachineBoard.computer(id("other"))));
    }

    @Test
    void a_computer_board_names_no_parts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MachineBoard(
                        FrameType.COMPUTER, id("console"), List.of(PartRequirement.of(PartTags.CAPACITORS, 1))));
        assertEquals(0, MachineBoard.computer(id("console")).partCount());
    }

    @Test
    void a_board_names_each_kind_once_and_at_least_one_of_it() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MachineBoard.machine(
                        id("twice"), PartRequirement.of(PartTags.LASERS, 1), PartRequirement.of(PartTags.LASERS, 2)));
        assertThrows(IllegalArgumentException.class, () -> PartRequirement.of(PartTags.LASERS, 0));
    }

    @Test
    void tiers_run_from_one_to_four() {
        assertEquals(
                ResourceLocation.parse("ss13_framework:parts/capacitors/tier_4"),
                PartTags.tier(PartTags.CAPACITORS, 4).location());
        assertEquals(
                ResourceLocation.parse("ss13_framework:parts/tier_1"),
                PartTags.tier(1).location());
        assertThrows(IllegalArgumentException.class, () -> PartTags.tier(0));
        assertThrows(IllegalArgumentException.class, () -> PartTags.tier(PartTags.CAPACITORS, PartTags.MAX_TIER + 1));
        assertEquals(5, PartTags.kinds().size());
    }
}
