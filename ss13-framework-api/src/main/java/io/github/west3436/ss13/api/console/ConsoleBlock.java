package io.github.west3436.ss13.api.console;

import io.github.west3436.ss13.api.device.BlockMovers;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A console 2 blocks wide, 2 tall and 1 deep, as SS13's computers are: ZAS³'s central atmospherics
 * controller, Expedition's Shuttle Computer and the framework's computer frame. Its {@link #FACING}
 * is the way its front looks, toward the player who placed it. Each of its four blocks is one
 * {@link #HALF} (lower or upper) and one {@link #SIDE} (left or right, as seen from the front).
 *
 * <ul>
 *   <li><b>Placing.</b> The block placed is the lower left one, the console's <em>origin</em>, and
 *       the console grows up and to its right as the placing player sees it ({@link #toRight}).
 *       All four spaces must be free. {@link #placeConsole} places a whole console without a
 *       player.
 *   <li><b>Breaking.</b> Breaking any block breaks the whole console. Only the origin should drop
 *       the item, which it does whichever block was broken, and not in creative or without the
 *       right tool: the subclass's loot table gives its item only for {@code half=lower} and
 *       {@code side=left}.
 *   <li><b>Its block entity.</b> Only the origin has one, made by {@link #newConsoleEntity}. The
 *       other three send use and items to it: {@link #useConsole} and {@link #useItemOnConsole}
 *       are called with the origin's position and state, whichever block was used. {@link
 *       #originAt} and {@link #entityAt} find the origin and its block entity from any block, for
 *       a capability provider registered on the console's block.
 *   <li><b>Its shape.</b> Each block is a full block to collide with, so the console is gas-tight,
 *       and its selection outline is the whole console from each block. It can't be pushed by a
 *       piston.
 * </ul>
 *
 * <p>A subclass supplies its codec, its block entity and its use. It may add block state
 * properties of its own: it overrides {@link #createBlockStateDefinition}, calls this class's, then
 * adds its own, and sets their defaults with {@code registerDefaultState(defaultBlockState()...)}
 * in its constructor. {@link #matchPartner} keeps such a property alike on all four blocks, and
 * {@link #consolePlaced} is told when a player has placed the console, to record its owner.
 *
 * <p>A block saved before its block became a console, such as a one-block computer, loads as a
 * lone origin, with the other three spaces as they were. It works as before, and {@link
 * #completeConsole} adds the missing blocks when their spaces are free. Until then a change to a
 * block in one of those spaces breaks it, as it would break a whole console, so it drops its
 * item.
 *
 * <p>The block model is the subclass's: one model per part, named by its {@code half} and {@code
 * side}, each turned by the blockstate's {@code y} for its facing. Seen from the front of a
 * console facing north, the origin is the east column.
 */
public abstract class ConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Whether a block is the lower or upper row of the console. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Whether a block is the left or right column of the console, as seen from its front. */
    public static final EnumProperty<ConsoleSide> SIDE = EnumProperty.create("side", ConsoleSide.class);

    /** The whole console's outline from each block, per facing, indexed as {@link #parts}. */
    private static final Map<Direction, VoxelShape[]> OUTLINES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] shapes = new VoxelShape[4];
            for (int i = 0; i < 4; i++) {
                shapes[i] = outline(facing, i);
            }
            OUTLINES.put(facing, shapes);
        }
    }

    /**
     * A console block. It can't be pushed, and its neighbours draw their faces next to it.
     *
     * @param properties its properties, which this changes as it says
     */
    protected ConsoleBlock(Properties properties) {
        super(properties.noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SIDE, ConsoleSide.LEFT));
    }

    /**
     * Adds {@link #FACING}, {@link #HALF} and {@link #SIDE}. A subclass that adds properties of its
     * own calls this first.
     *
     * @param builder the builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SIDE);
    }

    // ---- What the subclass supplies ----------------------------------------------------------

    /**
     * Makes the console's block entity, which only its origin has.
     *
     * @param pos the origin's position
     * @param state the origin's state
     * @return the block entity, or null for none
     */
    @Nullable
    protected abstract BlockEntity newConsoleEntity(BlockPos pos, BlockState state);

    /**
     * Uses the console with an empty hand, or with an item {@link #useItemOnConsole} passed on. By
     * default it does nothing.
     *
     * @param state the origin's state
     * @param level the level
     * @param origin the origin's position, whichever block was used
     * @param player who uses it
     * @param hit where they clicked, on the block they used
     * @return the result, as for {@link #useWithoutItem}
     */
    protected InteractionResult useConsole(
            BlockState state, Level level, BlockPos origin, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * Uses an item on the console. By default it passes the use on to {@link #useConsole}.
     *
     * @param stack the item
     * @param state the origin's state
     * @param level the level
     * @param origin the origin's position, whichever block was used
     * @param player who uses it
     * @param hand the hand the item is in
     * @param hit where they clicked, on the block they used
     * @return the result, as for {@link #useItemOn}
     */
    protected ItemInteractionResult useItemOnConsole(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos origin,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Called once a player has placed the console from its item and all four blocks are in, on the
     * client and the server as {@link #setPlacedBy} is; the origin's block entity has taken the
     * item's components. A console that records its owner does it here. By default it does nothing.
     *
     * @param level the level
     * @param origin the origin's position
     * @param state the origin's state
     * @param placer who placed it, if anyone
     * @param stack the item it was placed from
     */
    protected void consolePlaced(
            Level level, BlockPos origin, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {}

    /**
     * Keeps a block's own properties in step with a block beside, above or below it in the same
     * console when that one changes, as each of the console's screens shows the same thing. By
     * default it changes nothing.
     *
     * @param state this block's state
     * @param partner the changed block's state, a block of the same console
     * @return this block's state, changed to match
     */
    protected BlockState matchPartner(BlockState state, BlockState partner) {
        return state;
    }

    // ---- Its four blocks ---------------------------------------------------------------------

    /**
     * The way from a console's left column to its right.
     *
     * @param facing the way the console faces
     * @return the way, its facing turned counterclockwise as seen from above
     */
    public static Direction toRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    /**
     * The origin's position, from any block of a console.
     *
     * @param state the block's state, a console's
     * @param pos the block's position
     * @return the lower left block's position
     */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(SIDE) == ConsoleSide.RIGHT
                ? p.relative(toRight(state.getValue(FACING)).getOpposite())
                : p;
    }

    /**
     * The four blocks of a console.
     *
     * @param origin the origin's position
     * @param facing the way the console faces
     * @return lower left, lower right, upper left and upper right
     */
    public static List<BlockPos> parts(BlockPos origin, Direction facing) {
        BlockPos right = origin.relative(toRight(facing));
        return List.of(origin, right, origin.above(), right.above());
    }

    /**
     * The state of one block of a console, from any block's.
     *
     * @param state a state of the console's block
     * @param i which block, as in {@link #parts}: 0 to 3
     * @return its state, with every other property as in {@code state}
     */
    public static BlockState part(BlockState state, int i) {
        return state.setValue(HALF, i >= 2 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(SIDE, (i & 1) == 1 ? ConsoleSide.RIGHT : ConsoleSide.LEFT);
    }

    /**
     * Which block of a console a state is, as in {@link #parts}.
     *
     * @param state the state, a console's
     * @return 0 to 3; 0 is the origin
     */
    public static int index(BlockState state) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0)
                + (state.getValue(SIDE) == ConsoleSide.RIGHT ? 1 : 0);
    }

    /**
     * Whether a state is a console's origin.
     *
     * @param state the state, a console's
     * @return true if it is the lower left block
     */
    public static boolean isOrigin(BlockState state) {
        return index(state) == 0;
    }

    /**
     * The origin of the console at a position, if there is one.
     *
     * @param level the level
     * @param pos the position
     * @return the origin's position if the block there is a console's, or else {@code pos}
     */
    public static BlockPos originAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ConsoleBlock ? origin(state, pos) : pos;
    }

    /**
     * The block entity of the console at a position, from any of its blocks: for a capability
     * provider, or to find a computer from the block a player looks at.
     *
     * @param level the level
     * @param pos the position
     * @return the origin's block entity if the block there is a console's, the block entity at
     *     {@code pos} if it isn't, or null if there is none or the origin isn't loaded
     */
    @Nullable
    public static BlockEntity entityAt(BlockGetter level, BlockPos pos) {
        BlockPos origin = originAt(level, pos);
        if (level instanceof Level l && !l.isLoaded(origin)) {
            return null;
        }
        return level.getBlockEntity(origin);
    }

    /**
     * Whether the console whose origin is at a position has all four blocks.
     *
     * @param level the level
     * @param origin the origin's position
     * @return true if a console's origin is there with its other three blocks in place
     */
    public static boolean isWhole(BlockGetter level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof ConsoleBlock) || !isOrigin(state)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        List<BlockPos> parts = parts(origin, facing);
        for (int i = 1; i < parts.size(); i++) {
            BlockState there = level.getBlockState(parts.get(i));
            if (!there.is(state.getBlock()) || there.getValue(FACING) != facing || index(there) != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a console has room at a position: the three spaces beside and above its origin are in
     * the world and can be replaced, as air or grass can.
     *
     * @param level the level
     * @param origin the origin's position
     * @param facing the way the console would face
     * @return true if it has
     */
    public static boolean hasRoom(LevelReader level, BlockPos origin, Direction facing) {
        if (origin.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        List<BlockPos> parts = parts(origin, facing);
        for (int i = 1; i < parts.size(); i++) {
            BlockPos p = parts.get(i);
            if (!level.getWorldBorder().isWithinBounds(p)
                    || !level.getBlockState(p).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Places a whole console, replacing what is in its four spaces without dropping it: to build a
     * console from something else, such as a computer frame. The old blocks are removed as for
     * {@link Level#setBlock}, so a block entity there that drops its contents on removal still
     * does. Neighbours are told once all four are in. Server thread.
     *
     * @param level the level
     * @param origin the origin's position
     * @param state the console's state, with its facing; its {@code half} and {@code side} are set
     *     for each block
     * @throws IllegalArgumentException if the state isn't a console's
     */
    public static void placeConsole(Level level, BlockPos origin, BlockState state) {
        if (!(state.getBlock() instanceof ConsoleBlock)) {
            throw new IllegalArgumentException(state + " isn't a console's");
        }
        List<BlockPos> parts = parts(origin, state.getValue(FACING));
        for (int i = 0; i < parts.size(); i++) {
            level.setBlock(parts.get(i), part(state, i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (int i = 0; i < parts.size(); i++) {
            BlockPos p = parts.get(i);
            level.blockUpdated(p, state.getBlock());
            part(state, i).updateNeighbourShapes(level, p, Block.UPDATE_ALL);
        }
    }

    /**
     * Adds a console's missing blocks, if their spaces are all free: for a block saved before its
     * block became a console, which loads as a lone origin. Server thread.
     *
     * @param level the level
     * @param origin the origin's position
     * @return true if the console is whole afterwards
     */
    public static boolean completeConsole(Level level, BlockPos origin) {
        if (isWhole(level, origin)) {
            return true;
        }
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof ConsoleBlock) || !isOrigin(state)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        List<BlockPos> parts = parts(origin, facing);
        if (origin.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        for (int i = 1; i < parts.size(); i++) {
            BlockState there = level.getBlockState(parts.get(i));
            boolean ours = there.is(state.getBlock()) && there.getValue(FACING) == facing && index(there) == i;
            if (!ours && (!level.getWorldBorder().isWithinBounds(parts.get(i)) || !there.canBeReplaced())) {
                return false;
            }
        }
        for (int i = 1; i < parts.size(); i++) {
            level.setBlock(parts.get(i), part(state, i), Block.UPDATE_ALL);
        }
        return isWhole(level, origin);
    }

    /** The box of all four blocks, in the coordinates of block {@code i}. */
    private static VoxelShape outline(Direction facing, int i) {
        Direction right = toRight(facing);
        int ox = (i & 1) == 1 ? -right.getStepX() : 0;
        int oz = (i & 1) == 1 ? -right.getStepZ() : 0;
        int oy = i >= 2 ? -1 : 0;
        return Shapes.box(
                Math.min(ox, ox + right.getStepX()),
                oy,
                Math.min(oz, oz + right.getStepZ()),
                Math.max(ox, ox + right.getStepX()) + 1,
                oy + 2,
                Math.max(oz, oz + right.getStepZ()) + 1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(FACING))[index(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    // ---- Placing and removing, as vanilla's two-block blocks ---------------------------------

    /**
     * The origin's state, facing the player, if the other three spaces are free and in the world.
     * A subclass may set its own properties on the state this returns.
     *
     * @param context the placement
     * @return the state, or null if the console doesn't fit
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        if (origin.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        List<BlockPos> parts = parts(origin, facing);
        for (int i = 1; i < parts.size(); i++) {
            BlockPos p = parts.get(i);
            if (!level.getWorldBorder().isWithinBounds(p)
                    || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /**
     * Fills in the other three blocks, then calls {@link #consolePlaced}.
     *
     * @param level the level
     * @param pos the origin's position
     * @param state the origin's state
     * @param placer who placed it, if anyone
     * @param stack the item it was placed from
     */
    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        List<BlockPos> parts = parts(pos, state.getValue(FACING));
        for (int i = 1; i < parts.size(); i++) {
            level.setBlock(parts.get(i), part(state, i), Block.UPDATE_ALL);
        }
        consolePlaced(level, pos, state, placer, stack);
    }

    /**
     * Breaks the block when a block beside, above or below it in the console goes, so breaking any
     * block breaks them all, and otherwise keeps it in step with that block through {@link
     * #matchPartner}. A block a {@link BlockMovers mover} is carrying is left alone.
     *
     * @param state this block's state
     * @param direction the way to the changed block
     * @param neighbor the changed block's state
     * @param level the level
     * @param pos this block's position
     * @param neighborPos the changed block's position
     * @return this block's new state, air if it breaks
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        DoubleBlockHalf half = state.getValue(HALF);
        ConsoleSide side = state.getValue(SIDE);
        Direction vertical = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        Direction across =
                side == ConsoleSide.LEFT ? toRight(facing) : toRight(facing).getOpposite();
        if (direction != vertical && direction != across) {
            return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }
        boolean partner = neighbor.is(this)
                && neighbor.getValue(FACING) == facing
                && (direction == vertical
                        ? neighbor.getValue(HALF) != half && neighbor.getValue(SIDE) == side
                        : neighbor.getValue(SIDE) != side && neighbor.getValue(HALF) == half);
        if (partner) {
            return matchPartner(state, neighbor);
        }
        if (level instanceof ServerLevel server && BlockMovers.isMoving(server, pos)) {
            return state;
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * Breaking a block other than the origin in creative, or without the right tool, removes the
     * origin with no drop, as for a door. Otherwise the origin drops the item when it goes.
     *
     * @param level the level
     * @param pos the broken block's position
     * @param state the broken block's state
     * @param player who breaks it
     * @return the state broken, as vanilla's
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))) {
            BlockPos origin = origin(state, pos);
            BlockState there = level.getBlockState(origin);
            if (!origin.equals(pos) && there.is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, origin, Block.getId(there));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!state.is(oldState.getBlock())) {
            capabilitiesChanged(state, level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) {
            capabilitiesChanged(state, level, pos);
        }
    }

    /**
     * Tells the game that the console's capabilities came or went at each of its four blocks: only
     * the origin has a block entity, which is all the game watches by itself.
     */
    private static void capabilitiesChanged(BlockState state, Level level, BlockPos pos) {
        for (BlockPos part : parts(origin(state, pos), state.getValue(FACING))) {
            level.invalidateCapabilities(part);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE
                ? state
                : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(SIDE);
    }

    /**
     * The origin's block entity, from {@link #newConsoleEntity}; the other blocks have none.
     *
     * @param pos the block's position
     * @param state the block's state
     * @return the block entity, or null
     */
    @Nullable
    @Override
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isOrigin(state) ? newConsoleEntity(pos, state) : null;
    }

    // ---- Using it ------------------------------------------------------------------------

    /**
     * Passes the use to {@link #useConsole} with the origin's position and state.
     *
     * @param state the used block's state
     * @param level the level
     * @param pos the used block's position
     * @param player who uses it
     * @param hit where they clicked
     * @return {@link #useConsole}'s result, or a pass if the origin is gone
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos origin = origin(state, pos);
        BlockState there = level.getBlockState(origin);
        if (!there.is(this)) {
            return InteractionResult.PASS;
        }
        return useConsole(there, level, origin, player, hit);
    }

    /**
     * Passes the use to {@link #useItemOnConsole} with the origin's position and state.
     *
     * @param stack the item
     * @param state the used block's state
     * @param level the level
     * @param pos the used block's position
     * @param player who uses it
     * @param hand the hand the item is in
     * @param hit where they clicked
     * @return {@link #useItemOnConsole}'s result, or a pass if the origin is gone
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        BlockPos origin = origin(state, pos);
        BlockState there = level.getBlockState(origin);
        if (!there.is(this)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return useItemOnConsole(stack, there, level, origin, player, hand, hit);
    }
}
