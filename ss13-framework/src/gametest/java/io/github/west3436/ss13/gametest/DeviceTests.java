package io.github.west3436.ss13.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.device.BlockMover;
import io.github.west3436.ss13.api.device.BlockMovers;
import io.github.west3436.ss13.device.BlockTurns;
import io.github.west3436.ss13.device.DeviceActivity;
import io.github.west3436.ss13.device.DeviceActivityPayload;
import io.github.west3436.ss13.device.DeviceActivitySync;
import io.github.west3436.ss13.device.DeviceData;
import io.github.west3436.ss13.device.DeviceTicker;
import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.ss13.device.LoadedBlocks;
import io.github.west3436.ss13.device.RedstoneControl;
import io.github.west3436.ss13.device.WallPanelBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The device base with devices that have no gas in them ({@link TestDevices}): the schedule, what a
 * device saves, its redstone setting, how hard it works and who is told, waiting while its block is
 * moved, and the blocks devices are made of.
 *
 * <p>Tests that step a level's devices themselves ({@link DeviceTicker#stepNow}) step every test
 * device in the level, so the test that counts real steps runs in a batch of its own.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeviceTests {
    private static final String STEPPED = "ss13_framework_devices_stepped";

    /** The box a test mover is moving now, or null; one test at a time uses it. */
    private static volatile BoundingBox moving;

    static {
        BlockMovers.register(TestDevices.id("test_mover"), new BlockMover() {
            @Override
            public boolean isMoving(ServerLevel level, BlockPos pos) {
                BoundingBox box = moving;
                return box != null && box.isInside(pos);
            }

            @Override
            public Vec3 shownAt(Level level, Vec3 point) {
                BoundingBox box = moving;
                return box != null && box.isInside(BlockPos.containing(point)) ? point.add(0, 1000, 0) : point;
            }
        });
    }

    private DeviceTests() {}

    private static TestDevices.Device device(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, TestDevices.device());
        return h.getBlockEntity(pos);
    }

    private static FakePlayer player(GameTestHelper h, String name, Vec3 at) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.moveTo(at);
        return p;
    }

    // ---- The schedule -------------------------------------------------------------------

    /**
     * A device with no gas runs on the base: each steps every 20 game ticks, on the server thread,
     * in its own phase, and a removed device leaves the schedule and never steps again.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ss13_framework_device_schedule")
    public static void devices_step_once_per_device_tick(GameTestHelper h) {
        List<BlockPos> at = List.of(new BlockPos(0, 1, 0), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0));
        List<TestDevices.Device> devices = at.stream().map(p -> device(h, p)).toList();
        h.runAfterDelay(110, () -> {
            Thread server = h.getLevel().getServer().getRunningThread();
            Set<Long> phases = new TreeSet<>();
            for (TestDevices.Device d : devices) {
                h.assertTrue(d.steps.size() >= 5 && d.steps.size() <= 6, "steps in 110 ticks: " + d.steps);
                for (int i = 1; i < d.steps.size(); i++) {
                    h.assertValueEqual(
                            d.steps.get(i) - d.steps.get(i - 1), (long) DeviceTicks.PERIOD, "ticks between steps");
                }
                for (Thread t : d.threads) {
                    h.assertTrue(t == server, "stepped on " + t.getName() + ", not the server thread");
                }
                phases.add(d.steps.get(0) % DeviceTicks.PERIOD);
                h.assertTrue(DeviceTicker.loaded(h.getLevel()).contains(d), "not in the level's schedule");
                h.assertTrue(d.stepping(), "a device in a ticking chunk isn't stepping");
            }
            h.assertValueEqual(phases.size(), devices.size(), "devices placed together share a phase");
            h.setBlock(at.get(2), Blocks.AIR);
        });
        h.runAfterDelay(111, () -> {
            TestDevices.Device removed = devices.get(2);
            int before = removed.steps.size();
            h.assertFalse(DeviceTicker.isScheduled(removed), "a removed device is still scheduled");
            h.runAfterDelay(40, () -> {
                h.assertValueEqual(removed.steps.size(), before, "a removed device's steps");
                h.succeed();
            });
        });
    }

    /**
     * Runs a test's checks once its devices have joined the schedule, which they do at the level's
     * next tick, not as they are placed. Each check steps the level's devices itself, all at once, so
     * the real schedule never steps them between two checks.
     */
    private static void onceLoaded(GameTestHelper h, Runnable checks) {
        h.runAfterDelay(2, () -> {
            checks.run();
            h.succeed();
        });
    }

    /** A device that isn't ready is skipped; one that takes no label steps all the same. */
    @GameTest(template = "empty", batch = STEPPED)
    public static void a_device_steps_only_when_ready(GameTestHelper h) {
        TestDevices.Device d = device(h, new BlockPos(1, 1, 1));
        d.ready = false;
        h.setBlock(new BlockPos(3, 1, 1), TestDevices.plainDevice());
        TestDevices.PlainDevice plain = h.getBlockEntity(new BlockPos(3, 1, 1));
        onceLoaded(h, () -> {
            int plainBefore = plain.steps;
            DeviceTicker.stepNow(h.getLevel());
            h.assertTrue(d.steps.isEmpty(), "a device that isn't ready stepped");
            h.assertValueEqual(plain.steps, plainBefore + 1, "the plain device's steps");
            d.ready = true;
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.steps.size(), 1, "steps once ready");
        });
    }

    /** A device whose block a mover is moving waits, and steps again once the move is over. */
    @GameTest(template = "empty", batch = STEPPED)
    public static void a_device_waits_while_its_block_is_moved(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TestDevices.Device d = device(h, pos);
        BlockPos abs = h.absolutePos(pos);
        onceLoaded(h, () -> {
            int before = d.steps.size();
            moving = new BoundingBox(abs);
            try {
                DeviceTicker.stepNow(h.getLevel());
                h.assertValueEqual(d.steps.size(), before, "steps while it is moved");
                Vec3 shown = BlockMovers.shownAt(h.getLevel(), Vec3.atCenterOf(abs));
                h.assertValueEqual(shown, Vec3.atCenterOf(abs).add(0, 1000, 0), "where it is shown");
            } finally {
                moving = null;
            }
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.steps.size(), before + 1, "steps once the move is over");
        });
    }

    // ---- What it saves ------------------------------------------------------------------

    /**
     * A device saves its format version, and one saved with no version, from before versions were
     * added, loads and saves the same fields. One from a newer mod keeps the fields it knows.
     */
    @GameTest(template = "empty")
    public static void a_device_saves_its_format_version(GameTestHelper h) {
        TestDevices.Device d = device(h, new BlockPos(1, 1, 1));
        d.redstone().set(RedstoneControl.Mode.HIGH);
        d.setLabel("Supply 1");
        var registries = h.getLevel().registryAccess();
        CompoundTag saved = d.saveCustomOnly(registries);
        h.assertValueEqual(saved.getInt(DeviceData.VERSION_KEY), DeviceData.VERSION, "the version saved");
        h.assertValueEqual(saved.getString("Label"), "Supply 1", "the label saved");
        h.assertValueEqual(saved.getString(RedstoneControl.KEY), "high", "the redstone setting saved");

        CompoundTag old = saved.copy();
        old.remove(DeviceData.VERSION_KEY);
        TestDevices.Device reloaded = (TestDevices.Device) ((EntityBlock) TestDevices.device())
                .newBlockEntity(BlockPos.ZERO, TestDevices.device().defaultBlockState());
        reloaded.loadCustomOnly(old, registries);
        h.assertValueEqual(reloaded.saveCustomOnly(registries), saved, "an old save saved again");
        h.assertValueEqual(reloaded.label(), "Supply 1", "the label of an old save");

        CompoundTag newer = saved.copy();
        newer.putInt(DeviceData.VERSION_KEY, DeviceData.VERSION + 5);
        reloaded.loadCustomOnly(newer, registries);
        h.assertValueEqual(reloaded.redstone().mode(), RedstoneControl.Mode.HIGH, "a newer save's setting");
        h.succeed();
    }

    // ---- Its redstone setting -----------------------------------------------------------

    /** A device set to run on a signal runs only while it gets one, and the setting only stops it. */
    @GameTest(template = "empty", batch = STEPPED)
    public static void a_device_set_high_runs_only_with_a_signal(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TestDevices.Device d = device(h, pos);
        onceLoaded(h, () -> {
            int ran = d.ran.size();
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.ran.size(), ran + 1, "runs when redstone is ignored");

            d.redstone().set(RedstoneControl.Mode.HIGH);
            h.assertFalse(d.redstone().signal(), "a signal with none there");
            h.assertTrue(RedstoneControl.holds(d.redstone().data()), "the menu doesn't say it is held");
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.ran.size(), ran + 1, "runs set high with no signal");

            h.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.ran.size(), ran + 2, "runs with a signal");
            h.assertTrue(d.redstone().signal(), "no signal read");

            int low = RedstoneControl.FIRST_BUTTON + RedstoneControl.Mode.LOW.ordinal();
            h.assertTrue(d.redstone().button(low), "the button for low");
            h.assertValueEqual(d.redstone().mode(), RedstoneControl.Mode.LOW, "the mode the button set");
            DeviceTicker.stepNow(h.getLevel());
            h.assertValueEqual(d.ran.size(), ran + 2, "runs set low with a signal");
            h.setBlock(pos.east(), Blocks.AIR); // outside the template, so nothing else clears it
        });
    }

    // ---- How hard it works --------------------------------------------------------------

    /**
     * A working device is told to the players near it, and only to them, and its stopping is told
     * once. Fake players aren't in the player list, so this asks the sync what it would send.
     */
    @GameTest(template = "empty", batch = STEPPED)
    public static void a_working_device_is_told_to_players_near_it(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TestDevices.Device d = device(h, pos);
        d.work = 8;
        onceLoaded(h, () -> {
            ServerLevel level = h.getLevel();
            long abs = h.absolutePos(pos).asLong();
            Vec3 middle = Vec3.atCenterOf(h.absolutePos(pos));
            FakePlayer near = player(h, "ss13_activity_near", middle.add(4, 0, 0));
            FakePlayer far = player(h, "ss13_activity_far", middle.add(DeviceActivitySync.RANGE + 10, 0, 0));

            DeviceTicker.stepNow(level);
            long now = DeviceTicks.now(level);
            List<DeviceActivityPayload> sent = new ArrayList<>();
            DeviceActivityPayload told = DeviceActivitySync.syncAt(near, now, sent::add);
            h.assertTrue(told != null && sent.size() == 1, "nothing sent to a player near a working device");
            h.assertTrue(
                    told.changes().contains(new DeviceActivityPayload.Change(abs, DeviceActivity.HARD)),
                    "not told it works hard: " + told.changes());
            h.assertFalse(
                    mentions(DeviceActivitySync.syncAt(far, now, sent::add), abs), "a player out of range was told");

            DeviceTicker.stepNow(level);
            h.assertFalse(
                    mentions(DeviceActivitySync.syncAt(near, now + DeviceActivitySync.SEND_TICKS, sent::add), abs),
                    "told again with nothing changed");

            d.stopping = true;
            DeviceTicker.stepNow(level);
            DeviceActivityPayload stopped =
                    DeviceActivitySync.syncAt(near, now + 2L * DeviceActivitySync.SEND_TICKS, sent::add);
            h.assertTrue(
                    stopped != null
                            && stopped.changes().contains(new DeviceActivityPayload.Change(abs, DeviceActivity.IDLE)),
                    "not told it stopped");
        });
    }

    private static boolean mentions(DeviceActivityPayload payload, long pos) {
        return payload != null && payload.changes().stream().anyMatch(c -> c.pos() == pos);
    }

    // ---- What devices are made of -------------------------------------------------------

    /** A panel needs a wall behind it, and drops when the wall goes. */
    @GameTest(template = "empty")
    public static void a_wall_panel_drops_with_its_wall(GameTestHelper h) {
        BlockPos wall = new BlockPos(1, 1, 2);
        BlockPos at = new BlockPos(1, 1, 1);
        h.setBlock(wall, Blocks.STONE);
        h.setBlock(at.north(), Blocks.AIR); // outside the template, so nothing else clears it
        BlockState panel = TestDevices.panel().defaultBlockState().setValue(WallPanelBlock.FACING, Direction.NORTH);
        h.setBlock(at, panel);
        h.assertValueEqual(WallPanelBlock.wall(panel, h.absolutePos(at)), h.absolutePos(wall), "the wall behind it");
        h.assertTrue(panel.canSurvive(h.getLevel(), h.absolutePos(at)), "can't stand on a wall");
        h.assertFalse(
                panel.setValue(WallPanelBlock.FACING, Direction.SOUTH).canSurvive(h.getLevel(), h.absolutePos(at)),
                "stands with nothing behind it");
        h.setBlock(wall, Blocks.AIR);
        h.assertBlockPresent(Blocks.AIR, at);
        h.succeed();
    }

    /**
     * A block turns every way it faces with a structure: its facing, its axis, and for a block that
     * says it has a property for each face, those. A block that doesn't say so keeps its own.
     */
    @GameTest(template = "empty")
    public static void a_block_turns_every_way_it_faces(GameTestHelper h) {
        BlockState sides = TestDevices.sides()
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH)
                .setValue(PipeBlock.NORTH, true)
                .setValue(PipeBlock.UP, true);
        BlockState turned = BlockTurns.rotate(sides, Rotation.CLOCKWISE_90);
        h.assertValueEqual(turned.getValue(BlockStateProperties.FACING), Direction.EAST, "facing turned");
        h.assertTrue(turned.getValue(PipeBlock.EAST), "the north face didn't turn east");
        h.assertFalse(turned.getValue(PipeBlock.NORTH), "the north face stayed");
        h.assertTrue(turned.getValue(PipeBlock.UP), "the up face moved");
        BlockState mirrored = BlockTurns.mirror(sides, Mirror.LEFT_RIGHT);
        h.assertValueEqual(mirrored.getValue(BlockStateProperties.FACING), Direction.SOUTH, "facing mirrored");
        h.assertTrue(mirrored.getValue(PipeBlock.SOUTH), "the north face didn't mirror south");
        h.assertValueEqual(BlockTurns.rotate(sides, Rotation.NONE), sides, "no turn");

        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        h.assertValueEqual(
                BlockTurns.rotate(chain, Rotation.CLOCKWISE_90).getValue(BlockStateProperties.AXIS),
                Direction.Axis.Z,
                "a quarter turn of the axis");
        h.assertValueEqual(
                BlockTurns.rotate(chain, Rotation.CLOCKWISE_180).getValue(BlockStateProperties.AXIS),
                Direction.Axis.X,
                "a half turn of the axis");

        BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, true);
        h.assertValueEqual(BlockTurns.rotate(fence, Rotation.CLOCKWISE_90), fence, "a block that isn't Sides");
        h.succeed();
    }

    /** Reads only where the chunk is loaded in full, and loads nothing. */
    @GameTest(template = "empty")
    public static void loaded_blocks_read_only_what_is_loaded(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos here = h.absolutePos(new BlockPos(1, 1, 1));
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.assertValueEqual(LoadedBlocks.state(level, here), Blocks.STONE.defaultBlockState(), "a loaded block");
        h.assertTrue(LoadedBlocks.ticking(level, here), "the test's chunk doesn't tick");
        BlockPos away = new BlockPos(5_000_000, 64, 5_000_000);
        h.assertTrue(LoadedBlocks.state(level, away) == null, "read a block far from anything loaded");
        h.assertTrue(LoadedBlocks.entity(level, away) == null, "read a block entity far away");
        h.assertFalse(LoadedBlocks.ticking(level, away), "far away ticks");
        h.assertFalse(level.isLoaded(away), "reading loaded the chunk");
        h.assertTrue(
                LoadedBlocks.state(level, new BlockPos(here.getX(), 10_000, here.getZ())) == null, "above the world");
        h.succeed();
    }
}
