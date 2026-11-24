package io.github.west3436.ss13.api.construction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The circuit boards every mod has registered, each by its item's id: the board to machine
 * registry.
 *
 * <p>A board is an ordinary item the mod registers itself, such as {@code
 * expedition:shuttle_computer_board}; registering it here is what makes it a board. A mod calls
 * {@link #register} once per board, during mod construction, with the board's item id and what it
 * builds. The frames then take it, its tooltip lists what it needs, and a machine placed from its
 * own item gets the board and stock parts as if built from a frame.
 *
 * <p>A machine has one board. If two boards name the same machine, {@link #boardFor} answers with
 * the first registered.
 *
 * <p>Thread: registering may happen on any thread; reading too.
 */
public final class MachineBoards {
    private static volatile Map<ResourceLocation, MachineBoard> boards = Map.of();

    private MachineBoards() {}

    /**
     * Registers a board, usually during mod construction.
     *
     * @param board the board item's id, such as {@code expedition:shuttle_computer_board}
     * @param spec what it builds
     * @throws IllegalArgumentException if a board is already registered under that id
     */
    public static synchronized void register(ResourceLocation board, MachineBoard spec) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(spec, "spec");
        if (boards.containsKey(board)) {
            throw new IllegalArgumentException("a board is already registered as " + board);
        }
        Map<ResourceLocation, MachineBoard> next = new LinkedHashMap<>(boards);
        next.put(board, spec);
        boards = Collections.unmodifiableMap(next);
    }

    /**
     * What a board builds.
     *
     * @param board the board item's id
     * @return what it builds, or empty if no board is registered as it
     */
    public static Optional<MachineBoard> get(ResourceLocation board) {
        Objects.requireNonNull(board, "board");
        return Optional.ofNullable(boards.get(board));
    }

    /**
     * What a stack builds, if it is a board.
     *
     * @param stack the stack
     * @return what it builds, or empty if it isn't a registered board
     */
    public static Optional<MachineBoard> of(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * The board that builds a machine.
     *
     * @param machine the machine block's id
     * @return the board item's id, the first registered if several build it, or empty if none does
     */
    public static Optional<ResourceLocation> boardFor(ResourceLocation machine) {
        Objects.requireNonNull(machine, "machine");
        for (Map.Entry<ResourceLocation, MachineBoard> entry : boards.entrySet()) {
            if (entry.getValue().machine().equals(machine)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * Every registered board, by its item's id, in the order they were registered.
     *
     * @return the boards
     */
    public static Map<ResourceLocation, MachineBoard> all() {
        return boards;
    }
}
