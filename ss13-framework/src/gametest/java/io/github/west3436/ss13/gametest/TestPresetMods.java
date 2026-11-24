package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.PresetType;
import io.github.west3436.ss13.api.preset.Presets;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Two test mods' preset options, registered the way a mod registers its own, so the GameTests and
 * a person trying the command in a dev run can see one switch set both:
 *
 * <ul>
 *   <li>{@code ss13_test_alpha}: {@code flag} (false, Hardcore true) and {@code shared};
 *   <li>{@code ss13_test_beta}: {@code rate} (1, Hardcore 0.5) and {@code shared}, with a command
 *       root {@code /ss13_test_beta} that offers the preset command as an alias.
 * </ul>
 *
 * <p>They load with the gametest source set, which every dev run has, never with the mod jar.
 */
@EventBusSubscriber(modid = FrameworkApi.MOD_ID)
public final class TestPresetMods {
    /** The first test mod's namespace. */
    public static final String ALPHA = "ss13_test_alpha";

    /** The second test mod's namespace, also its command root. */
    public static final String BETA = "ss13_test_beta";

    /** A switch, on under Hardcore. */
    public static final PresetOption<Boolean> FLAG = Presets.register(
            id(ALPHA, "flag"), PresetType.BOOLEAN, false, true, "A test mod's switch, on under Hardcore");

    /** A rate, halved under Hardcore. */
    public static final PresetOption<Double> RATE = Presets.register(
            id(BETA, "rate"),
            PresetType.number(0, 10, "per second"),
            1.0,
            0.5,
            "A test mod's rate, halved under Hardcore");

    /** The first mod's option with a key the second also uses. */
    public static final PresetOption<Boolean> ALPHA_SHARED =
            Presets.register(id(ALPHA, "shared"), PresetType.BOOLEAN, false, true, "A key both test mods use");

    /** The second mod's option with that key. */
    public static final PresetOption<Boolean> BETA_SHARED =
            Presets.register(id(BETA, "shared"), PresetType.BOOLEAN, false, true, "A key both test mods use");

    private TestPresetMods() {}

    /** The second test mod's command root, which the framework adds {@code preset} under. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        Presets.addCommandAlias(BETA);
        event.getDispatcher().register(Commands.literal(BETA).requires(source -> source.hasPermission(2)));
    }

    private static ResourceLocation id(String namespace, String key) {
        return ResourceLocation.fromNamespaceAndPath(namespace, key);
    }
}
