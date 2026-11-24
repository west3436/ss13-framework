package io.github.west3436.ss13.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.area.Area;
import io.github.west3436.ss13.api.area.AreaClaim;
import io.github.west3436.ss13.api.area.AreaStore;
import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.ss13.api.area.AreasChangedEvent;
import io.github.west3436.ss13.area.StoredAreas;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Areas: providers answering by precedence, the stored areas and their events, saving, moving, and
 * the admin command.
 *
 * <p>Stored areas need no loaded chunk, so each test draws its areas far out in the Overworld, in a
 * spot of its own ({@link #spot}), and removes them when it is done, so tests that run at once never
 * meet. The test provider {@code ss13_framework:test_ship}, registered as this class loads, stands in
 * for Expedition's ship boxes: a {@link Areas#REGION} provider that answers inside a box a test gives
 * it, off until then.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AreaTests {
    private static final Area SHIP = new Area(id("test/ship"), "Test Ship");

    private static volatile BoundingBox shipBox;

    /** Every changed event fired while the tests run. */
    private static final List<AreasChangedEvent> EVENTS = new CopyOnWriteArrayList<>();

    static {
        Areas.register(id("test_ship"), Areas.REGION, (level, pos) -> {
            BoundingBox box = shipBox;
            return box != null && level.dimension() == Level.OVERWORLD && box.isInside(pos)
                    ? Optional.of(SHIP)
                    : Optional.empty();
        });
        NeoForge.EVENT_BUS.addListener(AreasChangedEvent.class, EVENTS::add);
    }

    private AreaTests() {}

    // ---- The tests ----------------------------------------------------------------------

    /**
     * The issue's first test: a ship's box from a region provider, and a room drawn inside it in the
     * stored areas. Inside the room the room wins and the ship is under it; on the ship outside the
     * room the ship answers; off the ship nothing does.
     */
    @GameTest(template = "empty")
    public static void overlapping_providers_answer_by_precedence(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = spot(0);
        AreaStore store = Areas.store(level);
        ResourceLocation roomId = id("test/precedence_bridge");
        try {
            shipBox = new BoundingBox(
                    base.getX(), base.getY(), base.getZ(), base.getX() + 9, base.getY() + 4, base.getZ() + 9);
            Area bridge = store.define(roomId, "Bridge");
            store.assign(
                    roomId,
                    new BoundingBox(
                            base.getX() + 1,
                            base.getY() + 1,
                            base.getZ() + 1,
                            base.getX() + 3,
                            base.getY() + 3,
                            base.getZ() + 3));

            BlockPos inRoom = base.offset(2, 2, 2);
            BlockPos onShip = base.offset(7, 2, 7);
            BlockPos offShip = base.offset(12, 2, 2);
            h.assertValueEqual(Areas.at(level, inRoom), Optional.of(bridge), "the area in the room");
            h.assertValueEqual(
                    Areas.claims(level, inRoom),
                    List.of(
                            new AreaClaim(Areas.STORED, Areas.ROOM, bridge),
                            new AreaClaim(id("test_ship"), Areas.REGION, SHIP)),
                    "the claims in the room");
            h.assertValueEqual(Areas.at(level, onShip), Optional.of(SHIP), "the area on the ship outside the room");
            h.assertValueEqual(Areas.at(level, offShip), Optional.empty(), "the area off the ship");

            // A room drawn outside the ship answers on its own.
            store.assign(roomId, List.of(offShip));
            h.assertValueEqual(Areas.at(level, offShip), Optional.of(bridge), "the room's block off the ship");
            h.assertValueEqual(Areas.claims(level, offShip).size(), 1, "the claims off the ship");
        } finally {
            shipBox = null;
            store.remove(roomId);
        }
        h.assertValueEqual(Areas.at(level, base.offset(2, 2, 2)), Optional.empty(), "the area once both are gone");
        h.succeed();
    }

    /**
     * A block is in one stored area: assigning takes it from the other. Each change fires an event
     * over the blocks it changed, and a change that changes nothing fires none.
     */
    @GameTest(template = "empty")
    public static void stored_areas_share_no_block_and_say_what_changed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = spot(1);
        AreaStore store = Areas.store(level);
        ResourceLocation a = id("test/share_a");
        ResourceLocation b = id("test/share_b");
        try {
            store.define(a, "Engineering");
            store.define(b, "Atmospherics");
            h.assertTrue(throwsIllegalArgument(() -> store.define(a, "Again")), "defining an id twice wasn't refused");

            BoundingBox boxA = box(base, 0, 0, 0, 3, 1, 3);
            int mark = EVENTS.size();
            h.assertValueEqual(store.assign(a, boxA), 32, "the blocks put in A");
            assertEvent(h, level, mark, boxA, "assigning A");
            mark = EVENTS.size();
            h.assertValueEqual(store.assign(a, boxA), 0, "the blocks put in A again");
            h.assertValueEqual(eventsSince(level, mark, boxA), 0L, "events for a change of nothing");

            BoundingBox boxB = box(base, 2, 0, 2, 5, 1, 5);
            h.assertValueEqual(store.assign(b, boxB), 32, "the blocks put in B");
            h.assertValueEqual(store.size(a), 32 - 8, "A's blocks after B took its corner");
            h.assertValueEqual(store.size(b), 32, "B's blocks");
            h.assertValueEqual(store.at(base.offset(2, 0, 2)).map(Area::id), Optional.of(b), "the shared corner");
            h.assertValueEqual(store.bounds(a), Optional.of(boxA), "A's bounds");

            mark = EVENTS.size();
            Area renamed = store.rename(b, "Atmos");
            h.assertValueEqual(store.at(base.offset(4, 0, 4)), Optional.of(renamed), "B after it was renamed");
            assertEvent(h, level, mark, boxB, "renaming B");

            h.assertValueEqual(store.clear(box(base, 0, 0, 0, 0, 1, 0)), 2, "the blocks cleared");
            h.assertValueEqual(store.size(a), 22, "A's blocks after the clear");

            mark = EVENTS.size();
            h.assertTrue(store.remove(b), "B wasn't there to remove");
            h.assertValueEqual(store.at(base.offset(4, 0, 4)), Optional.empty(), "B's block after it was removed");
            h.assertValueEqual(store.get(b), Optional.empty(), "B after it was removed");
            assertEvent(h, level, mark, boxB, "removing B");
            h.assertTrue(!store.remove(b), "B was removed twice");
        } finally {
            store.remove(a);
            store.remove(b);
        }
        h.succeed();
    }

    /**
     * The issue's second test: a stored area survives a save and reload. Its level's data is saved,
     * then read back from the file by a storage of its own, as a restarted server would.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void a_stored_area_survives_a_save_and_reload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos base = spot(2);
        AreaStore store = Areas.store(level);
        ResourceLocation id = id("test/saved_medbay");
        Area medbay = store.define(id, "Medbay");
        // Not a box: two blocks far apart and a slab, across several sections.
        store.assign(id, box(base, 0, 0, 0, 20, 0, 5));
        store.assign(id, List.of(base.offset(-40, 3, 17), base.offset(100, -30, 0)));
        int size = store.size(id);
        h.assertValueEqual(size, 21 * 6 + 2, "the medbay's blocks");

        // What the store writes, it reads back.
        StoredAreas copy =
                StoredAreas.load(level, ((StoredAreas) store).save(new CompoundTag(), level.registryAccess()));
        assertSame(h, copy, id, medbay, size, base, "a copy read from what it wrote");

        level.getDataStorage().save();
        File folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                .resolve("data")
                .toFile();
        h.startSequence()
                .thenWaitUntil(() -> {
                    // The file is written off the server thread; read it until it holds the medbay.
                    DimensionDataStorage fresh =
                            new DimensionDataStorage(folder, server.getFixerUpper(), level.registryAccess());
                    StoredAreas reloaded = fresh.get(StoredAreas.factory(level), StoredAreas.NAME);
                    h.assertTrue(reloaded != null, "no " + StoredAreas.NAME + ".dat yet");
                    assertSame(h, reloaded, id, medbay, size, base, "the areas read from the saved file");
                })
                .thenExecute(() -> store.remove(id))
                .thenSucceed();
    }

    /**
     * Areas move with their blocks, turned: an area wholly inside the box goes, keeping its id and
     * name; one the box cuts through stays whole; one at the destination loses the blocks the moved
     * area arrives on. Then one goes to another level.
     */
    @GameTest(template = "empty")
    public static void stored_areas_move_with_their_blocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        BlockPos base = spot(3);
        AreaStore store = Areas.store(level);
        ResourceLocation cabin = id("test/move_cabin");
        ResourceLocation hangar = id("test/move_hangar");
        ResourceLocation pad = id("test/move_pad");
        try {
            store.define(cabin, "Cabin");
            store.define(hangar, "Hangar");
            store.define(pad, "Landing Pad");
            BoundingBox ship = box(base, 0, 0, 0, 4, 2, 4);
            // The hangar runs through the ship's box and out of it; the cabin is inside it.
            store.assign(hangar, box(base, 0, 0, -3, 0, 0, 3));
            store.assign(cabin, box(base, 1, 0, 1, 3, 1, 2));
            // The pad lies where the cabin lands.
            BlockPos pivot = base.offset(2, 0, 2);
            BlockPos target = base.offset(40, 0, 2);
            store.assign(pad, box(target, -5, 0, -5, 5, 0, 5));
            int padBefore = store.size(pad);

            int mark = EVENTS.size();
            h.assertValueEqual(
                    Areas.moved(level, ship, level, pivot, target, Rotation.CLOCKWISE_90), 1, "the areas moved");
            for (BlockPos p : BlockPos.betweenClosed(base.offset(1, 0, 1), base.offset(3, 1, 2))) {
                BlockPos arrived = Areas.map(p, pivot, target, Rotation.CLOCKWISE_90);
                h.assertValueEqual(
                        store.at(arrived).map(Area::id),
                        Optional.of(cabin),
                        "the cabin's block from " + p.toShortString());
                h.assertValueEqual(store.at(p), Optional.empty(), "the block the cabin left at " + p.toShortString());
            }
            h.assertValueEqual(store.size(cabin), 12, "the cabin's blocks");
            h.assertValueEqual(store.size(hangar), 7, "the hangar's blocks");
            h.assertValueEqual(
                    store.at(base.offset(0, 0, 1)).map(Area::id), Optional.of(hangar), "the hangar in the box");
            h.assertValueEqual(store.size(pad), padBefore - 6, "the pad's blocks under the cabin");
            assertEvent(h, level, mark, ship, "the move, where the blocks left");
            assertEvent(h, level, mark, new BoundingBox(target), "the move, where the blocks arrived");

            // Now on to the Nether, by an offset.
            BoundingBox landed = BoundingBox.fromCorners(
                    Areas.map(base.offset(1, 0, 1), pivot, target, Rotation.CLOCKWISE_90),
                    Areas.map(base.offset(3, 1, 2), pivot, target, Rotation.CLOCKWISE_90));
            BlockPos offset = new BlockPos(0, 0, 50);
            h.assertValueEqual(Areas.moved(level, landed, nether, offset), 1, "the areas moved to the Nether");
            h.assertValueEqual(store.get(cabin), Optional.empty(), "the cabin left behind in the Overworld");
            AreaStore there = Areas.store(nether);
            BlockPos corner = new BlockPos(landed.minX(), landed.minY(), landed.minZ()).offset(offset);
            h.assertValueEqual(there.at(corner).map(Area::name), Optional.of("Cabin"), "the cabin in the Nether");
            h.assertValueEqual(there.size(cabin), 12, "the cabin's blocks in the Nether");
        } finally {
            Areas.store(nether).remove(cabin);
            store.remove(cabin);
            store.remove(hangar);
            store.remove(pad);
        }
        h.succeed();
    }

    /** {@code /ss13 area} draws an area, shows it, lists it and removes it. */
    @GameTest(template = "empty")
    public static void the_admin_command_draws_and_shows_areas(GameTestHelper h) throws CommandSyntaxException {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos base = spot(4);
        CommandSourceStack source =
                server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        var dispatcher = server.getCommands().getDispatcher();
        ResourceLocation id = id("test_command_brig");
        String at = base.getX() + " " + base.getY() + " " + base.getZ();
        String far = (base.getX() + 2) + " " + (base.getY() + 1) + " " + (base.getZ() + 2);
        try {
            h.assertValueEqual(dispatcher.execute("ss13 area define test_command_brig The Brig", source), 1, "define");
            h.assertValueEqual(
                    Areas.store(level).get(id), Optional.of(new Area(id, "The Brig")), "the area the command defined");
            h.assertValueEqual(
                    dispatcher.execute("ss13 area assign test_command_brig " + at + " " + far, source), 1, "assign");
            h.assertValueEqual(Areas.store(level).size(id), 18, "the brig's blocks");
            h.assertValueEqual(dispatcher.execute("ss13 area at " + at, source), 1, "the claims at the brig");
            h.assertTrue(dispatcher.execute("ss13 area list", source) >= 1, "the list has no area");
            h.assertValueEqual(
                    dispatcher.execute("ss13 area define test_command_brig Again", source), 0, "defining it twice");
            h.assertValueEqual(dispatcher.execute("ss13 area remove test_command_brig", source), 1, "remove");
            h.assertValueEqual(Areas.store(level).get(id), Optional.empty(), "the brig once removed");
        } finally {
            Areas.store(level).remove(id);
        }
        h.succeed();
    }

    /**
     * {@code /ss13 area} and {@code /ss13 preset} share one {@code /ss13} root, and each needs
     * permission level 2: an operator can use both, a player with level 0 neither.
     */
    @GameTest(template = "empty")
    public static void area_and_preset_share_the_ss13_root(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var root = server.getCommands().getDispatcher().getRoot().getChild("ss13");
        h.assertTrue(root != null, "there is no /ss13");
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);
        CommandSourceStack player = server.createCommandSourceStack().withPermission(0);
        for (String sub : List.of("area", "preset")) {
            var node = root.getChild(sub);
            h.assertTrue(node != null, "there is no /ss13 " + sub);
            h.assertTrue(root.canUse(op) && node.canUse(op), "an operator can't use /ss13 " + sub);
            h.assertTrue(!(root.canUse(player) && node.canUse(player)), "a level 0 player can use /ss13 " + sub);
        }
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path);
    }

    /** A spot of the Overworld for one test, far from the tests' structures and from each other. */
    private static BlockPos spot(int test) {
        return new BlockPos(40_000 + 1_000 * test, 100, 40_000);
    }

    private static BoundingBox box(BlockPos base, int x0, int y0, int z0, int x1, int y1, int z1) {
        return BoundingBox.fromCorners(base.offset(x0, y0, z0), base.offset(x1, y1, z1));
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static long eventsSince(Level level, int mark, BoundingBox box) {
        return EVENTS.subList(mark, EVENTS.size()).stream()
                .filter(e -> e.level() == level && e.bounds().intersects(box))
                .count();
    }

    /** An event since {@code mark} covers the whole box. */
    private static void assertEvent(GameTestHelper h, Level level, int mark, BoundingBox box, String what) {
        boolean found = EVENTS.subList(mark, EVENTS.size()).stream()
                .anyMatch(e -> e.level() == level
                        && e.affects(new BlockPos(box.minX(), box.minY(), box.minZ()))
                        && e.affects(new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
        h.assertTrue(found, "no changed event covers " + box + " after " + what);
    }

    private static void assertSame(
            GameTestHelper h, AreaStore store, ResourceLocation id, Area area, int size, BlockPos base, String what) {
        h.assertValueEqual(store.get(id), Optional.of(area), what + ": the area");
        h.assertValueEqual(store.size(id), size, what + ": its blocks");
        h.assertValueEqual(store.at(base.offset(20, 0, 5)), Optional.of(area), what + ": a block of the slab");
        h.assertValueEqual(store.at(base.offset(100, -30, 0)), Optional.of(area), what + ": a lone block");
        h.assertValueEqual(store.at(base.offset(21, 0, 5)), Optional.empty(), what + ": a block beside the slab");
    }
}
