package io.github.west3436.ss13.gametest;

import com.google.gson.JsonObject;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.tool.Linkable;
import io.github.west3436.ss13.api.tool.ToolTags;
import io.github.west3436.ss13.api.tool.WeldingTool;
import io.github.west3436.ss13.registry.FrameworkCreativeTab;
import io.github.west3436.ss13.registry.FrameworkItems;
import io.github.west3436.ss13.tool.MultitoolItem;
import io.github.west3436.ss13.tool.WelderItem;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The tools: their ids, tags and recipes, and what each does through its capability, on the test
 * mod's blocks ({@link TestTools}).
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolTests {
    /** The ids ZAS³ aliases its old {@code zas_atmos:} tool ids to; they must never change. */
    private static final List<String> IDS =
            List.of("multitool", "wrench", "crowbar", "screwdriver", "wirecutters", "welder");

    private static final List<TagKey<Item>> TAGS = List.of(
            ToolTags.MULTITOOLS,
            ToolTags.WRENCHES,
            ToolTags.CROWBARS,
            ToolTags.SCREWDRIVERS,
            ToolTags.WIRE_CUTTERS,
            ToolTags.WELDERS);

    private ToolTests() {}

    @GameTest(template = "empty")
    public static void tools_register_under_their_ids_with_their_tags_and_recipes(GameTestHelper h) {
        List<DeferredItem<? extends Item>> tools = FrameworkItems.tools();
        h.assertValueEqual(tools.size(), IDS.size(), "tools");
        ServerLevel level = h.getLevel();
        TagKey<Item> all = TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools"));
        for (int i = 0; i < IDS.size(); i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, IDS.get(i));
            Item item = tools.get(i).get();
            h.assertTrue(tools.get(i).getId().equals(id), "tool " + i + " isn't " + id);
            h.assertTrue(BuiltInRegistries.ITEM.get(id) == item, id + " isn't registered");
            ItemStack stack = new ItemStack(item);
            h.assertTrue(stack.is(TAGS.get(i)), id + " isn't in " + TAGS.get(i).location());
            h.assertTrue(stack.is(all), id + " isn't in c:tools");
            h.assertValueEqual(stack.getMaxStackSize(), 1, id + "'s stack size");
            h.assertTrue(level.getRecipeManager().byKey(id).isPresent(), "no recipe " + id);
        }
        h.assertTrue(
                level.getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "welder_refuel"))
                        .isPresent(),
                "no refuelling recipe");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_tools_have_models_textures_names_and_follow_the_components_in_the_tab(GameTestHelper h) {
        JsonObject lang = PartsTests.json(h, "/assets/ss13_framework/lang/en_us.json");
        for (String id : IDS) {
            h.assertTrue(lang.has("item.ss13_framework." + id), "no name for " + id);
            JsonObject model = PartsTests.json(h, "/assets/ss13_framework/models/item/" + id + ".json");
            String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
            h.assertValueEqual(texture, "ss13_framework:item/" + id, id + "'s texture");
            h.assertTrue(
                    ToolTests.class.getResource("/assets/ss13_framework/textures/item/" + id + ".png") != null,
                    "no texture for " + id);
        }
        h.assertTrue(
                ToolTests.class.getResource("/assets/ss13_framework/textures/item/welder_lit.png") != null,
                "no texture for a lit welder");

        ServerLevel level = h.getLevel();
        CreativeModeTab tab = FrameworkCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        List<Item> shown =
                tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        List<Item> tools =
                FrameworkItems.tools().stream().map(t -> (Item) t.get()).toList();
        int components = FrameworkItems.components().size();
        h.assertValueEqual(shown.subList(components, shown.size()), tools, "the tab after the components");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_wrench_turns_a_wrenchable_block_and_leaves_others_to_the_block(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.NORTH));
        Player player = holding(h, FrameworkItems.WRENCH.get());
        InteractionResult turned = useFirst(h, player, pos, Direction.UP, false);
        h.assertTrue(turned.consumesAction(), "the wrench didn't take the click: " + turned);
        h.assertBlockProperty(pos, ObserverBlock.FACING, Direction.EAST);

        BlockPos stone = new BlockPos(2, 2, 1);
        h.setBlock(stone, Blocks.STONE);
        h.assertValueEqual(useFirst(h, player, stone, Direction.UP, false), InteractionResult.PASS, "on stone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_wrench_removes_a_wrenchable_block_unless_it_refuses(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos power = new BlockPos(2, 2, 1);
        h.setBlock(pos, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.UP));
        h.setBlock(power, Blocks.REDSTONE_BLOCK);
        Player player = holding(h, FrameworkItems.WRENCH.get());

        useFirst(h, player, pos, Direction.UP, true);
        h.assertBlockPresent(Blocks.OBSERVER, pos);

        h.setBlock(power, Blocks.AIR);
        useFirst(h, player, pos, Direction.UP, true);
        h.assertBlockNotPresent(Blocks.OBSERVER, pos);
        h.assertItemEntityPresent(Items.OBSERVER, pos, 2.0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_crowbar_pries_a_pryable_block(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockState door = Blocks.IRON_DOOR.defaultBlockState();
        h.setBlock(pos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(pos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Player player = holding(h, FrameworkItems.CROWBAR.get());

        h.assertTrue(useOn(h, player, pos, Direction.NORTH).consumesAction(), "the crowbar didn't pry");
        h.assertBlockProperty(pos, DoorBlock.OPEN, true);
        useOn(h, player, pos, Direction.NORTH);
        h.assertBlockProperty(pos, DoorBlock.OPEN, false);

        BlockPos stone = new BlockPos(2, 2, 1);
        h.setBlock(stone, Blocks.STONE);
        h.assertValueEqual(useOn(h, player, stone, Direction.UP), InteractionResult.PASS, "on stone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_multitool_stores_a_source_and_links_a_target_through_linkable(GameTestHelper h) {
        BlockPos lectern = new BlockPos(1, 2, 1);
        BlockPos lamp = new BlockPos(3, 2, 1);
        BlockPos stone = new BlockPos(2, 2, 2);
        h.setBlock(lectern, Blocks.LECTERN);
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        h.setBlock(stone, Blocks.STONE);
        ServerLevel level = h.getLevel();
        BlockPos source = h.absolutePos(lectern);
        BlockPos target = h.absolutePos(lamp);
        TestTools.LINKS.remove(GlobalPos.of(level.dimension(), source));
        Player player = holding(h, FrameworkItems.MULTITOOL.get());
        ItemStack multitool = player.getMainHandItem();

        // Nothing stored: the block gets the click first, and after it the multitool says why.
        h.assertValueEqual(useFirst(h, player, lamp, Direction.UP, false), InteractionResult.PASS, "unselected");
        h.assertTrue(useOn(h, player, lamp, Direction.UP).consumesAction(), "no reason given");
        // Not linkable at all: the block's, before and after.
        h.assertValueEqual(useFirst(h, player, stone, Direction.UP, false), InteractionResult.PASS, "stone first");
        h.assertValueEqual(useOn(h, player, stone, Direction.UP), InteractionResult.PASS, "stone after");

        h.assertTrue(useFirst(h, player, lectern, Direction.UP, false).consumesAction(), "didn't select");
        h.assertValueEqual(MultitoolItem.source(multitool, level), Optional.of(source), "the buffer");
        // The source already stored leaves the click to the block.
        h.assertValueEqual(useFirst(h, player, lectern, Direction.UP, false), InteractionResult.PASS, "reselect");

        h.assertTrue(useFirst(h, player, lamp, Direction.UP, false).consumesAction(), "didn't link");
        h.assertValueEqual(links(level, source), Set.of(target), "links after linking");
        useFirst(h, player, lamp, Direction.UP, false);
        h.assertValueEqual(links(level, source), Set.of(), "links after unlinking");

        // A target the source refuses: the block gets the click first, and after it the source says why.
        BlockPos refused = new BlockPos(3, 2, 3);
        h.setBlock(refused, Blocks.TARGET);
        h.assertValueEqual(useFirst(h, player, refused, Direction.UP, false), InteractionResult.PASS, "refused");
        h.assertTrue(useOn(h, player, refused, Direction.UP).consumesAction(), "no refusal given");
        h.assertValueEqual(links(level, source), Set.of(), "links after a refusal");

        // A sneak-click stores another source.
        BlockPos second = new BlockPos(1, 2, 3);
        h.setBlock(second, Blocks.LECTERN);
        h.assertTrue(useFirst(h, player, second, Direction.UP, true).consumesAction(), "didn't select on a sneak");
        h.assertValueEqual(
                MultitoolItem.source(multitool, level), Optional.of(h.absolutePos(second)), "selected the second");

        player.setShiftKeyDown(true);
        multitool.use(level, player, InteractionHand.MAIN_HAND);
        h.assertValueEqual(MultitoolItem.source(multitool, level), Optional.empty(), "cleared");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void links_follow_their_source_when_it_moves(GameTestHelper h) {
        BlockPos lectern = new BlockPos(1, 2, 1);
        h.setBlock(lectern, Blocks.LECTERN);
        ServerLevel level = h.getLevel();
        BlockPos now = h.absolutePos(lectern);
        BlockPos was = now.east(5);
        BlockPos linked = was.north();
        TestTools.LINKS.remove(GlobalPos.of(level.dimension(), now));
        TestTools.LINKS.put(
                GlobalPos.of(level.dimension(), was), new java.util.HashSet<>(Set.of(linked, was.below(50))));

        // The box moved 5 west; a link to a block that stayed outside it is dropped.
        BoundingBox box = BoundingBox.fromCorners(now, now);
        BoundingBox before = BoundingBox.fromCorners(was.north(), was);
        int asked = Linkable.rebaseAll(level, box, p -> before.isInside(p) ? p.west(5) : null);
        h.assertValueEqual(asked, 1, "sources asked");
        h.assertValueEqual(links(level, now), Set.of(linked.west(5)), "links after the move");
        h.assertTrue(!TestTools.LINKS.containsKey(GlobalPos.of(level.dimension(), was)), "links left behind");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void a_zas_multitool_keeps_its_selected_controller(GameTestHelper h) {
        ItemStack stack = new ItemStack(FrameworkItems.MULTITOOL.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("FormatVersion", 1);
        tag.putLong("Controller", new BlockPos(10, 64, -3).asLong());
        tag.putString("Dimension", "minecraft:overworld");
        tag.putString("Other", "kept");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        h.assertValueEqual(
                MultitoolItem.stored(stack).map(GlobalPos::pos), Optional.of(new BlockPos(10, 64, -3)), "selected");

        MultitoolItem.store(stack, h.getLevel(), new BlockPos(1, 2, 3));
        CompoundTag left = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertValueEqual(left.getAllKeys(), Set.of("Other"), "custom data left");
        h.assertValueEqual(MultitoolItem.stored(stack).map(GlobalPos::pos), Optional.of(new BlockPos(1, 2, 3)), "new");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void the_welder_welds_only_while_lit_burning_fuel_and_eyes(GameTestHelper h) {
        Player player = holding(h, FrameworkItems.WELDER.get());
        ItemStack welder = player.getMainHandItem();
        WeldingTool tool = welder.getCapability(WeldingTool.ITEM);
        h.assertTrue(tool != null, "the welder isn't a welding tool");
        h.assertValueEqual(tool.fuel(), WelderItem.CAPACITY, "a new welder's fuel");
        h.assertTrue(!tool.weld(player, 1), "welded unlit");

        welder.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(tool.isLit(), "didn't light");
        h.assertTrue(!tool.weld(player, WelderItem.CAPACITY + 1), "welded short of fuel");
        h.assertTrue(tool.weld(player, 5), "didn't weld");
        h.assertValueEqual(tool.fuel(), WelderItem.CAPACITY - 5, "fuel after a weld");
        h.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "an unprotected weld didn't hurt the eyes");

        h.assertTrue(tool.weld(player, WelderItem.CAPACITY - 5), "didn't weld the rest");
        h.assertTrue(!tool.isLit(), "a dry welder stayed lit");
        welder.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(!tool.isLit(), "a dry welder lit");
        h.assertValueEqual(WelderItem.refuel(welder, 50), WelderItem.CAPACITY, "refuelled");
        h.succeed();
    }

    private static Set<BlockPos> links(ServerLevel level, BlockPos source) {
        return TestTools.LINKS.getOrDefault(GlobalPos.of(level.dimension(), source), Set.of());
    }

    /** A survival player holding a tool. */
    private static Player holding(GameTestHelper h, Item tool) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return player;
    }

    /** A click on a block's face, ahead of the block's own use, sneaking or not. */
    private static InteractionResult useFirst(
            GameTestHelper h, Player player, BlockPos rel, Direction face, boolean sneak) {
        player.setShiftKeyDown(sneak);
        return player.getMainHandItem().onItemUseFirst(context(h, player, rel, face));
    }

    /** A click on a block's face once the block's own use has passed. */
    private static InteractionResult useOn(GameTestHelper h, Player player, BlockPos rel, Direction face) {
        player.setShiftKeyDown(false);
        return player.getMainHandItem().useOn(context(h, player, rel, face));
    }

    private static UseOnContext context(GameTestHelper h, Player player, BlockPos rel, Direction face) {
        BlockPos pos = h.absolutePos(rel);
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }
}
