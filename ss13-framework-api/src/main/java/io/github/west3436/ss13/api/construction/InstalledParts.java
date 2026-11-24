package io.github.west3436.ss13.api.construction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The board and parts inside a built machine, and the stats-from-parts queries a machine reads to
 * set its stats, such as "the sum of my manipulators' tiers".
 *
 * <p>Each part is one item. The queries read item tags, so they must be called once tags are
 * loaded: in a block entity's {@code onLoad} or later, never while it is read from disk.
 *
 * <p>{@link #EMPTY} is a machine with no board or parts, as one put down by {@code /setblock} or a
 * structure is. A machine should treat it as holding tier-1 parts: {@code Math.max(1,
 * parts.lowestTier(kind))}.
 *
 * @param board the board, or {@link ItemStack#EMPTY} if there is none
 * @param parts the parts, one item each, in the order they went in
 */
public record InstalledParts(ItemStack board, List<ItemStack> parts) {
    /** No board and no parts. */
    public static final InstalledParts EMPTY = new InstalledParts(ItemStack.EMPTY, List.of());

    /** Saves the board and parts, as {@code {board, parts}}; both may be left out when empty. */
    public static final Codec<InstalledParts> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ItemStack.OPTIONAL_CODEC
                            .optionalFieldOf("board", ItemStack.EMPTY)
                            .forGetter(InstalledParts::board),
                    ItemStack.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(InstalledParts::parts))
            .apply(i, InstalledParts::new));

    /** Sends the board and parts over the network, as in an item's data component. */
    public static final StreamCodec<RegistryFriendlyByteBuf, InstalledParts> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC,
            InstalledParts::board,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
            InstalledParts::parts,
            InstalledParts::new);

    /**
     * Copies the board and parts, so later changes to the stacks given don't reach this.
     *
     * @param board the board, or {@link ItemStack#EMPTY}
     * @param parts the parts; each is taken as one item, and empty stacks are left out
     */
    public InstalledParts {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(parts, "parts");
        board = board.isEmpty() ? ItemStack.EMPTY : board.copyWithCount(1);
        List<ItemStack> copies = new ArrayList<>(parts.size());
        for (ItemStack part : parts) {
            if (!part.isEmpty()) {
                copies.add(part.copyWithCount(1));
            }
        }
        parts = List.copyOf(copies);
    }

    /**
     * Whether this has neither a board nor parts.
     *
     * @return true if it has neither
     */
    public boolean isEmpty() {
        return board.isEmpty() && parts.isEmpty();
    }

    /**
     * The board, as a copy the caller may change.
     *
     * @return the board, or {@link ItemStack#EMPTY}
     */
    @Override
    public ItemStack board() {
        return board.copy();
    }

    /**
     * The parts, as copies the caller may change.
     *
     * @return the parts, one item each
     */
    @Override
    public List<ItemStack> parts() {
        return parts.stream().map(ItemStack::copy).toList();
    }

    /**
     * Whether another holds the same board and the same parts in the same order, item and
     * components alike, as an item's data component must be compared.
     *
     * @param other the other
     * @return true if it does
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InstalledParts that)
                || !ItemStack.isSameItemSameComponents(board, that.board)
                || parts.size() != that.parts.size()) {
            return false;
        }
        for (int i = 0; i < parts.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(parts.get(i), that.parts.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A hash of the board's and parts' items and components, consistent with {@link #equals}.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int hash = ItemStack.hashItemAndComponents(board);
        for (ItemStack part : parts) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(part);
        }
        return hash;
    }

    /**
     * The same board with one part replaced.
     *
     * @param index the part's index in {@link #parts()}
     * @param part the new part, one item
     * @return the new parts
     * @throws IndexOutOfBoundsException if there is no part at the index
     */
    public InstalledParts withPart(int index, ItemStack part) {
        Objects.requireNonNull(part, "part");
        List<ItemStack> next = new ArrayList<>(parts);
        next.set(index, part);
        return new InstalledParts(board, next);
    }

    /**
     * How many parts of a kind this holds.
     *
     * @param kind the kind, such as {@link PartTags#MANIPULATORS}
     * @return the count
     */
    public int count(TagKey<Item> kind) {
        int n = 0;
        for (ItemStack part : parts) {
            if (part.is(kind)) {
                n++;
            }
        }
        return n;
    }

    /**
     * The sum of the tiers of the parts of a kind: SS13's "rating" of, for instance, a machine's
     * manipulators.
     *
     * @param kind the kind
     * @return the sum, 0 if there are none
     */
    public int totalTier(TagKey<Item> kind) {
        int sum = 0;
        for (ItemStack part : parts) {
            sum += PartTags.tierOf(part, kind);
        }
        return sum;
    }

    /**
     * The lowest tier among the parts of a kind.
     *
     * @param kind the kind
     * @return the tier, 0 if there are none
     */
    public int lowestTier(TagKey<Item> kind) {
        int lowest = 0;
        for (ItemStack part : parts) {
            int tier = PartTags.tierOf(part, kind);
            if (tier > 0 && (lowest == 0 || tier < lowest)) {
                lowest = tier;
            }
        }
        return lowest;
    }

    /**
     * The highest tier among the parts of a kind.
     *
     * @param kind the kind
     * @return the tier, 0 if there are none
     */
    public int highestTier(TagKey<Item> kind) {
        int highest = 0;
        for (ItemStack part : parts) {
            highest = Math.max(highest, PartTags.tierOf(part, kind));
        }
        return highest;
    }

    /**
     * The mean tier of the parts of a kind.
     *
     * @param kind the kind
     * @return the mean, 0 if there are none
     */
    public double averageTier(TagKey<Item> kind) {
        int n = count(kind);
        return n == 0 ? 0 : (double) totalTier(kind) / n;
    }

    /**
     * Saves this as NBT, for a block entity's {@code saveAdditional}.
     *
     * @param registries the registries, as {@code saveAdditional} is given them
     * @return the tag
     */
    public Tag save(HolderLookup.Provider registries) {
        return CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .getOrThrow();
    }

    /**
     * Reads what {@link #save} wrote, for a block entity's {@code loadAdditional}.
     *
     * @param tag the tag, or null if there is none
     * @param registries the registries, as {@code loadAdditional} is given them
     * @return the parts, or {@link #EMPTY} if the tag is null or can't be read, as when an item in it
     *     no longer exists
     */
    public static InstalledParts load(Tag tag, HolderLookup.Provider registries) {
        if (tag == null) {
            return EMPTY;
        }
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(EMPTY);
    }
}
