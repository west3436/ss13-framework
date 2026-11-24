package io.github.west3436.ss13.testkit;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Players for GameTests.
 *
 * <p>NeoForge's {@link FakePlayer} can't be hurt. It is invulnerable to every damage source, and,
 * like every new {@link ServerPlayer}, it is spared most damage for its first 60 ticks, which its
 * tick never counts down because it does nothing. A test that checks a player takes harm, or takes
 * none, needs {@link #hurtable} instead.
 */
public final class TestPlayers {
    private TestPlayers() {}

    /**
     * A survival fake player in a level, not added to it, that damage reaches as it would reach a
     * real player: neither invulnerable nor spawn-protected. Its tick still does nothing, so a test
     * that hurts it more than once within 10 ticks sets {@code invulnerableTime} back to 0 itself,
     * as a real player's tick would count it down.
     *
     * @param level the level the player is in
     * @param name the player's name, which shows in messages and logs
     * @return the player
     * @throws IllegalStateException if the player's spawn protection can't be ended
     */
    public static FakePlayer hurtable(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        try {
            Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't end the test player's spawn protection", e);
        }
        return p;
    }

    /**
     * A payload's context on the server, from {@code player}: enough to call a client-to-server
     * payload's handler as the network would. It answers only {@link IPayloadContext#player()}; every
     * other method throws {@link UnsupportedOperationException}.
     *
     * @param player the player the payload comes from
     * @return the context
     */
    public static IPayloadContext sentBy(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) {
                        return player;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
