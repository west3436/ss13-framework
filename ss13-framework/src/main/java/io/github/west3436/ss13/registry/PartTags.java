package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The stock parts' item tags, by kind and by tier, so a recipe or a machine can ask for "a tier-2
 * capacitor" ({@code #ss13_framework:parts/capacitors/tier_2}) or "any tier-2 part" ({@code
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
 * <p>Tier 1 is the basic part. Capacitors have tiers 1 to 3 so far; the other kinds have tier 1.
 */
public final class PartTags {
    /** Every stock part. */
    public static final TagKey<Item> PARTS = tag("parts");

    /** Capacitors: {@code capacitor}, {@code advanced_capacitor} and {@code super_capacitor}. */
    public static final TagKey<Item> CAPACITORS = tag("parts/capacitors");

    /** Scanning modules: {@code scanning_module}. */
    public static final TagKey<Item> SCANNING_MODULES = tag("parts/scanning_modules");

    /** Manipulators: {@code micro_manipulator}. */
    public static final TagKey<Item> MANIPULATORS = tag("parts/manipulators");

    /** Lasers: {@code micro_laser}. */
    public static final TagKey<Item> LASERS = tag("parts/lasers");

    private PartTags() {}

    /**
     * The parts of one kind and tier, such as {@code parts/capacitors/tier_2}.
     *
     * @param kind a kind's tag, such as {@link #CAPACITORS}
     * @param tier the tier, from 1
     * @return the tag {@code <kind>/tier_<tier>}
     */
    public static TagKey<Item> tier(TagKey<Item> kind, int tier) {
        return TagKey.create(Registries.ITEM, kind.location().withSuffix("/tier_" + checkTier(tier)));
    }

    /**
     * Every kind of part of one tier, such as {@code parts/tier_2}.
     *
     * @param tier the tier, from 1
     * @return the tag {@code parts/tier_<tier>}
     */
    public static TagKey<Item> tier(int tier) {
        return tag("parts/tier_" + checkTier(tier));
    }

    private static int checkTier(int tier) {
        if (tier < 1) {
            throw new IllegalArgumentException("A part's tier starts at 1, not " + tier);
        }
        return tier;
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path));
    }
}
