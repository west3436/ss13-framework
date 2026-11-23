package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The framework loads on a dedicated server, with the API beside it. */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoadTests {
    private LoadTests() {}

    @GameTest(template = "empty")
    public static void framework_mod_loads(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(FrameworkApi.MOD_ID), "ss13_framework isn't loaded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void api_is_its_own_module(GameTestHelper helper) {
        // The API loads as a GAMELIBRARY, never as part of the mod: listing it in the mod would
        // split its package across two modules, and a nesting mod's copy couldn't replace ours.
        Module api = FrameworkApi.class.getModule();
        Module mod = io.github.west3436.ss13.Ss13Framework.class.getModule();
        helper.assertTrue(api != mod, "the API loaded inside the mod's module " + mod.getName());
        helper.succeed();
    }
}
