package io.github.west3436.ss13.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.registry.FrameworkCreativeTab;
import io.github.west3436.ss13.registry.FrameworkItems;
import io.github.west3436.ss13.registry.PartTags;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/** The crafting components and stock parts: their ids, recipes, tags, tab and art. */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartsTests {
    /** The ids ZAS³ aliases its old {@code zas_atmos:} ids to; they must never change. */
    private static final List<String> IDS = List.of(
            "cable_coil",
            "circuit_board",
            "electronics",
            "machine_frame",
            "capacitor",
            "advanced_capacitor",
            "super_capacitor",
            "scanning_module",
            "micro_manipulator",
            "micro_laser",
            "console_screen",
            "igniter_assembly",
            "timer_assembly",
            "ansible_crystal",
            "subspace_ansible",
            "subspace_transmitter",
            "bluespace_crystal",
            "plasteel_sheet",
            "reinforced_glass");

    private PartsTests() {}

    @GameTest(template = "empty")
    public static void components_register_under_their_ids(GameTestHelper helper) {
        List<DeferredItem<Item>> components = FrameworkItems.components();
        helper.assertTrue(components.size() == IDS.size(), "expected " + IDS.size() + " components");
        for (int i = 0; i < IDS.size(); i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, IDS.get(i));
            helper.assertTrue(components.get(i).getId().equals(id), "component " + i + " isn't " + id);
            helper.assertTrue(
                    BuiltInRegistries.ITEM.get(id) == components.get(i).get(), id + " isn't registered");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void every_component_has_a_recipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DeferredItem<Item> component : FrameworkItems.components()) {
            Optional<RecipeHolder<?>> recipe = level.getRecipeManager().byKey(component.getId());
            helper.assertTrue(recipe.isPresent(), "no recipe " + component.getId());
            ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
            helper.assertTrue(result.is(component.get()), component.getId() + "'s recipe makes " + result);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipes_craft_from_vanilla_and_from_components(GameTestHelper helper) {
        ItemStack i = new ItemStack(Items.IRON_INGOT);
        ItemStack x = new ItemStack(Items.IRON_BARS);
        ItemStack e = ItemStack.EMPTY;
        assertCrafts(helper, CraftingInput.of(3, 3, List.of(i, x, i, x, e, x, i, x, i)), FrameworkItems.MACHINE_FRAME);
        assertCrafts(
                helper,
                CraftingInput.of(
                        2,
                        2,
                        List.of(
                                FrameworkItems.CIRCUIT_BOARD.toStack(),
                                new ItemStack(Items.REDSTONE_TORCH),
                                FrameworkItems.CABLE_COIL.toStack(),
                                ItemStack.EMPTY)),
                FrameworkItems.ELECTRONICS);
        assertCrafts(
                helper,
                CraftingInput.of(
                        2,
                        2,
                        List.of(
                                FrameworkItems.CAPACITOR.toStack(),
                                FrameworkItems.CAPACITOR.toStack(),
                                new ItemStack(Items.GOLD_INGOT),
                                new ItemStack(Items.REDSTONE))),
                FrameworkItems.ADVANCED_CAPACITOR);
        helper.succeed();
    }

    private static void assertCrafts(GameTestHelper helper, CraftingInput input, DeferredItem<Item> expected) {
        ServerLevel level = helper.getLevel();
        ItemStack result = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(r -> r.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(expected.get()), "expected " + expected.getId() + ", crafted " + result);
    }

    @GameTest(template = "empty")
    public static void parts_are_tagged_by_kind_and_tier(GameTestHelper helper) {
        assertTagged(helper, FrameworkItems.CAPACITOR, PartTags.tier(PartTags.CAPACITORS, 1), PartTags.tier(1));
        assertTagged(
                helper, FrameworkItems.ADVANCED_CAPACITOR, PartTags.tier(PartTags.CAPACITORS, 2), PartTags.tier(2));
        assertTagged(helper, FrameworkItems.SUPER_CAPACITOR, PartTags.tier(PartTags.CAPACITORS, 3), PartTags.tier(3));
        assertTagged(helper, FrameworkItems.SCANNING_MODULE, PartTags.tier(PartTags.SCANNING_MODULES, 1));
        assertTagged(helper, FrameworkItems.MICRO_MANIPULATOR, PartTags.tier(PartTags.MANIPULATORS, 1));
        assertTagged(helper, FrameworkItems.MICRO_LASER, PartTags.tier(PartTags.LASERS, 1));
        for (DeferredItem<Item> capacitor :
                List.of(FrameworkItems.CAPACITOR, FrameworkItems.ADVANCED_CAPACITOR, FrameworkItems.SUPER_CAPACITOR)) {
            assertTagged(helper, capacitor, PartTags.CAPACITORS, PartTags.PARTS);
        }
        assertTagged(helper, FrameworkItems.SCANNING_MODULE, PartTags.SCANNING_MODULES, PartTags.tier(1));
        assertTagged(helper, FrameworkItems.MICRO_MANIPULATOR, PartTags.MANIPULATORS, PartTags.tier(1));
        assertTagged(helper, FrameworkItems.MICRO_LASER, PartTags.LASERS, PartTags.PARTS);

        ItemStack capacitor = FrameworkItems.CAPACITOR.toStack();
        helper.assertFalse(capacitor.is(PartTags.tier(PartTags.CAPACITORS, 2)), "a capacitor is tier 2");
        helper.assertFalse(capacitor.is(PartTags.tier(2)), "a capacitor is a tier-2 part");
        helper.assertFalse(capacitor.is(PartTags.LASERS), "a capacitor is a laser");
        helper.assertFalse(FrameworkItems.MACHINE_FRAME.toStack().is(PartTags.PARTS), "a machine frame is a part");
        helper.succeed();
    }

    @SafeVarargs
    private static void assertTagged(GameTestHelper helper, DeferredItem<Item> item, TagKey<Item>... tags) {
        for (TagKey<Item> tag : tags) {
            helper.assertTrue(item.toStack().is(tag), item.getId() + " isn't in #" + tag.location());
        }
    }

    @GameTest(template = "empty")
    public static void creative_tab_lists_every_component(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTab tab = FrameworkCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        List<Item> shown =
                tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        List<Item> components =
                FrameworkItems.components().stream().map(DeferredItem::get).toList();
        helper.assertTrue(shown.equals(components), "the tab shows " + shown);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void every_component_has_a_model_texture_and_name(GameTestHelper helper) {
        JsonObject lang = json(helper, "/assets/ss13_framework/lang/en_us.json");
        for (String id : IDS) {
            helper.assertTrue(lang.has("item.ss13_framework." + id), "no name for " + id);
            JsonObject model = json(helper, "/assets/ss13_framework/models/item/" + id + ".json");
            String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
            helper.assertTrue(texture.equals("ss13_framework:item/" + id), id + "'s model shows " + texture);
            String png = "/assets/ss13_framework/textures/item/" + id + ".png";
            try (InputStream in = PartsTests.class.getResourceAsStream(png)) {
                helper.assertTrue(in != null, "no texture for " + id);
            } catch (IOException e) {
                throw new IllegalStateException("can't read " + png, e);
            }
        }
        helper.assertTrue(lang.has("itemGroup.ss13_framework"), "no name for the creative tab");
        helper.succeed();
    }

    private static JsonObject json(GameTestHelper helper, String path) {
        try (InputStream in = PartsTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "no " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("can't read " + path, e);
        }
    }
}
