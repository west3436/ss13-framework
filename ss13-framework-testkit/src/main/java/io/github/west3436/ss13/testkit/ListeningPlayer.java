package io.github.west3436.ss13.testkit;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * A player at a point that keeps every packet the server sends it, so a test can check what a client
 * would be told. {@link #during} puts listeners in the level's player list only while a call runs,
 * within one game tick, so no other test or system sees them. That reaches what the level sends to
 * its own players, such as particles and explosions.
 *
 * <p>Some things send nothing to a {@link FakePlayer}, and the server's player list sends others,
 * such as sounds, so a test of those uses a {@link #client}, which stays on the level's and the
 * server's lists from {@link #join} to {@link #leave} and has every channel open. Nothing ticks it
 * but the test, with {@link #tick}. Call {@link #leave} in a {@code finally}, so a failing test
 * doesn't leave it on the lists.
 */
public final class ListeningPlayer {
    private final List<Packet<?>> sent = new ArrayList<>();
    private final ServerPlayer player;

    private ListeningPlayer(ServerLevel level, ServerPlayer player, Vec3 at) {
        this.player = player;
        new Recording(level, player, sent);
        player.moveTo(at);
    }

    /**
     * A listener at a point, not yet in the level.
     *
     * @param level the level it listens in
     * @param at where it stands
     * @return the listener
     */
    public static ListeningPlayer at(ServerLevel level, Vec3 at) {
        FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ss13_listener"));
        return new ListeningPlayer(level, fake, at);
    }

    /**
     * A listener at a point that the game treats as a client, not yet on the server's lists. Nothing
     * can hurt it.
     *
     * @param level the level it listens in
     * @param at where it stands
     * @param operator whether it has permission level 4, for an operator's commands, or 0
     * @return the listener
     */
    public static ListeningPlayer client(ServerLevel level, Vec3 at, boolean operator) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "ss13_client");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return true;
            }

            @Override
            protected int getPermissionLevel() {
                return operator ? 4 : 0;
            }
        };
        return new ListeningPlayer(level, player, at);
    }

    /**
     * Runs a call with these listeners in the level's player list.
     *
     * @param level the level whose list they join
     * @param listeners the listeners
     * @param call what to run while they are in it
     */
    public static void during(ServerLevel level, List<ListeningPlayer> listeners, Runnable call) {
        List<ServerPlayer> players = new ArrayList<>();
        listeners.forEach(l -> players.add(l.player));
        level.players().addAll(players);
        try {
            call.run();
        } finally {
            level.players().removeAll(players);
        }
    }

    /**
     * The player that hears.
     *
     * @return the player
     */
    public ServerPlayer player() {
        return player;
    }

    /** Puts this listener on the level's and the server's player lists, until {@link #leave}. */
    public void join() {
        PlayerList list = player.server.getPlayerList();
        ListeningPlayer.<List<ServerPlayer>>field(list, "players").add(player);
        ListeningPlayer.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        player.serverLevel().players().add(player);
    }

    /** Takes this listener off the lists. Safe if it isn't on them. */
    public void leave() {
        PlayerList list = player.server.getPlayerList();
        ListeningPlayer.<List<ServerPlayer>>field(list, "players").remove(player);
        ListeningPlayer.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().players().remove(player);
    }

    /** Runs one game tick of the player, as its connection does for a real client. */
    public void tick() {
        player.doTick();
    }

    /** A collection of the player list's that nothing public adds to. */
    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't reach the player list's " + name, e);
        }
    }

    /**
     * The sounds it was sent.
     *
     * @return the sound packets, in order
     */
    public List<ClientboundSoundPacket> sounds() {
        return packets(ClientboundSoundPacket.class);
    }

    /**
     * The explosions it was sent. A client plays each one's own sound.
     *
     * @return the explosion packets, in order
     */
    public List<ClientboundExplodePacket> explosions() {
        return packets(ClientboundExplodePacket.class);
    }

    /**
     * The packets of one type it was sent.
     *
     * @param type the packet's class
     * @param <T> the packet's type
     * @return the packets, in order
     */
    public <T extends Packet<?>> List<T> packets(Class<T> type) {
        return sent.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * The custom payloads it was sent, which is how mods tell a client anything.
     *
     * @return the payloads, in order
     */
    public List<CustomPacketPayload> payloads() {
        return sent.stream()
                .filter(ClientboundCustomPayloadPacket.class::isInstance)
                .<CustomPacketPayload>map(p -> ((ClientboundCustomPayloadPacket) p).payload())
                .toList();
    }

    /**
     * The payloads of one type it was sent.
     *
     * @param type the payload's class
     * @param <T> the payload's type
     * @return the payloads, in order
     */
    public <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
        return payloads().stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * A connection that keeps what a server sends, and hands what it's given to the game's own
     * handlers. Making one sets it as the player's connection. A listener has one; a test that needs
     * another kind of player, such as one a mod's own factory makes, gives it one of its own.
     */
    public static final class Recording extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> sent;

        /**
         * Becomes a player's connection.
         *
         * @param level the level the player is in
         * @param player the player
         * @param sent where to keep every packet the server sends the player, in order
         */
        public Recording(ServerLevel level, ServerPlayer player, List<Packet<?>> sent) {
            super(
                    level.getServer(),
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sent = sent;
        }

        /**
         * Keeps a packet instead of sending it.
         *
         * @param packet the packet
         * @param listener ignored: nothing is sent
         */
        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
        }

        /**
         * It has no channel to negotiate, so it takes whatever a client with the mods would.
         *
         * @param payloadId the payload's channel
         * @return always true
         */
        @Override
        public boolean hasChannel(ResourceLocation payloadId) {
            return true;
        }
    }
}
