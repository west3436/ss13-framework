package io.github.west3436.ss13.preset;

import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetStore;
import io.github.west3436.ss13.api.preset.Presets;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link Presets} store on a server: a {@link WorldPresets} for the running world, loaded as the
 * server is about to start and forgotten once it has stopped, with the {@code /ss13 preset}
 * command. Once a second it applies a hand edit of the world's preset file.
 */
public final class PresetService implements PresetStore {
    /** A world's {@code serverconfig/}, where its own preset file goes. */
    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");

    /** How often the world's preset file is looked at for a hand edit, in ticks. */
    private static final int EDIT_CHECK_TICKS = 20;

    private static final PresetService INSTANCE = new PresetService();

    private static volatile @Nullable WorldPresets world;

    private PresetService() {}

    /**
     * Installs the store and listens on the game bus.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void register(IEventBus gameBus) {
        Presets.install(INSTANCE);
        gameBus.addListener(ServerAboutToStartEvent.class, e -> load(e.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, e -> unload());
        gameBus.addListener(ServerTickEvent.Post.class, e -> {
            WorldPresets w = world;
            if (w != null && e.getServer().getTickCount() % EDIT_CHECK_TICKS == 0) {
                w.pickUpEdits();
            }
        });
        // Lowest, so a mod's own command root, which an alias goes under, is already there.
        gameBus.addListener(
                EventPriority.LOWEST, RegisterCommandsEvent.class, e -> PresetCommand.register(e.getDispatcher()));
    }

    /**
     * The running world's presets.
     *
     * @return them, or empty while no server runs
     */
    public static Optional<WorldPresets> world() {
        return Optional.ofNullable(world);
    }

    /**
     * Reads the running world's presets from its files again, as a restart would. For GameTests.
     *
     * @param server the server
     * @return the presets read
     */
    public static WorldPresets reloadForTest(MinecraftServer server) {
        unload();
        load(server);
        return world().orElseThrow();
    }

    private static void load(MinecraftServer server) {
        WorldPresets w = new WorldPresets(
                FMLPaths.CONFIGDIR.get().resolve(Presets.FILE_NAME),
                server.getWorldPath(SERVERCONFIG).resolve(Presets.FILE_NAME),
                server.isDedicatedServer());
        w.load();
        world = w;
    }

    private static void unload() {
        WorldPresets w = world;
        world = null;
        if (w != null) {
            w.unload();
        }
    }

    private static PresetStore current() {
        WorldPresets w = world;
        return w == null ? PresetStore.NONE : w;
    }

    @Override
    public boolean isLoaded() {
        return current().isLoaded();
    }

    @Override
    public Preset preset() {
        return current().preset();
    }

    @Override
    public Optional<Object> override(ResourceLocation option) {
        return current().override(option);
    }

    @Override
    public void switchTo(Preset preset) {
        current().switchTo(preset);
    }

    @Override
    public void setOverride(ResourceLocation option, Object stored) {
        current().setOverride(option, stored);
    }

    @Override
    public void clearOverride(ResourceLocation option) {
        current().clearOverride(option);
    }
}
