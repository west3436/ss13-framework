package io.github.west3436.ss13;

import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.area.FrameworkAreas;
import io.github.west3436.ss13.construction.ConstructionEvents;
import io.github.west3436.ss13.device.Devices;
import io.github.west3436.ss13.gravity.GravityEntities;
import io.github.west3436.ss13.preset.PresetService;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import io.github.west3436.ss13.registry.FrameworkCreativeTab;
import io.github.west3436.ss13.registry.FrameworkItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** The {@code ss13_framework} mod: it applies the shared contracts, such as gravity, to the game. */
@Mod(FrameworkApi.MOD_ID)
public final class Ss13Framework {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Ss13Framework(IEventBus modBus, ModContainer container) {
        // Which copies loaded matters when several mods nest the framework, so say so once.
        LOGGER.info(
                "SS13 Framework {} loaded with API {}",
                container.getModInfo().getVersion(),
                FrameworkApi.version().orElse("from class directories"));
        GravityEntities.register(NeoForge.EVENT_BUS);
        FrameworkBlocks.register(modBus);
        FrameworkItems.register(modBus);
        ConstructionEvents.register(NeoForge.EVENT_BUS);
        FrameworkCreativeTab.register(modBus);
        Devices.register(modBus, NeoForge.EVENT_BUS);
        FrameworkAreas.register(NeoForge.EVENT_BUS);
        PresetService.register(NeoForge.EVENT_BUS);
    }
}
