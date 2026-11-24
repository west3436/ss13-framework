package io.github.west3436.ss13.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.claim.ClaimCheck;
import io.github.west3436.ss13.claim.ClaimCheck.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Claimed and protected land: the claim check refuses a player where the server's protection or a
 * claim mod keeps them out, and never refuses an operator.
 *
 * <p>The GameTest server protects no spawn, so the first test stands in the server's protection
 * with its own. The second stands in a claim mod, which cancels NeoForge's block events.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClaimTests {
    /** The player who isn't an operator, until the test makes them one. */
    private static final GameProfile PILOT =
            new GameProfile(UUID.fromString("4b0d6a1e-2f53-4c11-8d4e-5c5e13f0c901"), "ss13_claim_pilot");

    /** The player the stand-in claim mod keeps out. */
    private static final GameProfile TRESPASSER =
            new GameProfile(UUID.fromString("4b0d6a1e-2f53-4c11-8d4e-5c5e13f0c902"), "ss13_claim_trespasser");

    /** The stand-in claim mod's one claim, if it keeps one now; read on the server thread. */
    private static @Nullable Claim claim;

    private static boolean listening;

    private ClaimTests() {}

    /**
     * Where the server protects a position, a player who isn't an operator may neither break nor
     * place there, and a box that holds it is refused in that position's chunk; an operator may.
     */
    @GameTest(template = "empty")
    public static void the_servers_protection_refuses_a_player_who_is_not_an_operator(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BoundingBox box = box(h);
        // The box's lowest corner is looked at in its chunk, whatever chunks the box spans.
        BlockPos guarded = new BlockPos(box.minX(), box.minY(), box.minZ());
        ServerPlayer pilot = FakePlayerFactory.get(level, PILOT);
        PlayerList players = level.getServer().getPlayerList();
        h.assertFalse(players.isOp(PILOT), "the pilot is an operator already");
        ClaimCheck check = new ClaimCheck(pilot, (l, pos, player) -> !pos.equals(guarded));

        h.assertFalse(check.bypasses(), "the pilot bypasses the check");
        for (Action action : Action.values()) {
            h.assertFalse(check.mayBuild(level, guarded, action), "the pilot may " + action + " where guarded");
            h.assertTrue(check.mayBuild(level, guarded.above(), action), "the pilot may not " + action + " beside it");
            h.assertFalse(check.mayBuild(level, box, action), "the pilot may " + action + " across the box");
            h.assertValueEqual(
                    check.refusedChunks(level, box, action), List.of(new ChunkPos(guarded)), action + " refused in");
        }

        players.op(PILOT);
        try {
            h.assertTrue(check.bypasses(), "an operator doesn't bypass the check");
            for (Action action : Action.values()) {
                h.assertTrue(check.mayBuild(level, guarded, action), "an operator may not " + action);
                h.assertTrue(check.mayBuild(level, box, action), "an operator may not " + action + " across the box");
                h.assertValueEqual(check.refusedChunks(level, box, action), List.of(), "an operator refused in");
            }
        } finally {
            players.deop(PILOT);
        }
        h.succeed();
    }

    /**
     * A claim mod that cancels the block events keeps a player out of its claim: a claim on
     * breaking refuses breaking and not placing, and the other way round, and every chunk of the
     * box is named.
     */
    @GameTest(template = "empty")
    public static void a_claim_kept_by_another_mod_refuses_what_it_cancels(GameTestHelper h) {
        listen();
        ServerLevel level = h.getLevel();
        BoundingBox box = box(h);
        ServerPlayer trespasser = FakePlayerFactory.get(level, TRESPASSER);
        // The server itself protects nothing here.
        ClaimCheck check = new ClaimCheck(trespasser, (l, pos, player) -> true);
        h.assertTrue(check.mayBuild(level, box, Action.BREAK), "refused with no claim");
        h.assertTrue(check.mayBuild(level, box, Action.PLACE), "refused with no claim");
        try {
            for (Action claimed : Action.values()) {
                claim = new Claim(level.dimension(), box, claimed);
                Action other = claimed == Action.BREAK ? Action.PLACE : Action.BREAK;
                h.assertFalse(check.mayBuild(level, box, claimed), "the trespasser may " + claimed + " in the claim");
                h.assertTrue(check.mayBuild(level, box, other), "the trespasser may not " + other + " in the claim");
                h.assertValueEqual(check.refusedChunks(level, box, claimed), chunks(box), claimed + " refused in");
                h.assertValueEqual(check.refusedChunks(level, box, other), List.of(), other + " refused in");
            }
            ServerPlayer pilot = FakePlayerFactory.get(level, PILOT);
            h.assertTrue(
                    new ClaimCheck(pilot, (l, pos, player) -> true).mayBuild(level, box, Action.PLACE),
                    "the claim keeps out a player it doesn't name");
        } finally {
            claim = null;
        }
        h.succeed();
    }

    /** A box of 20 by 3 by 20 at the test's own spot, which always spans more than one chunk. */
    private static BoundingBox box(GameTestHelper h) {
        BlockPos corner = h.absolutePos(new BlockPos(0, 1, 0));
        return BoundingBox.fromCorners(corner, corner.offset(19, 2, 19));
    }

    /** Every chunk a box touches, in order of x then z. */
    private static List<ChunkPos> chunks(BoundingBox box) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    /** One claim of the stand-in claim mod: on breaking, or on placing, inside a box. */
    private record Claim(net.minecraft.resources.ResourceKey<Level> level, BoundingBox box, Action action) {
        boolean keepsOut(Level in, BlockPos pos, Action asked) {
            return in.dimension() == level && asked == action && box.isInside(pos);
        }
    }

    /** Listens, once, as a claim mod would: cancels the trespasser's events inside the claim. */
    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            Claim now = claim;
            if (now != null
                    && event.getPlayer().getUUID().equals(TRESPASSER.getId())
                    && now.keepsOut((Level) event.getLevel(), event.getPos(), Action.BREAK)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            Claim now = claim;
            if (now != null
                    && event.getEntity() != null
                    && event.getEntity().getUUID().equals(TRESPASSER.getId())
                    && now.keepsOut((Level) event.getLevel(), event.getPos(), Action.PLACE)) {
                event.setCanceled(true);
            }
        });
    }
}
