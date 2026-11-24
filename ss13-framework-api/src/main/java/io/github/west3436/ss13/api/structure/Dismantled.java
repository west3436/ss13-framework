package io.github.west3436.ss13.api.structure;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What taking a block apart leaves: the block in its place and what drops.
 *
 * @param left the state left where the block was, such as air, or a bare pipe where a wall held one
 * @param drops what drops, such as the wall's plating; may be empty
 */
public record Dismantled(BlockState left, List<ItemStack> drops) {
    /**
     * Creates the result.
     *
     * @param left the state left where the block was
     * @param drops what drops; copied
     */
    public Dismantled {
        Objects.requireNonNull(left, "left");
        drops = List.copyOf(drops);
    }
}
