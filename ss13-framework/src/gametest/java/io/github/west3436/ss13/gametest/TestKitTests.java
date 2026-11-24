package io.github.west3436.ss13.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.testkit.FaultLog;
import io.github.west3436.ss13.testkit.ListeningPlayer;
import io.github.west3436.ss13.testkit.SoundLog;
import io.github.west3436.ss13.testkit.TestPlayers;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The test kit does what the mods' GameTests rely on, on the server they run on. The kit is in this
 * source set as a mod adds it: a jar of its own, loaded as a game library.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TestKitTests {
    private static final Logger LOGGER = LogManager.getLogger("ss13_framework_testkit_probe");

    private TestKitTests() {}

    /** The hurtable player takes damage that a plain fake player shrugs off. */
    @GameTest(template = "empty")
    public static void test_kit_hurtable_player_takes_damage(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer plain = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ss13_plain"));
        FakePlayer hurtable = TestPlayers.hurtable(level, "ss13_hurtable");
        plain.hurt(level.damageSources().generic(), 2);
        hurtable.hurt(level.damageSources().generic(), 2);
        h.assertValueEqual(plain.getHealth(), plain.getMaxHealth(), "the plain fake player's health");
        h.assertValueEqual(hurtable.getHealth(), hurtable.getMaxHealth() - 2, "the hurtable player's health");
        h.succeed();
    }

    /** A payload's context answers with the player that sent it. */
    @GameTest(template = "empty")
    public static void test_kit_payload_context_names_its_sender(GameTestHelper h) {
        FakePlayer sender = TestPlayers.hurtable(h.getLevel(), "ss13_sender");
        h.assertTrue(TestPlayers.sentBy(sender).player() == sender, "the context's player isn't its sender");
        h.succeed();
    }

    /** The fault log has the errors logged while it's open, and none after it closes. */
    @GameTest(template = "empty")
    public static void test_kit_fault_log_collects_errors_while_open(GameTestHelper h) {
        String fault = "a test fault " + UUID.randomUUID() + " (expected: the test kit's FaultLog test)";
        FaultLog log = new FaultLog();
        try (log) {
            LOGGER.warn("{}, only a warning", fault);
            LOGGER.error(fault);
        }
        LOGGER.error("{}, after the log closed", fault);
        h.assertValueEqual(log.naming(fault), 1, "errors naming the fault, in " + log.all());
        h.assertValueEqual(
                log.by("ss13_framework_testkit_probe", fault).size(), 1, "the probe logger's errors naming it");
        h.assertValueEqual(log.by("another_logger", fault).size(), 0, "another logger's errors naming it");
        h.succeed();
    }

    /** The sound log has the sounds played at a block, once. */
    @GameTest(template = "empty")
    public static void test_kit_sound_log_takes_the_sounds_at_a_block(GameTestHelper h) {
        SoundLog.listen();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        Vec3 at = Vec3.atCenterOf(pos);
        h.getLevel().playSound(null, pos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.BLOCKS, 1, 1);
        ResourceLocation harp = SoundEvents.NOTE_BLOCK_HARP.value().getLocation();
        List<SoundLog.Played> sounds = SoundLog.take(at);
        h.assertValueEqual(sounds.size(), 1, "sounds at the block");
        h.assertTrue(sounds.getFirst().is(harp), "the sound at the block is " + sounds.getFirst());
        h.assertValueEqual(SoundLog.take(at).size(), 0, "sounds at the block once they were taken");
        h.succeed();
    }

    /** A listener in the level gets what the level sends its players while a call runs. */
    @GameTest(template = "empty")
    public static void test_kit_listener_gets_what_the_level_sends(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        ListeningPlayer listener = ListeningPlayer.at(level, at);
        ListeningPlayer.during(
                level,
                List.of(listener),
                () -> level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0, 0, 0, 0));
        h.assertValueEqual(
                listener.packets(ClientboundLevelParticlesPacket.class).size(), 1, "particle packets it got");
        h.assertFalse(level.players().contains(listener.player()), "the listener stayed in the level");
        h.succeed();
    }

    /** A client on the server's lists hears a sound the server plays beside it. */
    @GameTest(template = "empty")
    public static void test_kit_client_hears_a_sound(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        ListeningPlayer client = ListeningPlayer.client(level, Vec3.atCenterOf(pos), false);
        client.join();
        try {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1, 1);
        } finally {
            client.leave();
        }
        h.assertTrue(
                client.sounds().stream()
                        .anyMatch(s -> s.getSound().value().equals(SoundEvents.NOTE_BLOCK_BELL.value())),
                "the client heard no bell");
        h.assertFalse(
                level.getServer().getPlayerList().getPlayers().contains(client.player()),
                "the client stayed on the server's list");
        h.succeed();
    }
}
