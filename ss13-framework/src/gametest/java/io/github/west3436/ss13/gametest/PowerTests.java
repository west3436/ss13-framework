package io.github.west3436.ss13.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.area.AreaStore;
import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.ss13.api.power.AreaPower;
import io.github.west3436.ss13.api.power.DevicePower;
import io.github.west3436.ss13.api.power.EnergyStore;
import io.github.west3436.ss13.api.power.PowerChannel;
import io.github.west3436.ss13.api.power.PowerFeed.Outcome;
import io.github.west3436.ss13.power.AreaDevices;
import io.github.west3436.ss13.power.Batteries;
import io.github.west3436.ss13.power.BatteryItem;
import io.github.west3436.ss13.power.WorkToFe;
import io.github.west3436.ss13.registry.FrameworkComponents;
import io.github.west3436.ss13.registry.FrameworkItems;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The power contract with test providers ({@link TestPower}): an APC-like provider takes over
 * powering a device a controller-like one still controls, and hands it back when removed; channels
 * switch off on their own; a provider's buffer fills and drains through the FE capability; and the
 * batteries.
 *
 * <p>The providers and devices step on the real schedule, a step a second, so each test waits for
 * steps rather than stepping devices itself.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerTests {
    private static final String BATCH = "ss13_framework_power";

    /** Long enough for every provider to step twice, and a lease to lapse. */
    private static final int STEPS_2 = 2 * DevicePower.PERIOD + 5;

    private PowerTests() {}

    private static ResourceLocation area(String path) {
        return TestDevices.id("power_test_" + path);
    }

    /** Defines an area holding the given relative blocks. */
    private static void defineArea(GameTestHelper h, ResourceLocation id, BlockPos... blocks) {
        AreaStore store = Areas.store(h.getLevel());
        store.remove(id);
        store.define(id, id.getPath());
        store.assign(id, List.of(blocks).stream().map(h::absolutePos).toList());
    }

    private static TestPower.Part part(GameTestHelper h, BlockPos pos, PowerChannel channel) {
        h.setBlock(pos, TestPower.device(channel));
        return h.getBlockEntity(pos);
    }

    private static TestPower.Provider controller(GameTestHelper h, BlockPos pos, BlockPos... links) {
        h.setBlock(pos, TestPower.controller());
        TestPower.Provider c = h.getBlockEntity(pos);
        c.store.set(TestPower.CAPACITY);
        for (BlockPos link : links) {
            c.links.add(h.absolutePos(link));
        }
        return c;
    }

    private static TestPower.Provider apc(GameTestHelper h, BlockPos pos, ResourceLocation area, int energy) {
        h.setBlock(pos, TestPower.apc());
        TestPower.Provider a = h.getBlockEntity(pos);
        a.area = area;
        a.store.set(energy);
        return a;
    }

    // ---- Takeover ---------------------------------------------------------------------------

    /**
     * The Phase B gate, with test providers: a vent its controller powers and controls is taken over
     * by an APC whose area it is in. The APC powers it, the controller stops paying for it and goes
     * on ordering it about; when the APC is removed, the controller powers it again. The vent never
     * goes a tick without power.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = BATCH)
    public static void an_apc_takes_over_a_vent_its_controller_still_controls(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ResourceLocation room = area("takeover");
        BlockPos ventAt = new BlockPos(2, 1, 0);
        BlockPos apcAt = new BlockPos(4, 1, 0);
        defineArea(h, room, ventAt, apcAt);
        TestPower.Part vent = part(h, ventAt, PowerChannel.ENVIRONMENT);
        TestPower.Provider controller = controller(h, new BlockPos(0, 1, 0), ventAt);
        boolean[] everPowered = {false};
        h.onEachTick(() -> {
            if (vent.power().isPowered()) {
                everPowered[0] = true;
            } else if (everPowered[0]) {
                h.fail("the vent went without power at " + h.getTick());
            }
        });
        TestPower.Provider[] apc = new TestPower.Provider[1];
        long[] controllerStore = new long[1];
        int[] order = new int[1];

        h.runAfterDelay(STEPS_2, () -> {
            h.assertTrue(vent.power().isPowered(), "the controller didn't power the vent");
            h.assertTrue(
                    vent.power().source() == controller,
                    "the vent's source: " + vent.power().source());
            h.assertTrue(vent.orderedBy == controller && vent.order > 0, "the controller doesn't control it");
            apc[0] = apc(h, apcAt, room, TestPower.CAPACITY);
        });
        h.runAfterDelay(2 * STEPS_2, () -> {
            h.assertTrue(
                    vent.power().source() == apc[0],
                    "the APC didn't take over: " + vent.power().source());
            h.assertTrue(vent.power().claimant() == apc[0], "the APC doesn't hold the vent");
            h.assertTrue(AreaPower.provider(level, room).orElse(null) == apc[0], "the APC doesn't hold its area");
            h.assertValueEqual(
                    controller.outcome(vent), Outcome.HELD_BY_ANOTHER, "the controller's outcome for the vent");
            h.assertTrue(apc[0].store.getEnergyStored() < TestPower.CAPACITY, "the APC didn't pay for the vent");
            controllerStore[0] = controller.store.getEnergyStored();
            order[0] = vent.order;
        });
        h.runAfterDelay(3 * STEPS_2, () -> {
            h.assertValueEqual(
                    (long) controller.store.getEnergyStored(),
                    controllerStore[0],
                    "the controller's buffer while the APC powers the vent");
            h.assertTrue(vent.order > order[0], "the controller stopped ordering the vent about");
            h.assertTrue(vent.orderedBy == controller, "the vent's orders came from " + vent.orderedBy);
            h.setBlock(apcAt, Blocks.AIR);
            h.assertTrue(vent.power().claimant() == null, "a removed APC still holds the vent");
            h.assertTrue(AreaPower.provider(level, room).isEmpty(), "a removed APC still holds its area");
        });
        h.runAfterDelay(4 * STEPS_2, () -> {
            h.assertTrue(vent.power().source() == controller, "the vent didn't fall back to its controller");
            h.assertTrue(vent.power().claimant() == controller, "the controller doesn't hold the vent");
            h.assertTrue(controller.store.getEnergyStored() < controllerStore[0], "the controller didn't pay again");
            h.assertTrue(vent.worked >= 6, "the vent worked " + vent.worked + " steps");
            Areas.store(level).remove(room);
            h.succeed();
        });
    }

    // ---- Channels ---------------------------------------------------------------------------

    /**
     * An APC's channels switch off on their own: the devices on a channel that is off lose power
     * when their lease runs out, and the others keep it. A device on a channel that is off stays the
     * APC's, so the controller that also covers it doesn't power it.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = BATCH)
    public static void channels_switch_off_on_their_own(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ResourceLocation room = area("channels");
        BlockPos ventAt = new BlockPos(1, 1, 1);
        BlockPos rechargerAt = new BlockPos(2, 1, 1);
        BlockPos lightAt = new BlockPos(3, 1, 1);
        BlockPos apcAt = new BlockPos(4, 1, 1);
        defineArea(h, room, ventAt, rechargerAt, lightAt, apcAt);
        TestPower.Part vent = part(h, ventAt, PowerChannel.ENVIRONMENT);
        TestPower.Part recharger = part(h, rechargerAt, PowerChannel.EQUIPMENT);
        TestPower.Part light = part(h, lightAt, PowerChannel.LIGHTING);
        TestPower.Provider apc = apc(h, apcAt, room, TestPower.CAPACITY);
        TestPower.Provider controller = controller(h, new BlockPos(0, 1, 1), ventAt);

        h.runAfterDelay(STEPS_2, () -> {
            for (TestPower.Part p : List.of(vent, recharger, light)) {
                h.assertTrue(p.power().isPowered(), p.power().channel() + " isn't powered");
                h.assertTrue(p.power().source() == apc, p.power().channel() + "'s source isn't the APC");
            }
            h.assertValueEqual(AreaDevices.in(level, room).size(), 3, "the devices in the APC's area");
            h.assertTrue(AreaPower.channelOn(level, room, PowerChannel.LIGHTING), "the area's lighting is off");
            apc.on.remove(PowerChannel.LIGHTING);
        });
        h.runAfterDelay(2 * STEPS_2 + DevicePower.PERIOD, () -> {
            h.assertFalse(light.power().isPowered(), "the light kept its power with lighting off");
            h.assertTrue(vent.power().isPowered(), "the vent lost power with lighting off");
            h.assertTrue(recharger.power().isPowered(), "the recharger lost power with lighting off");
            h.assertValueEqual(apc.outcome(light), Outcome.CHANNEL_OFF, "the APC's outcome for the light");
            h.assertTrue(light.power().claimant() == apc, "the APC let go of the light");
            h.assertFalse(AreaPower.channelOn(level, room, PowerChannel.LIGHTING), "the area's lighting is on");
            h.assertTrue(AreaPower.channelOn(level, room, PowerChannel.ENVIRONMENT), "the area's environment is off");
            apc.on.add(PowerChannel.LIGHTING);
            apc.on.remove(PowerChannel.ENVIRONMENT);
        });
        h.runAfterDelay(4 * STEPS_2, () -> {
            h.assertTrue(light.power().isPowered(), "the light didn't come back with lighting on");
            h.assertFalse(vent.power().isPowered(), "the vent kept its power with the environment off");
            h.assertTrue(recharger.power().isPowered(), "the recharger lost power with the environment off");
            h.assertTrue(vent.power().claimant() == apc, "the controller took the vent from the APC");
            h.assertValueEqual(
                    controller.outcome(vent), Outcome.HELD_BY_ANOTHER, "the controller's outcome for the vent");
            h.assertTrue(vent.orderedBy == controller, "the controller stopped ordering the vent about");
            Areas.store(level).remove(room);
            h.succeed();
        });
    }

    // ---- FE ---------------------------------------------------------------------------------

    /**
     * A provider's buffer takes FE from a battery and from a generator through the FE capability, gives
     * none back, and drains by what it powers; when it runs short, its device loses power.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = BATCH)
    public static void a_buffer_fills_through_fe_and_drains_by_what_it_powers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ResourceLocation room = area("fe");
        BlockPos ventAt = new BlockPos(1, 1, 2);
        BlockPos apcAt = new BlockPos(3, 1, 2);
        defineArea(h, room, ventAt, apcAt);
        TestPower.Part vent = part(h, ventAt, PowerChannel.ENVIRONMENT);
        TestPower.Provider apc = apc(h, apcAt, room, 0);
        IEnergyStorage side = level.getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(apcAt), Direction.UP);
        h.assertTrue(side == apc.store, "the APC's buffer isn't its FE capability");

        // A battery charged full empties into the buffer.
        ItemStack battery = FrameworkItems.BATTERY.get().full();
        IEnergyStorage cell = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        int moved = side.receiveEnergy(cell.extractEnergy(Integer.MAX_VALUE, false), false);
        h.assertValueEqual(moved, 100_000, "FE from a full battery");
        h.assertValueEqual(BatteryItem.energy(battery), 0, "the battery after");
        h.assertValueEqual(side.extractEnergy(1000, false), 0, "FE taken out of a buffer");

        // A generator next to it pushes its store in.
        EnergyStore generator = new EnergyStore(() -> 50_000, false, true, () -> {});
        generator.set(50_000);
        h.assertValueEqual(
                WorkToFe.push(level, h.absolutePos(apcAt.west()), generator), 50_000, "FE a generator pushed");
        h.assertValueEqual(apc.store.getEnergyStored(), 150_000, "the buffer filled");

        int[] start = new int[2];
        h.runAfterDelay(STEPS_2, () -> {
            h.assertTrue(vent.power().isPowered(), "the vent isn't powered");
            start[0] = apc.steps;
            start[1] = apc.store.getEnergyStored();
        });
        h.runAfterDelay(2 * STEPS_2, () -> {
            int steps = apc.steps - start[0];
            h.assertTrue(steps >= 2, "the APC stepped " + steps + " times");
            h.assertValueEqual(
                    start[1] - apc.store.getEnergyStored(),
                    steps * TestPower.DRAW * DevicePower.PERIOD,
                    "what " + steps + " steps cost");
            apc.store.set(TestPower.DRAW * DevicePower.PERIOD + 50);
        });
        h.runAfterDelay(4 * STEPS_2, () -> {
            h.assertValueEqual(apc.store.getEnergyStored(), 50, "the buffer after its last full step");
            h.assertValueEqual(apc.outcome(vent), Outcome.SHORT, "the APC's outcome for the vent");
            h.assertFalse(vent.power().isPowered(), "the vent kept its power with the buffer short");
            Areas.store(level).remove(room);
            h.succeed();
        });
    }

    // ---- Batteries --------------------------------------------------------------------------

    /**
     * Each battery fills through the FE item capability to its capacity, 100,000, 1,000,000 and
     * 2,000,000 FE, and no further, gives FE back the same way, stacks to one, is in the battery tag,
     * and its creative copy is full.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void batteries_fill_and_drain_through_fe(GameTestHelper h) {
        int[] capacities = {100_000, 1_000_000, 2_000_000};
        List<DeferredItem<BatteryItem>> batteries = FrameworkItems.batteries();
        h.assertValueEqual(batteries.size(), 3, "batteries");
        for (int i = 0; i < 3; i++) {
            BatteryItem item = batteries.get(i).get();
            ItemStack stack = new ItemStack(item);
            h.assertTrue(Batteries.isBattery(stack), item + " isn't a battery");
            h.assertTrue(stack.is(Batteries.TAG), item + " isn't in the tag");
            h.assertValueEqual(stack.getMaxStackSize(), 1, item + "'s stack size");
            IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(e != null, item + " has no FE item capability");
            h.assertValueEqual(e.getMaxEnergyStored(), capacities[i], item + "'s capacity");
            h.assertValueEqual(e.getEnergyStored(), 0, item + " new");
            h.assertValueEqual(BatteryItem.charge(stack), 0f, item + "'s charge new");
            h.assertValueEqual(e.receiveEnergy(capacities[i] / 2, false), capacities[i] / 2, item + " half filled");
            h.assertValueEqual(BatteryItem.charge(stack), 0.5f, item + "'s charge half full");
            h.assertValueEqual(e.receiveEnergy(capacities[i], false), capacities[i] / 2, item + " filled");
            h.assertValueEqual(e.receiveEnergy(1, false), 0, item + " over full");
            h.assertValueEqual(BatteryItem.energy(stack), capacities[i], item + "'s component");
            h.assertValueEqual(stack.get(FrameworkComponents.ENERGY.get()), capacities[i], item + "'s data");
            h.assertValueEqual(e.extractEnergy(1000, false), 1000, item + " drained");
            h.assertValueEqual(BatteryItem.energy(item.full()), capacities[i], item + " full");
        }
        h.assertFalse(Batteries.isBattery(new ItemStack(Items.REDSTONE)), "redstone is a battery");
        h.assertFalse(Batteries.isBattery(ItemStack.EMPTY), "nothing is a battery");
        h.succeed();
    }

    /** A battery slot takes one battery, and nothing else. */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_battery_slot_takes_only_a_battery(GameTestHelper h) {
        int[] changes = {0};
        Batteries.Slot slot = new Batteries.Slot(() -> changes[0]++);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        h.assertValueEqual(slot.insertItem(0, redstone, false).getCount(), 1, "redstone left over");
        h.assertTrue(slot.battery().isEmpty(), "the slot took redstone");
        ItemStack battery = FrameworkItems.HIGH_CAPACITY_BATTERY.get().full();
        h.assertTrue(slot.insertItem(0, battery, false).isEmpty(), "the slot refused a battery");
        h.assertValueEqual(slot.energy().getEnergyStored(), 1_000_000, "the battery's FE through the slot");
        h.assertTrue(changes[0] > 0, "the slot didn't say it changed");
        h.assertFalse(
                slot.insertItem(0, FrameworkItems.BATTERY.get().full(), false).isEmpty(),
                "the slot took a second battery");
        h.succeed();
    }

    /** Each battery has a recipe from its capacitor, a name, and a model for each charge. */
    @GameTest(template = "empty", batch = BATCH)
    public static void batteries_have_recipes_names_and_models(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        JsonObject lang = json(h, "/assets/ss13_framework/lang/en_us.json");
        for (DeferredItem<BatteryItem> b : FrameworkItems.batteries()) {
            Optional<RecipeHolder<?>> recipe = level.getRecipeManager().byKey(b.getId());
            h.assertTrue(recipe.isPresent(), "no recipe " + b.getId());
            ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
            h.assertTrue(result.is(b.get()), b.getId() + "'s recipe makes " + result);
            String id = b.getId().getPath();
            h.assertTrue(lang.has("item.ss13_framework." + id), "no name for " + id);
            for (String model : List.of(id, id + "_partial", id + "_full")) {
                json(h, "/assets/ss13_framework/models/item/" + model + ".json");
            }
        }
        h.assertTrue(lang.has("item.ss13_framework.battery.charge"), "no charge tooltip");
        h.succeed();
    }

    private static JsonObject json(GameTestHelper h, String path) {
        try (InputStream in = PowerTests.class.getResourceAsStream(path)) {
            h.assertTrue(in != null, "no " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("can't read " + path, e);
        }
    }
}
