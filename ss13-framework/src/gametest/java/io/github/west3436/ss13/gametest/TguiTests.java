package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.client.tgui.Readout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The parts of the TGUI kit that a dedicated server loads too. */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TguiTests {
    private TguiTests() {}

    /** An item's tooltip writes readings on a server too, so Readout loads there without client classes. */
    @GameTest(template = "empty")
    public static void readout_works_on_a_dedicated_server(GameTestHelper helper) {
        helper.assertValueEqual(Readout.pressure(101.325), "101.3 kPa", "a pressure");
        helper.assertValueEqual(Readout.temperature(293.15), "20.0 °C", "a temperature");
        helper.succeed();
    }
}
