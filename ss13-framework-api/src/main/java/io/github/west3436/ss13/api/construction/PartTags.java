package io.github.west3436.ss13.api.construction;

import io.github.west3436.ss13.api.FrameworkApi;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The stock parts' item tags, by kind and by tier, so a board, a recipe or a machine can ask for
 * "a capacitor of any tier" ({@code #ss13_framework:parts/capacitors}), "a tier-2 capacitor"
 * ({@code #ss13_framework:parts/capacitors/tier_2}) or "any tier-2 part" ({@code
 * #ss13_framework:parts/tier_2}) rather than for an item. Another mod's part joins by adding its
 * item to its kind's tier tag; the kind and tier tags take in the kind-and-tier tags, so it joins
 * those too.
 *
 * <ul>
 *   <li>{@code parts/<kind>/tier_<n>}: the parts of one kind and tier
 *   <li>{@code parts/<kind>}: every tier of a kind
 *   <li>{@code parts/tier_<n>}: every kind of a tier
 *   <li>{@code parts}: every stock part
 * </ul>
 *
 * <p>Every kind has tiers 1 to {@value #MAX_TIER}. Tier 1 is the basic part, the one a machine
 * crafted by its plain recipe holds.
 */
public final class PartTags {
    /** The highest tier a part has. */
    public static final int MAX_TIER = 4;

    /** Every stock part. */
    public static final TagKey<Item> PARTS = tag("parts");

    /**
     * Capacitors: {@code capacitor}, {@code advanced_capacitor}, {@code super_capacitor} and {@code
     * quadratic_capacitor}.
     */
    public static final TagKey<Item> CAPACITORS = tag("parts/capacitors");

    /**
     * Scanning modules: {@code scanning_module}, {@code advanced_scanning_module}, {@code
     * phasic_scanning_module} and {@code triphasic_scanning_module}.
     */
    public static final TagKey<Item> SCANNING_MODULES = tag("parts/scanning_modules");

    /**
     * Manipulators: {@code micro_manipulator}, {@code nano_manipulator}, {@code pico_manipulator}
     * and {@code femto_manipulator}.
     */
    public static final TagKey<Item> MANIPULATORS = tag("parts/manipulators");

    /**
     * Lasers: {@code micro_laser}, {@code high_power_micro_laser}, {@code
     * ultra_high_power_micro_laser} and {@code quad_ultra_micro_laser}.
     */
    public static final TagKey<Item> LASERS = tag("parts/lasers");

    /**
     * Matter bins: {@code matter_bin}, {@code advanced_matter_bin}, {@code super_matter_bin} and
     * {@code bluespace_matter_bin}.
     */
    public static final TagKey<Item> MATTER_BINS = tag("parts/matter_bins");

    private PartTags() {}

    /**
     * Every kind of stock part, in the order the parts are listed.
     *
     * @return the kinds' tags
     */
    public static List<TagKey<Item>> kinds() {
        return List.of(CAPACITORS, SCANNING_MODULES, MANIPULATORS, LASERS, MATTER_BINS);
    }

    /**
     * The parts of one kind and tier, such as {@code parts/capacitors/tier_2}.
     *
     * @param kind a kind's tag, such as {@link #CAPACITORS}
     * @param tier the tier, from 1 to {@value #MAX_TIER}
     * @return the tag {@code <kind>/tier_<tier>}
     * @throws IllegalArgumentException if the tier is out of range
     */
    public static TagKey<Item> tier(TagKey<Item> kind, int tier) {
        Objects.requireNonNull(kind, "kind");
        return TagKey.create(Registries.ITEM, kind.location().withSuffix("/tier_" + checkTier(tier)));
    }

    /**
     * Every kind of part of one tier, such as {@code parts/tier_2}.
     *
     * @param tier the tier, from 1 to {@value #MAX_TIER}
     * @return the tag {@code parts/tier_<tier>}
     * @throws IllegalArgumentException if the tier is out of range
     */
    public static TagKey<Item> tier(int tier) {
        return tag("parts/tier_" + checkTier(tier));
    }

    /**
     * A stack's tier as a part of a kind: the highest {@code n} whose {@code <kind>/tier_<n>} tag
     * holds it, or 1 if it is of the kind but in no tier tag. Reads item tags, so the tags must be
     * loaded.
     *
     * @param stack the stack
     * @param kind the kind, such as {@link #MANIPULATORS}, or any other item tag a board asks for
     * @return the tier, from 1 to {@value #MAX_TIER}, or 0 if the stack isn't of the kind
     */
    public static int tierOf(ItemStack stack, TagKey<Item> kind) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(kind, "kind");
        if (stack.isEmpty() || !stack.is(kind)) {
            return 0;
        }
        for (int tier = MAX_TIER; tier > 1; tier--) {
            if (stack.is(tier(kind, tier))) {
                return tier;
            }
        }
        return 1;
    }

    /**
     * A stack's tier as a part of any kind: the highest {@code n} whose {@code parts/tier_<n>} tag
     * holds it. Reads item tags, so the tags must be loaded.
     *
     * @param stack the stack
     * @return the tier, from 1 to {@value #MAX_TIER}, or 0 if it isn't a tiered part
     */
    public static int tierOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        for (int tier = MAX_TIER; tier >= 1; tier--) {
            if (stack.is(tier(tier))) {
                return tier;
            }
        }
        return 0;
    }

    private static int checkTier(int tier) {
        if (tier < 1 || tier > MAX_TIER) {
            throw new IllegalArgumentException("A part's tier is 1 to " + MAX_TIER + ", not " + tier);
        }
        return tier;
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path));
    }
}
