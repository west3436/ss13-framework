package io.github.west3436.ss13.claim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Whether a player may build somewhere: break blocks there, or place them. A ship mover asks it
 * before lifting a ship out of its box and setting it down, and a tool that builds or takes apart
 * from a distance, such as an RCD, before it acts.
 *
 * <p>At each position it asks:
 *
 * <ul>
 *   <li>the server's own protection ({@link Protection#VANILLA}: {@link ServerLevel#mayInteract},
 *       which is spawn protection on a dedicated server, and the world border); and
 *   <li>other mods: it posts NeoForge's {@link BlockEvent.BreakEvent} for the player, or {@link
 *       BlockEvent.EntityPlaceEvent}, which claim mods cancel where the player may not build. No
 *       claim mod is needed or named.
 * </ul>
 *
 * <p>The events are asked, not acted on: nothing is broken or placed, though a mod that listens may
 * tell the player it refused.
 *
 * <p>For a box, it looks at a few positions of the part of the box in each chunk it touches: its
 * centre, a corner in each of the chunk's four columns at alternating heights, and the position
 * nearest the level's spawn. A claim smaller than a chunk, between the positions looked at, can be
 * missed.
 *
 * <p>An operator ({@link net.minecraft.server.players.PlayerList#isOp}) is never refused, as they
 * may build anywhere; whether they are one is asked each time the check is. Something with no
 * player, such as a command from the console, has no claim check at all.
 *
 * <p>Thread: server thread only.
 */
public final class ClaimCheck {
    /** What the player would do at a position. */
    public enum Action {
        /** Take blocks away, asked with {@link BlockEvent.BreakEvent}. */
        BREAK,
        /** Set blocks down, asked with {@link BlockEvent.EntityPlaceEvent}. */
        PLACE
    }

    /** Whether a player may build at a position, by the server's own rules. */
    @FunctionalInterface
    public interface Protection {
        /** The server's own: {@link ServerLevel#mayInteract}, spawn protection and the world border. */
        Protection VANILLA = (level, pos, player) -> level.mayInteract(player, pos);

        /**
         * Asks whether a player may build at a position.
         *
         * @param level the level
         * @param pos the position
         * @param player the player
         * @return true if they may
         */
        boolean mayBuild(ServerLevel level, BlockPos pos, ServerPlayer player);
    }

    private final ServerPlayer player;
    private final Protection protection;

    /**
     * A check for a player, by the server's own protection and other mods' events.
     *
     * @param player who would build
     */
    public ClaimCheck(ServerPlayer player) {
        this(player, Protection.VANILLA);
    }

    /**
     * A check for a player, with the server's protection replaced, as GameTests do, since their
     * server protects no spawn. Other mods' events are still asked.
     *
     * @param player who would build
     * @param protection the server's protection
     */
    public ClaimCheck(ServerPlayer player, Protection protection) {
        this.player = Objects.requireNonNull(player, "player");
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    /**
     * Who would build.
     *
     * @return the player
     */
    public ServerPlayer player() {
        return player;
    }

    /**
     * Whether the player is an operator, whom the check never refuses. Asked now.
     *
     * @return true if they are one
     */
    public boolean bypasses() {
        return player.server.getPlayerList().isOp(player.getGameProfile());
    }

    /**
     * Whether the player may do something at one position.
     *
     * @param level the level
     * @param pos the position
     * @param action what they would do
     * @return true if they may
     */
    public boolean mayBuild(ServerLevel level, BlockPos pos, Action action) {
        return bypasses() || asks(level, pos, action);
    }

    /**
     * Whether the player may do something across a box, at the positions looked at in each chunk.
     * Stops at the first refusal.
     *
     * @param level the level
     * @param box the box
     * @param action what they would do
     * @return true if they may
     */
    public boolean mayBuild(ServerLevel level, BoundingBox box, Action action) {
        return bypasses() || scan(level, box, action, false).isEmpty();
    }

    /**
     * Each chunk a box touches where the player may not do something, for a report or a dry run.
     *
     * @param level the level
     * @param box the box
     * @param action what they would do
     * @return the chunks, in order of x then z; empty if they may everywhere
     */
    public List<ChunkPos> refusedChunks(ServerLevel level, BoundingBox box, Action action) {
        return bypasses() ? List.of() : scan(level, box, action, true);
    }

    private List<ChunkPos> scan(ServerLevel level, BoundingBox box, Action action, boolean all) {
        List<ChunkPos> refused = new ArrayList<>();
        BlockPos spawn = level.getSharedSpawnPos();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                for (BlockPos pos : samples(box, cx, cz, spawn)) {
                    if (!asks(level, pos, action)) {
                        refused.add(new ChunkPos(cx, cz));
                        if (!all) {
                            return refused;
                        }
                        break;
                    }
                }
            }
        }
        return refused;
    }

    /**
     * The positions looked at in the part of a box in one chunk.
     *
     * @param box the box
     * @param cx the chunk's x
     * @param cz the chunk's z
     * @param spawn the level's spawn
     * @return the positions, each once
     */
    static Set<BlockPos> samples(BoundingBox box, int cx, int cz, BlockPos spawn) {
        int x0 = Math.max(box.minX(), cx << 4);
        int x1 = Math.min(box.maxX(), (cx << 4) + 15);
        int z0 = Math.max(box.minZ(), cz << 4);
        int z1 = Math.min(box.maxZ(), (cz << 4) + 15);
        int y0 = box.minY();
        int y1 = box.maxY();
        int midY = Math.floorDiv(y0 + y1, 2);
        Set<BlockPos> samples = new LinkedHashSet<>();
        samples.add(new BlockPos(Math.floorDiv(x0 + x1, 2), midY, Math.floorDiv(z0 + z1, 2)));
        samples.add(new BlockPos(x0, y0, z0));
        samples.add(new BlockPos(x1, y0, z1));
        samples.add(new BlockPos(x0, y1, z1));
        samples.add(new BlockPos(x1, y1, z0));
        samples.add(new BlockPos(Mth.clamp(spawn.getX(), x0, x1), midY, Mth.clamp(spawn.getZ(), z0, z1)));
        return samples;
    }

    /** Asks the server and the other mods, as if the player weren't an operator. */
    private boolean asks(ServerLevel level, BlockPos pos, Action action) {
        if (!protection.mayBuild(level, pos, player)) {
            return false;
        }
        if (action == Action.PLACE) {
            // Placed against what is below it, as a block set down on the ground would be.
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            return !NeoForge.EVENT_BUS
                    .post(new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player))
                    .isCanceled();
        }
        return !NeoForge.EVENT_BUS
                .post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player))
                .isCanceled();
    }
}
