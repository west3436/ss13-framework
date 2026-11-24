package io.github.west3436.ss13.construction;

import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.construction.ConstructionTags;
import io.github.west3436.ss13.api.construction.FrameType;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.api.construction.MachineBoard;
import io.github.west3436.ss13.api.construction.MachineBoards;
import io.github.west3436.ss13.api.construction.PartHolder;
import io.github.west3436.ss13.api.construction.PartRequirement;
import io.github.west3436.ss13.api.construction.PartTags;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import io.github.west3436.ss13.registry.FrameworkItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

/**
 * How frames are built and machines taken apart, SS13's way, with tools found by their {@link
 * ConstructionTags tags}. Every step happens on the server and tells the player what happened, or
 * what comes next, above the hotbar.
 *
 * <p>A <b>machine frame</b> is anchored with a wrench, wired with a cable coil, takes a machine
 * board, then the parts the board names (any tier), and a screwdriver closes it into the machine.
 * Wirecutters take the wiring back out, and a crowbar pries out the board and parts.
 *
 * <p>A <b>computer frame</b> is anchored, takes a computer board, which a screwdriver secures, then
 * a cable coil and a console screen, and a screwdriver closes it. A crowbar pries out the screen
 * or a loose board, and wirecutters cut the wiring.
 *
 * <p>A built machine's screwdriver opens its panel; with the panel open, a better part replaces
 * its worst of that kind and a crowbar takes it apart to a wired frame. A computer's screwdriver
 * takes its screen off, back to a finished frame.
 */
public final class Construction {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MESSAGE = "message.ss13_framework.construction.";

    private Construction() {}

    // Frames

