package io.github.west3436.ss13;

import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.FrameworkApi;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** The {@code ss13_framework} mod. It holds nothing yet: Phase A moves the shared parts in. */
@Mod(FrameworkApi.MOD_ID)
public final class Ss13Framework {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Ss13Framework(ModContainer container) {
        // Which copies loaded matters when several mods nest the framework, so say so once.
        LOGGER.info(
                "SS13 Framework {} loaded with API {}",
                container.getModInfo().getVersion(),
                FrameworkApi.version().orElse("from class directories"));
    }
}
