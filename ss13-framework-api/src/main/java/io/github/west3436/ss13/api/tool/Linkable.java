package io.github.west3436.ss13.api.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.label.Labels;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * A block that takes part in multitool links, offered as the block capability {@link #BLOCK} by
 * the mod that owns the block. A block plays one role or both:
 *
 * <ul>
 *   <li>A <b>source</b>, such as a controller, keeps links to other blocks. The multitool used on
 *       it stores it in its buffer ({@link #source}), and validates and makes each link through it
 *       ({@link #accepts}, {@link #link}). The source keeps its links, by position.
 *   <li>A <b>target</b>, such as a vent or a door, is what a source links. Offering the capability
 *       is what makes a block one; {@link #target} says where its link is kept, such as the origin
 *       of a block of several parts.
 * </ul>
 *
 * <p>What the framework's multitool does on a block, before the block's own use but only where it
 * has something to do, so anywhere else the block gets the click as from an empty hand:
 *
 * <ol>
 *   <li>On a source, it stores it and says {@link #selected}. On the source already stored, it
 *       leaves the click to the block, so its window opens; a sneak-click stores it again.
 *   <li>On a target, with a source stored in its buffer that {@link #accepts} the target, it asks
 *       the source to {@link #link} it, or unlink it if it is linked, and says what the source
 *       answers.
 *   <li>Otherwise it leaves the click to the block. After the block's own use, or on a sneak-click,
 *       which skips it, it says why: nothing stored, the stored source gone, or the source's
 *       {@link #refusal}.
 * </ol>
 *
 * <p>Sneak-using the multitool on nothing clears its buffer. The buffer is the source's position
 * and dimension; a source in another dimension is out of reach.
 *
 * <p><b>Moves.</b> A mover that moves blocks with their saved data, such as a ship, calls {@link
 * #rebaseAll} once the blocks are in place, so each moved source's links follow ({@link
 * #rebase}).
 */
public interface Linkable {
    /** {@code ss13_framework:linkable}: the capability, with no context. */
    BlockCapability<Linkable, @Nullable Void> BLOCK = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "linkable"), Linkable.class);

    /**
     * As a source: whether the multitool stores this block in its buffer from this use, and as
     * which position. Decided from what the client knows too, so both sides agree whether the click
     * is taken. Not a source by default.
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param use the multitool's use on the block, which says whether the player is sneaking
     * @return the position to store, usually the block's own; empty if it isn't a source from this
     *     use
     */
    default Optional<BlockPos> source(UseOnContext use) {
        return Optional.empty();
    }

    /**
     * As a source: what the multitool says once it has stored this source. By default {@code
     * item.ss13_framework.multitool.selected} with the block's name and what {@link Labels#named}
     * calls it: its label, or its position.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param source the position stored
     * @return the message
     */
    default Component selected(ServerLevel level, BlockPos source) {
        return Component.translatable(
                "item.ss13_framework.multitool.selected",
                level.getBlockState(source).getBlock().getName(),
                Labels.named(level, source));
    }

    /**
     * As a source: whether it takes a link to a target, decided from what the client knows too, such
     * as the target's block or block entity type, so both sides agree whether the click is taken.
     * Takes none by default.
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param level the level, which holds both blocks
     * @param source this source's stored position
     * @param target where the target's link is kept, from its {@link #target}
     * @return true if {@link #link} may be asked
     */
    default boolean accepts(Level level, BlockPos source, BlockPos target) {
        return false;
    }

    /**
     * As a source: why it takes no link to a target, which the multitool says after the block's
     * own use, or on a sneak-click. By default {@code item.ss13_framework.multitool.not_linkable}
     * with both blocks' names.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param source this source's stored position
     * @param target where the target's link is kept
     * @return the message
     */
    default Component refusal(ServerLevel level, BlockPos source, BlockPos target) {
        return Component.translatable(
                "item.ss13_framework.multitool.not_linkable",
                level.getBlockState(target).getBlock().getName(),
                level.getBlockState(source).getBlock().getName());
    }

    /**
     * As a source: links a target it {@link #accepts}, or unlinks it if it is linked. By default it
     * links nothing and answers its {@link #refusal}.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param source this source's stored position
     * @param target where the target's link is kept
     * @param player who links them, or null
     * @return what the multitool says, such as "Linked" or "Unlinked"
     */
    default Component link(ServerLevel level, BlockPos source, BlockPos target, @Nullable Player player) {
        return refusal(level, source, target);
    }

    /**
     * As a target: where this block's link is kept, such as the origin of a block of several
     * parts. The block clicked by default.
     *
     * <p>Thread: the level's own thread, client or server.
     *
     * @param use the multitool's use on the block
     * @return the position, or empty if this block isn't a target from this use, so the click goes
     *     to the block
     */
    default Optional<BlockPos> target(UseOnContext use) {
        return Optional.of(use.getClickedPos());
    }

    /**
     * After a move: brings the links this block keeps forward. Each position it links is given to
     * {@code rebase}, which answers where that block is now, or null if the link should be dropped,
     * such as one to a block left behind in another dimension. Does nothing by default.
     *
     * <p>Thread: server thread.
     *
     * @param level the level the block is in now
     * @param pos where the block is now
     * @param rebase where a linked position is now, or null to drop the link
     */
    default void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {}

    /**
     * After a move: asks every block entity in a box whose block is {@code Linkable} to {@link
     * #rebase} its links. Only loaded chunks are looked at, which a mover's destination is.
     *
     * <p>Thread: server thread.
     *
     * @param level the level the blocks are in now
     * @param box where they are now, both corners inside
     * @param rebase where a position linked before the move is now, or null to drop the link
     * @return how many blocks were asked
     */
    static int rebaseAll(ServerLevel level, BoundingBox box, UnaryOperator<@Nullable BlockPos> rebase) {
        List<BlockPos> found = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (box.isInside(be.getBlockPos())) {
                        found.add(be.getBlockPos());
                    }
                }
            }
        }
        int asked = 0;
        for (BlockPos pos : found) {
            Linkable linkable = level.getCapability(BLOCK, pos);
            if (linkable != null) {
                linkable.rebase(level, pos, rebase);
                asked++;
            }
        }
        return asked;
    }
}
