package io.github.west3436.ss13.api.construction;

import java.util.Objects;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * How many parts of one kind a board needs, such as two capacitors of any tier.
 *
 * @param kind the item tag the parts are in: usually a kind from {@link PartTags}, such as {@link
 *     PartTags#CAPACITORS}, whose parts have tiers; any other item tag also works, and its items
 *     count as tier 1
 * @param count how many, at least 1
 */
public record PartRequirement(TagKey<Item> kind, int count) {
    /**
     * Checks the requirement.
     *
     * @param kind the item tag the parts are in
     * @param count how many, at least 1
     * @throws IllegalArgumentException if the count is below 1
     */
    public PartRequirement {
        Objects.requireNonNull(kind, "kind");
        if (count < 1) {
            throw new IllegalArgumentException("a board needs at least one " + kind.location() + ", not " + count);
        }
    }

    /**
     * A requirement.
     *
     * @param kind the item tag the parts are in
     * @param count how many, at least 1
     * @return the requirement
     * @throws IllegalArgumentException if the count is below 1
     */
    public static PartRequirement of(TagKey<Item> kind, int count) {
        return new PartRequirement(kind, count);
    }

    /**
     * Whether a stack is a part of this kind. Reads item tags, so the tags must be loaded.
     *
     * @param stack the stack
     * @return true if it is
     */
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.is(kind);
    }
}
