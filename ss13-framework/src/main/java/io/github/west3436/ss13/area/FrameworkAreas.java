package io.github.west3436.ss13.area;

import io.github.west3436.ss13.api.area.Areas;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@link Areas} in the game: each level's {@link StoredAreas}, installed as the API's stores and
 * registered as the provider {@link Areas#STORED}, and the {@code /ss13 area} command.
 */
public final class FrameworkAreas {
    private FrameworkAreas() {}

    /**
     * Installs the stored areas and listens for command registration.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void register(IEventBus gameBus) {
        Areas.installStores(StoredAreas::of);
        // Stored areas live on the server; a client level has none until they are synced.
        Areas.register(
                Areas.STORED,
                Areas.ROOM,
                (level, pos) -> level instanceof ServerLevel server
                        ? StoredAreas.of(server).at(pos)
                        : Optional.empty());
        gameBus.addListener((RegisterCommandsEvent event) -> AreaCommand.register(event.getDispatcher()));
    }
}