    /**
     * Uses an item on a frame. Server only.
     *
     * @param level the level
     * @param pos the frame's position
     * @param state the frame's state
     * @param player who uses it
     * @param stack what they use
     */
    static void useOnFrame(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!(state.getBlock() instanceof FrameBlock frame)
                || !(level.getBlockEntity(pos) instanceof FrameBlockEntity be)) {
            return;
        }
        boolean anchored = state.getValue(FrameBlock.ANCHORED);
        FrameStage stage = state.getValue(frame.stage());
        if (stack.is(ConstructionTags.WRENCH)) {
            if (!anchored) {
                level.setBlockAndUpdate(pos, state.setValue(FrameBlock.ANCHORED, true));
                done(level, pos, player, SoundEvents.IRON_TRAPDOOR_CLOSE, Component.translatable(MESSAGE + "anchored"));
                return;
            }
            if (stage == FrameStage.EMPTY) {
                level.setBlockAndUpdate(pos, state.setValue(FrameBlock.ANCHORED, false));
                done(
                        level,
                        pos,
                        player,
                        SoundEvents.IRON_TRAPDOOR_OPEN,
                        Component.translatable(MESSAGE + "unanchored"));
                return;
            }
        } else if (!anchored) {
            tell(player, Component.translatable(MESSAGE + "anchor_first"));
            return;
        } else if (frame.type() == FrameType.MACHINE
                ? useOnMachineFrame(level, pos, state, frame, be, stage, player, stack)
                : useOnComputerFrame(level, pos, state, frame, be, stage, player, stack)) {
            return;
        }
        describeFrame(level, pos, state, player);
    }

    private static boolean useOnMachineFrame(
            Level level,
            BlockPos pos,
            BlockState state,
            FrameBlock frame,
            FrameBlockEntity be,
            FrameStage stage,
            Player player,
            ItemStack stack) {
        switch (stage) {
            case EMPTY -> {
                if (stack.is(ConstructionTags.CABLE_COILS)) {
                    stack.consume(1, player);
                    setStage(level, pos, state, frame, FrameStage.WIRED);
                    done(level, pos, player, SoundEvents.WOOL_PLACE, Component.translatable(MESSAGE + "wired"));
                    return true;
                }
            }
            case WIRED -> {
                if (stack.is(ConstructionTags.WIRECUTTER)) {
                    Block.popResource(level, pos, FrameworkItems.CABLE_COIL.toStack());
                    setStage(level, pos, state, frame, FrameStage.EMPTY);
                    done(level, pos, player, SoundEvents.SHEEP_SHEAR, Component.translatable(MESSAGE + "unwired"));
                    return true;
                }
                Optional<MachineBoard> board = MachineBoards.of(stack);
                if (board.isPresent()) {
                    if (insertBoard(level, pos, state, frame, be, player, stack, board.get(), FrameStage.BOARD)) {
                        tell(player, nextStep(level, pos));
                    }
                    return true;
                }
            }
            case BOARD -> {
                Optional<MachineBoard> board = MachineBoards.of(be.contents().board());
                if (stack.is(ConstructionTags.CROWBAR)) {
                    dropContents(level, pos, be);
                    setStage(level, pos, state, frame, FrameStage.WIRED);
                    done(
                            level,
                            pos,
                            player,
                            SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                            Component.translatable(MESSAGE + "pried"));
                    return true;
                }
                if (stack.is(ConstructionTags.SCREWDRIVER)) {
                    if (board.isPresent()
                            && remaining(board.get(), be.contents().parts()).isEmpty()) {
                        build(level, pos, state, be, player);
                    } else {
                        tell(player, nextStep(level, pos));
                    }
                    return true;
                }
                if (board.isPresent()) {
                    return addPart(level, pos, be, board.get(), player, stack);
                }
            }
            default -> {}
        }
        return false;
    }

    private static boolean useOnComputerFrame(
            Level level,
            BlockPos pos,
            BlockState state,
            FrameBlock frame,
            FrameBlockEntity be,
            FrameStage stage,
            Player player,
            ItemStack stack) {
        switch (stage) {
            case EMPTY -> {
                Optional<MachineBoard> board = MachineBoards.of(stack);
                if (board.isPresent()) {
                    if (insertBoard(level, pos, state, frame, be, player, stack, board.get(), FrameStage.BOARD)) {
                        tell(player, nextStep(level, pos));
                    }
                    return true;
                }
            }
            case BOARD -> {
                if (stack.is(ConstructionTags.SCREWDRIVER)) {
                    setStage(level, pos, state, frame, FrameStage.SECURED);
                    done(
                            level,
                            pos,
                            player,
                            SoundEvents.IRON_TRAPDOOR_CLOSE,
                            Component.translatable(MESSAGE + "secured"));
                    return true;
                }
                if (stack.is(ConstructionTags.CROWBAR)) {
                    dropContents(level, pos, be);
                    setStage(level, pos, state, frame, FrameStage.EMPTY);
                    done(
                            level,
                            pos,
                            player,
                            SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                            Component.translatable(MESSAGE + "pried"));
                    return true;
                }
            }
            case SECURED -> {
                if (stack.is(ConstructionTags.SCREWDRIVER)) {
                    setStage(level, pos, state, frame, FrameStage.BOARD);
                    done(
                            level,
                            pos,
                            player,
                            SoundEvents.IRON_TRAPDOOR_OPEN,
                            Component.translatable(MESSAGE + "loosened"));
                    return true;
                }
                if (stack.is(ConstructionTags.CABLE_COILS)) {
                    stack.consume(1, player);
                    setStage(level, pos, state, frame, FrameStage.WIRED);
                    done(level, pos, player, SoundEvents.WOOL_PLACE, Component.translatable(MESSAGE + "wired"));
                    return true;
                }
            }
            case WIRED -> {
                if (stack.is(ConstructionTags.WIRECUTTER)) {
                    Block.popResource(level, pos, FrameworkItems.CABLE_COIL.toStack());
                    setStage(level, pos, state, frame, FrameStage.SECURED);
                    done(level, pos, player, SoundEvents.SHEEP_SHEAR, Component.translatable(MESSAGE + "unwired"));
                    return true;
                }
                if (stack.is(ConstructionTags.CONSOLE_SCREENS)) {
                    stack.consume(1, player);
                    setStage(level, pos, state, frame, FrameStage.SCREEN);
                    done(level, pos, player, SoundEvents.GLASS_PLACE, Component.translatable(MESSAGE + "screen_on"));
                    return true;
                }
            }
            case SCREEN -> {
                if (stack.is(ConstructionTags.CROWBAR)) {
                    Block.popResource(level, pos, FrameworkItems.CONSOLE_SCREEN.toStack());
                    setStage(level, pos, state, frame, FrameStage.WIRED);
                    done(
                            level,
                            pos,
                            player,
                            SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                            Component.translatable(MESSAGE + "screen_off"));
                    return true;
                }
                if (stack.is(ConstructionTags.SCREWDRIVER)) {
                    build(level, pos, state, be, player);
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    private static boolean insertBoard(
            Level level,
            BlockPos pos,
            BlockState state,
            FrameBlock frame,
            FrameBlockEntity be,
            Player player,
            ItemStack stack,
            MachineBoard board,
            FrameStage next) {
        if (board.frame() != frame.type()) {
            tell(
                    player,
                    Component.translatable(
                            MESSAGE + "wrong_frame." + board.frame().name().toLowerCase(Locale.ROOT)));
            return false;
        }
        be.setContents(new InstalledParts(stack, List.of()));
        stack.consume(1, player);
        setStage(level, pos, state, frame, next);
        sound(level, pos, SoundEvents.ITEM_FRAME_ADD_ITEM);
        return true;
    }

    private static boolean addPart(
            Level level, BlockPos pos, FrameBlockEntity be, MachineBoard board, Player player, ItemStack stack) {
        List<ItemStack> parts = new ArrayList<>(be.contents().parts());
        Map<PartRequirement, Integer> remaining = remaining(board, parts);
        for (PartRequirement requirement : remaining.keySet()) {
            if (requirement.accepts(stack)) {
                parts.add(stack.copyWithCount(1));
                be.setContents(new InstalledParts(be.contents().board(), parts));
                Component name = stack.getHoverName();
                stack.consume(1, player);
                sound(level, pos, SoundEvents.ITEM_FRAME_ADD_ITEM);
                tell(player, Component.translatable(MESSAGE + "part_added", name, nextStep(level, pos)));
                return true;
            }
        }
        if (stack.is(PartTags.PARTS) || board.parts().stream().anyMatch(r -> r.accepts(stack))) {
            tell(player, Component.translatable(MESSAGE + "part_not_needed", stack.getHoverName()));
            return true;
        }
        return false;
    }

    private static void build(Level level, BlockPos pos, BlockState state, FrameBlockEntity be, Player player) {
        InstalledParts contents = be.contents();
        Optional<MachineBoard> board = MachineBoards.of(contents.board());
        Optional<Block> machine = board.flatMap(b -> BuiltInRegistries.BLOCK.getOptional(b.machine()));
        if (machine.isEmpty()) {
            tell(
                    player,
                    Component.translatable(
                            MESSAGE + "unknown_machine", contents.board().getHoverName()));
            return;
        }
        be.markBuilt();
        level.setBlockAndUpdate(pos, facing(machine.get().defaultBlockState(), state.getValue(FrameBlock.FACING)));
        if (level.getBlockEntity(pos) instanceof PartHolder holder) {
            holder.setPanelOpen(false);
            holder.installParts(contents);
        } else {
            LOGGER.warn(
                    "{} is built from a board but its block entity isn't a PartHolder; its board and parts drop",
                    board.get().machine());
            drop(level, pos, contents);
        }
        done(
                level,
                pos,
                player,
                SoundEvents.IRON_DOOR_CLOSE,
                Component.translatable(MESSAGE + "built", machine.get().getName()));
    }

    /**
     * Tells a player what a frame needs next. Server only.
     *
     * @param level the level
     * @param pos the frame's position
     * @param state the frame's state
     * @param player who looks
     */
    static void describeFrame(Level level, BlockPos pos, BlockState state, Player player) {
        tell(player, nextStep(level, pos));
    }

    /**
     * What a frame needs next, as one line.
     *
     * @param level the level
     * @param pos the frame's position
     * @return the line
     */
    public static Component nextStep(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FrameBlock frame)
                || !(level.getBlockEntity(pos) instanceof FrameBlockEntity be)) {
            return Component.empty();
        }
        if (!state.getValue(FrameBlock.ANCHORED)) {
            return Component.translatable(MESSAGE + "next.anchor");
        }
        FrameStage stage = state.getValue(frame.stage());
        Optional<MachineBoard> board = MachineBoards.of(be.contents().board());
        if (frame.type() == FrameType.MACHINE) {
            return switch (stage) {
                case EMPTY -> Component.translatable(MESSAGE + "next.wire");
                case WIRED -> Component.translatable(MESSAGE + "next.machine_board");
                default -> {
                    Map<PartRequirement, Integer> remaining =
                            board.map(b -> remaining(b, be.contents().parts())).orElse(Map.of());
                    yield remaining.isEmpty()
                            ? Component.translatable(MESSAGE + "next.close")
                            : Component.translatable(MESSAGE + "next.parts", list(remaining));
                }
            };
        }
        return switch (stage) {
            case EMPTY -> Component.translatable(MESSAGE + "next.computer_board");
            case BOARD -> Component.translatable(MESSAGE + "next.secure");
            case SECURED -> Component.translatable(MESSAGE + "next.wire");
            case WIRED -> Component.translatable(MESSAGE + "next.screen");
            default -> Component.translatable(MESSAGE + "next.close");
        };
    }

    /**
     * Whether a frame in a stage has its wiring in.
     *
     * @param frame the frame
     * @param stage its stage
     * @return true if it has
     */
    static boolean isWired(FrameBlock frame, FrameStage stage) {
        return frame.type() == FrameType.MACHINE
                ? stage == FrameStage.WIRED || stage == FrameStage.BOARD
                : stage == FrameStage.WIRED || stage == FrameStage.SCREEN;
    }

    /**
     * The parts a board still needs, given those already in: each part counts toward the first
     * requirement that takes it and isn't yet met.
     *
     * @param board the board
     * @param parts the parts in
     * @return each requirement not yet met, with how many more it needs, in the board's order
     */
    public static Map<PartRequirement, Integer> remaining(MachineBoard board, List<ItemStack> parts) {
        Map<PartRequirement, Integer> remaining = new LinkedHashMap<>();
        board.parts().forEach(r -> remaining.put(r, r.count()));
        for (ItemStack part : parts) {
            for (PartRequirement requirement : board.parts()) {
                int left = remaining.getOrDefault(requirement, 0);
                if (left > 0 && requirement.accepts(part)) {
                    remaining.put(requirement, left - 1);
                    break;
                }
            }
        }
        remaining.values().removeIf(n -> n == 0);
        return remaining;
    }

    /**
     * Lists requirements as "2 × Capacitors, 1 × Manipulators".
     *
     * @param requirements each requirement with a count
     * @return the list
     */
    public static Component list(Map<PartRequirement, Integer> requirements) {
        List<Component> entries = new ArrayList<>();
        requirements.forEach((r, n) -> entries.add(Component.translatable(MESSAGE + "count", n, kindName(r.kind()))));
        return ComponentUtils.formatList(entries, Component.literal(", "));
    }

    /**
     * A part kind's name: its tag's translation, {@code tag.item.<namespace>.<path with dots>}.
     *
     * @param kind the kind
     * @return the name, or the tag's id where it has no translation
     */
    public static MutableComponent kindName(TagKey<Item> kind) {
        ResourceLocation id = kind.location();
        return Component.translatableWithFallback(
                "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'), "#" + id);
    }

    // Machines

    /** A built machine: its holder, its board's id and what the board builds. */
    record Machine(PartHolder holder, ResourceLocation boardId, MachineBoard board, BlockState state) {}

    /**
     * The machine at a position, if it is one some board builds and its block entity is a {@link
     * PartHolder}.
     *
     * @param level the level
     * @param pos the position
     * @return the machine, or empty
     */
    static Optional<Machine> machineAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PartHolder holder)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        ItemStack installed = holder.installedParts().board();
        Optional<ResourceLocation> boardId = installed.isEmpty()
                ? MachineBoards.boardFor(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                : Optional.of(BuiltInRegistries.ITEM.getKey(installed.getItem()));
        return boardId.flatMap(id -> MachineBoards.get(id).map(b -> new Machine(holder, id, b, state)));
    }

    /**
     * Whether a tool used on a block is one construction takes on a built machine there, so the
     * client can leave it to the server.
     *
     * @param level the level
     * @param pos the position
     * @param stack the tool
     * @return true if the block is a built machine and the stack a screwdriver or crowbar
     */
    static boolean isMachineTool(Level level, BlockPos pos, ItemStack stack) {
        return (stack.is(ConstructionTags.SCREWDRIVER) || stack.is(ConstructionTags.CROWBAR))
                && machineAt(level, pos).isPresent();
    }

    /**
     * Uses an item on a built machine: a screwdriver, a crowbar, or a part through an open panel.
     * Server only.
     *
     * @param level the level
     * @param pos the machine's position
     * @param player who uses it
     * @param stack what they use
     * @return true if construction took the use, so the machine's own use mustn't run
     */
    static boolean useOnMachine(Level level, BlockPos pos, Player player, ItemStack stack) {
        Optional<Machine> found = machineAt(level, pos);
        if (found.isEmpty()) {
            return false;
        }
        Machine machine = found.get();
        boolean computer = machine.board().frame() == FrameType.COMPUTER;
        if (stack.is(ConstructionTags.SCREWDRIVER)) {
            if (computer) {
                InstalledParts parts = effective(machine);
                machine.holder().installParts(InstalledParts.EMPTY);
                level.setBlockAndUpdate(
                        pos, FrameworkBlocks.COMPUTER_FRAME.get().state(FrameStage.SCREEN, facingOf(machine.state())));
                if (level.getBlockEntity(pos) instanceof FrameBlockEntity frame) {
                    frame.setContents(parts);
                }
                done(
                        level,
                        pos,
                        player,
                        SoundEvents.IRON_TRAPDOOR_OPEN,
                        Component.translatable(MESSAGE + "screen_loose"));
            } else {
                boolean open = !machine.holder().panelOpen();
                machine.holder().setPanelOpen(open);
                done(
                        level,
                        pos,
                        player,
                        open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                        Component.translatable(MESSAGE + (open ? "panel_opened" : "panel_closed")));
            }
            return true;
        }
        if (stack.is(ConstructionTags.CROWBAR)) {
            if (computer) {
                tell(player, Component.translatable(MESSAGE + "screen_first"));
            } else if (!machine.holder().panelOpen()) {
                tell(player, Component.translatable(MESSAGE + "panel_first"));
            } else {
                dismantle(level, pos, machine, player);
            }
            return true;
        }
        if (!computer && machine.holder().panelOpen()) {
            return swapPart(level, pos, machine, player, stack);
        }
        return false;
    }

    private static void dismantle(Level level, BlockPos pos, Machine machine, Player player) {
        InstalledParts parts = effective(machine);
        machine.holder().installParts(InstalledParts.EMPTY);
        machine.holder().setPanelOpen(false);
        level.setBlockAndUpdate(
                pos, FrameworkBlocks.MACHINE_FRAME.get().state(FrameStage.WIRED, facingOf(machine.state())));
        drop(level, pos, parts);
        done(level, pos, player, SoundEvents.ITEM_FRAME_REMOVE_ITEM, Component.translatable(MESSAGE + "dismantled"));
    }

    private static boolean swapPart(Level level, BlockPos pos, Machine machine, Player player, ItemStack stack) {
        PartRequirement requirement = null;
        for (PartRequirement r : machine.board().parts()) {
            if (r.accepts(stack)) {
                requirement = r;
                break;
            }
        }
        if (requirement == null) {
            if (stack.is(PartTags.PARTS)) {
                tell(player, Component.translatable(MESSAGE + "part_not_needed", stack.getHoverName()));
                return true;
            }
            return false;
        }
        InstalledParts installed = effective(machine);
        List<ItemStack> parts = installed.parts();
        int worst = -1;
        int worstTier = Integer.MAX_VALUE;
        for (int i = 0; i < parts.size(); i++) {
            int tier = PartTags.tierOf(parts.get(i), requirement.kind());
            if (tier > 0 && tier < worstTier) {
                worst = i;
                worstTier = tier;
            }
        }
        if (worst < 0 || worstTier >= PartTags.tierOf(stack, requirement.kind())) {
            tell(player, Component.translatable(MESSAGE + "part_not_better", stack.getHoverName()));
            return true;
        }
        ItemStack old = parts.get(worst);
        Component name = stack.getHoverName();
        machine.holder().installParts(installed.withPart(worst, stack.copyWithCount(1)));
        stack.consume(1, player);
        player.getInventory().placeItemBackInInventory(old);
        done(
                level,
                pos,
                player,
                SoundEvents.ITEM_FRAME_ADD_ITEM,
                Component.translatable(MESSAGE + "part_swapped", old.getHoverName(), name));
        return true;
    }

    /**
     * Puts the board and parts into a machine a player has just placed from its item: those the item
     * carries, or else the board and tier-1 parts. Server only.
     *
     * @param level the level
     * @param pos the machine's position
     * @param placer who placed it, whose held item is read
     */
    static void placed(Level level, BlockPos pos, LivingEntity placer) {
        Optional<Machine> found = machineAt(level, pos);
        if (found.isEmpty() || !found.get().holder().installedParts().isEmpty()) {
            return;
        }
        Machine machine = found.get();
        Item item = machine.state().getBlock().asItem();
        InstalledParts carried = null;
        if (placer != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = placer.getItemInHand(hand);
                if (held.is(item) && held.has(FrameworkBlocks.INSTALLED_PARTS.get())) {
                    carried = held.get(FrameworkBlocks.INSTALLED_PARTS.get());
                    break;
                }
            }
        }
        machine.holder().installParts(carried != null ? carried : stock(machine.boardId(), machine.board()));
    }

    /**
     * The board and parts a machine counts as holding: those installed, or the board and tier-1
     * parts if none are.
     *
     * @param machine the machine
     * @return them
     */
    static InstalledParts effective(Machine machine) {
        InstalledParts installed = machine.holder().installedParts();
        return installed.isEmpty() ? stock(machine.boardId(), machine.board()) : installed;
    }

    /**
     * A board and the tier-1 parts it needs: what a machine crafted by its plain recipe holds. Each
     * part is the first item in its kind's {@code tier_1} tag, or in the kind's own tag if that has
     * no tiers. Reads item tags.
     *
     * @param boardId the board item's id
     * @param board what the board builds
     * @return the board and parts
     */
    public static InstalledParts stock(ResourceLocation boardId, MachineBoard board) {
        ItemStack boardStack =
                BuiltInRegistries.ITEM.getOptional(boardId).map(ItemStack::new).orElse(ItemStack.EMPTY);
        List<ItemStack> parts = new ArrayList<>();
        for (PartRequirement requirement : board.parts()) {
            Optional<Item> part = first(PartTags.tier(requirement.kind(), 1)).or(() -> first(requirement.kind()));
            if (part.isEmpty()) {
                LOGGER.warn(
                        "No item is a {}, which {} needs", requirement.kind().location(), board.machine());
                continue;
            }
            for (int i = 0; i < requirement.count(); i++) {
                parts.add(new ItemStack(part.get()));
            }
        }
        return new InstalledParts(boardStack, parts);
    }

    private static Optional<Item> first(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM
                .getTag(tag)
                .flatMap(named -> named.stream().findFirst())
                .map(Holder::value);
    }

    // Helpers

    private static BlockState facing(BlockState state, Direction facing) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, facing);
        }
        return state;
    }

    private static Direction facingOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)
                && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return Direction.NORTH;
    }

    private static void setStage(Level level, BlockPos pos, BlockState state, FrameBlock frame, FrameStage stage) {
        level.setBlockAndUpdate(pos, state.setValue(frame.stage(), stage));
    }

    private static void dropContents(Level level, BlockPos pos, FrameBlockEntity be) {
        drop(level, pos, be.contents());
        be.setContents(InstalledParts.EMPTY);
    }

    private static void drop(Level level, BlockPos pos, InstalledParts parts) {
        if (!parts.board().isEmpty()) {
            Block.popResource(level, pos, parts.board());
        }
        parts.parts().forEach(part -> Block.popResource(level, pos, part));
    }

    private static void done(Level level, BlockPos pos, Player player, SoundEvent sound, Component message) {
        sound(level, pos, sound);
        tell(player, message);
    }

    private static void sound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
