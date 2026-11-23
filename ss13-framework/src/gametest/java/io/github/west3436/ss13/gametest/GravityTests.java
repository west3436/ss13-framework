package io.github.west3436.ss13.gametest;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.gravity.Gravity;
import io.github.west3436.ss13.api.gravity.GravityEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Gravity at a position, from test providers registered as this class loads, before any test runs:
 *
 * <ul>
 *   <li>{@code ss13_framework:test_half}, a field that halves gravity inside a box in the Overworld,
 *       off until a test gives it a box;
 *   <li>{@code ss13_framework:test_zero_g}, a dimension provider that sets the End's gravity to 0,
 *       standing in for Space;
 *   <li>{@code ss13_framework:test_deck}, a field that sets 1 g inside a box in the End, standing in
 *       for deck gravity or a gravity generator in Space.
 * </ul>
 *
 * <p>Each box belongs to one test, so tests that run at once don't meet.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityTests {
    private static final double EPSILON = 1e-9;

    /** A pig's gravity attribute with no modifier. */
    private static final double PIG_GRAVITY = 0.08;

    /** Far from the End's main island, high above anything that generates. */
    private static final BlockPos END_SPOT = new BlockPos(10_008, 120, 8);

    private static volatile BoundingBox halfBox;
    private static volatile BoundingBox deckBox;

    static {
        Gravity.register(
                id("test_half"),
                Gravity.FIELD,
                (level, pos) ->
                        inside(halfBox, level, Level.OVERWORLD, pos) ? GravityEffect.scale(0.5) : GravityEffect.NONE);
        Gravity.register(
                id("test_zero_g"),
                Gravity.DIMENSION,
                (level, pos) -> level.dimension() == Level.END ? GravityEffect.set(0) : GravityEffect.NONE);
        Gravity.register(
                id("test_deck"),
                Gravity.FIELD,
                (level, pos) -> inside(deckBox, level, Level.END, pos) ? GravityEffect.set(1) : GravityEffect.NONE);
    }

    private GravityTests() {}

    // ---- The tests ----------------------------------------------------------------------

    /**
     * A field that halves gravity: a pig standing where it switches on picks up the modifiers within
     * a second, a dropped item inside falls at half its gravity, and the pig loses them as soon as
     * it's moved out.
     */
    @GameTest(template = "empty")
    public static void a_field_halves_gravity_inside_its_box(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // Both spots are in the test's own chunk, forced for the test, so the pig ticks wherever it
        // is: the test's structure is one block, and the chunks around it needn't be loaded.
        ChunkPos chunk = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        int y = h.absolutePos(BlockPos.ZERO).getY() + 2;
        BlockPos inside = new BlockPos(chunk.getMinBlockX() + 3, y, chunk.getMinBlockZ() + 3);
        BlockPos outside = new BlockPos(chunk.getMaxBlockX() - 3, y, chunk.getMaxBlockZ() - 3);
        level.setChunkForced(chunk.x, chunk.z, true);
        Pig pig = pigIn(h, level, inside);
        assertNoModifiers(h, pig, "the pig before the field is on");

        halfBox = BoundingBox.fromCorners(inside.offset(-1, -2, -1), inside.offset(1, 2, 1));
        h.startSequence()
                .thenWaitUntil(() -> {
                    AttributeModifier modifier =
                            pig.getAttribute(Attributes.GRAVITY).getModifier(Gravity.MODIFIER_ID);
                    h.assertTrue(modifier != null, "the pig in the field has no gravity modifier yet");
                })
                .thenExecute(() -> {
                    near(h, Gravity.at(level, inside), 0.5, "the gravity inside the box");
                    near(h, Gravity.at(level, outside), Gravity.NORMAL, "the gravity outside the box");
                    AttributeModifier modifier =
                            pig.getAttribute(Attributes.GRAVITY).getModifier(Gravity.MODIFIER_ID);
                    h.assertValueEqual(
                            modifier.operation(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "its operation");
                    near(h, modifier.amount(), 0.5 - 1, "its amount");
                    near(h, pig.getGravity(), PIG_GRAVITY * 0.5, "the pig's gravity");
                    near(h, pig.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 3 / 0.5, "its safe fall");
                    near(h, pig.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5, "its fall damage");

                    ItemEntity in = item(level, inside);
                    ItemEntity out = item(level, outside);
                    near(h, in.getGravity(), out.getGravity() * 0.5, "an item's gravity in the field");
                    h.assertTrue(out.getGravity() > 0, "an item outside the field has no gravity");

                    pig.teleportTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
                })
                .thenWaitUntil(() -> assertNoModifiers(h, pig, "the pig moved out of the field"))
                .thenExecute(() -> {
                    halfBox = null;
                    pig.discard();
                    level.setChunkForced(chunk.x, chunk.z, false);
                })
                .thenSucceed();
    }

    /**
     * In a level of 0 g, a field that sets 1 g: inside it a pig carries no modifiers and an item
     * falls as in the Overworld; outside it the pig's gravity is 0, its falls never hurt, and the
     * item doesn't fall at all.
     */
    @GameTest(template = "empty")
    public static void a_setting_field_gives_1_g_in_a_0_g_level(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        BlockPos inside = END_SPOT;
        BlockPos outside = END_SPOT.offset(0, 0, 8);
        end.getChunk(inside);
        end.getChunk(outside);
        deckBox = BoundingBox.fromCorners(inside.offset(-2, -2, -2), inside.offset(2, 2, 2));
        Pig in = null;
        Pig out = null;
        try {
            near(h, Gravity.at(end, outside), 0.0, "the End's gravity");
            near(h, Gravity.at(end, inside), 1.0, "the gravity on the deck");

            in = pigIn(h, end, inside);
            out = pigIn(h, end, outside);
            assertNoModifiers(h, in, "the pig on the deck");
            near(h, in.getGravity(), PIG_GRAVITY, "the pig on the deck's gravity");
            AttributeModifier modifier = out.getAttribute(Attributes.GRAVITY).getModifier(Gravity.MODIFIER_ID);
            h.assertTrue(modifier != null, "the pig off the deck has no gravity modifier");
            near(h, modifier.amount(), -1.0, "its amount");
            near(h, out.getGravity(), 0.0, "the pig off the deck's gravity");
            h.assertTrue(
                    out.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) >= 1_000,
                    "a fall in 0 g could hurt: safe fall " + out.getAttributeValue(Attributes.SAFE_FALL_DISTANCE));

            ItemEntity onDeck = item(end, inside);
            ItemEntity overworld = item(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 1)));
            near(h, onDeck.getGravity(), overworld.getGravity(), "an item's gravity on the deck");
            near(h, item(end, outside).getGravity(), 0.0, "an item's gravity off the deck");
        } finally {
            deckBox = null;
            if (in != null) in.discard();
            if (out != null) out.discard();
        }
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path);
    }

    private static boolean inside(BoundingBox box, Level level, ResourceKey<Level> dimension, BlockPos pos) {
        return box != null && level.dimension() == dimension && box.isInside(pos);
    }

    /** A pig without AI, added to a level as a spawned one is, so it joins with its gravity. */
    private static Pig pigIn(GameTestHelper h, ServerLevel level, BlockPos at) {
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) {
            throw new GameTestAssertException("no pig");
        }
        pig.moveTo(Vec3.atBottomCenterOf(at));
        pig.setNoAi(true);
        h.assertTrue(
                level.addFreshEntity(pig),
                "the pig wasn't added to " + level.dimension().location());
        return pig;
    }

    /** An item entity at a position, never added to the level: its gravity is all that's asked. */
    private static ItemEntity item(ServerLevel level, BlockPos at) {
        return new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.STONE));
    }

    private static void assertNoModifiers(GameTestHelper h, LivingEntity entity, String what) {
        assertNone(h, entity, Attributes.GRAVITY, Gravity.MODIFIER_ID, what);
        assertNone(h, entity, Attributes.SAFE_FALL_DISTANCE, Gravity.SAFE_FALL_ID, what);
        assertNone(h, entity, Attributes.FALL_DAMAGE_MULTIPLIER, Gravity.FALL_DAMAGE_ID, what);
    }

    private static void assertNone(
            GameTestHelper h, LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, String what) {
        h.assertTrue(
                entity.getAttribute(attribute).getModifier(id) == null,
                what + " carries " + id + " on " + attribute.getRegisteredName());
    }

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < EPSILON, what + " is " + actual + ", not " + expected);
    }
}
