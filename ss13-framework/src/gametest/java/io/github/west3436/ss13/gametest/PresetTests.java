package io.github.west3436.ss13.gametest;

import static io.github.west3436.ss13.gametest.TestPresetMods.ALPHA_SHARED;
import static io.github.west3436.ss13.gametest.TestPresetMods.BETA_SHARED;
import static io.github.west3436.ss13.gametest.TestPresetMods.FLAG;
import static io.github.west3436.ss13.gametest.TestPresetMods.RATE;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.Presets;
import io.github.west3436.ss13.preset.PresetFile;
import io.github.west3436.ss13.preset.PresetService;
import io.github.west3436.ss13.preset.WorldPresets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Normal/Hardcore switch on a running server, through {@code /ss13 preset} and a mod's alias,
 * with the options of two test mods ({@link TestPresetMods}). Each test has a batch of its own, so
 * none runs while another has the preset switched, and leaves the preset Normal with no override.
 *
 * <p>The GameTest server isn't dedicated, so a change is saved to the world's own copy of the
 * preset file, as in singleplayer.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetTests {
    private PresetTests() {}

    /** One switch to Hardcore sets both test mods' options, and one back to Normal resets them. */
    @GameTest(template = "empty", batch = "preset_switch")
    public static void hardcore_sets_two_mods_options(GameTestHelper h) {
        try {
            h.assertValueEqual(Presets.active(), Preset.NORMAL, "the preset at the start");
            h.assertValueEqual(FLAG.get(), false, "alpha's flag under Normal");
            h.assertValueEqual(RATE.get(), 1.0, "beta's rate under Normal");

            run(h, "ss13 preset hardcore");
            h.assertValueEqual(Presets.active(), Preset.HARDCORE, "the preset");
            h.assertValueEqual(FLAG.get(), true, "alpha's flag under Hardcore");
            h.assertValueEqual(RATE.get(), 0.5, "beta's rate under Hardcore");

            run(h, "ss13 preset normal");
            h.assertValueEqual(FLAG.get(), false, "alpha's flag back under Normal");
            h.assertValueEqual(RATE.get(), 1.0, "beta's rate back under Normal");
        } finally {
            reset(h);
        }
        h.succeed();
    }

    /**
     * The switch and an override are saved to the world's own preset file, and reading the files
     * again, as a restart does, finds both. The shared file, which other worlds read, stays Normal.
     */
    @GameTest(template = "empty", batch = "preset_restart")
    public static void the_choice_survives_a_restart(GameTestHelper h) throws IOException {
        MinecraftServer server = h.getLevel().getServer();
        try {
            run(h, "ss13 preset hardcore");
            run(h, "ss13 preset override ss13_test_alpha:flag false");
            WorldPresets before = PresetService.world().orElseThrow();
            h.assertTrue(before.hasOwnCopy(), "the world has no preset file of its own: " + before.file());
            Path file = before.file();
            h.assertValueEqual(PresetFile.read(file).preset(), Preset.HARDCORE, "the preset in " + file);
            h.assertValueEqual(
                    PresetFile.read(before.sharedFile()).preset(), Preset.NORMAL, "the preset in the shared file");

            WorldPresets after = PresetService.reloadForTest(server);
            h.assertValueEqual(after.file(), file, "the file read after the restart");
            h.assertValueEqual(Presets.active(), Preset.HARDCORE, "the preset after the restart");
            h.assertValueEqual(FLAG.get(), false, "alpha's overridden flag after the restart");
            h.assertValueEqual(FLAG.source(), PresetOption.Source.OVERRIDE, "where alpha's flag comes from");
            h.assertValueEqual(RATE.get(), 0.5, "beta's rate after the restart");
            h.assertTrue(Files.readString(file).contains("flag = false"), "the override isn't in " + file);
        } finally {
            reset(h);
        }
        h.succeed();
    }

    /**
     * Under a mod's alias, a key alone names that mod's option first; under {@code /ss13} it needs
     * the mod's namespace when two mods use the key. Unknown options and values are refused.
     */
    @GameTest(template = "empty", batch = "preset_alias")
    public static void an_alias_names_its_own_mods_options(GameTestHelper h) {
        try {
            run(h, "ss13_test_beta preset override rate 0.25");
            h.assertValueEqual(RATE.get(), 0.25, "beta's rate, overridden through its alias");
            run(h, "ss13_test_beta preset hardcore");
            h.assertValueEqual(RATE.get(), 0.25, "beta's overridden rate under Hardcore");
            h.assertValueEqual(FLAG.get(), true, "alpha's flag, switched through beta's alias");
            run(h, "ss13_test_beta preset clear rate");
            h.assertValueEqual(RATE.get(), 0.5, "beta's rate, following the preset again");

            run(h, "ss13_test_beta preset override shared true");
            h.assertValueEqual(BETA_SHARED.source(), PresetOption.Source.OVERRIDE, "beta's shared, through its alias");
            h.assertValueEqual(ALPHA_SHARED.source(), PresetOption.Source.PRESET, "alpha's shared");
            refused(h, "ss13 preset override shared true", "a key two mods use, without a namespace");
            run(h, "ss13 preset override ss13_test_alpha:shared false");
            h.assertValueEqual(ALPHA_SHARED.get(), false, "alpha's shared, named in full");

            run(h, "ss13 preset override flag false");
            h.assertValueEqual(FLAG.get(), false, "alpha's flag, by a key only it uses");

            refused(h, "ss13 preset override no_such_option true", "an unknown option");
            refused(h, "ss13 preset override ss13_test_beta:rate 99", "a value out of range");
            refused(h, "ss13 preset override ss13_test_alpha:flag maybe", "a value that isn't a boolean");
        } finally {
            reset(h);
        }
        h.succeed();
    }

    // ---- Helpers -----------------------------------------------------------------------

    private static CommandSourceStack source(GameTestHelper h) {
        return h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
    }

    private static void run(GameTestHelper h, String command) {
        try {
            h.getLevel().getServer().getCommands().getDispatcher().execute(command, source(h));
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
    }

    private static void refused(GameTestHelper h, String command, String what) {
        try {
            h.getLevel().getServer().getCommands().getDispatcher().execute(command, source(h));
        } catch (CommandSyntaxException expected) {
            return;
        }
        throw new GameTestAssertException("/" + command + " was accepted: " + what);
    }

    /** Normal, with no test option overridden. */
    private static void reset(GameTestHelper h) {
        for (PresetOption<?> option : new PresetOption<?>[] {FLAG, RATE, ALPHA_SHARED, BETA_SHARED}) {
            if (option.source() == PresetOption.Source.OVERRIDE) {
                option.clearOverride();
            }
        }
        if (Presets.active() != Preset.NORMAL) {
            Presets.switchTo(Preset.NORMAL);
        }
    }
}
