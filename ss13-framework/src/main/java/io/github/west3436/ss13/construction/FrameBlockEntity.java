package io.github.west3436.ss13.construction;

import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.registry.FrameworkBlocks;
import io.github.west3436.ss13.registry.FrameworkItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What has gone into a frame: its board and parts. The wiring and screen are in its block state's
 * stage, so they aren't kept here.
 */
public final class FrameBlockEntity extends BlockEntity {
    private static final String CONTENTS = "Contents";

    private InstalledParts contents = InstalledParts.EMPTY;

    /** Set once the frame has become its machine, so its removal drops nothing. */
    private boolean built;

    public FrameBlockEntity(BlockPos pos, BlockState state) {
        super(FrameworkBlocks.FRAME.get(), pos, state);
    }

    /**
     * The board and parts in the frame.
     *
     * @return them
     */
    public InstalledParts contents() {
        return contents;
    }

    /**
     * Replaces the board and parts in the frame.
     *
     * @param contents the board and parts
     */
    public void setContents(InstalledParts contents) {
        this.contents = contents;
        setChanged();
    }

    /** Marks the frame built: its contents have moved into the machine, so nothing drops. */
    void markBuilt() {
        built = true;
        contents = InstalledParts.EMPTY;
    }

    /**
     * What breaking the frame drops besides the frame itself: its board, parts, and the cable and
     * screen its stage says it has.
     *
     * @param state the frame's state
     * @return the stacks
     */
    public List<ItemStack> drops(BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        if (built || !(state.getBlock() instanceof Frame frame)) {
            return drops;
        }
        if (Construction.isWired(frame, state.getValue(frame.stage()))) {
            drops.add(FrameworkItems.CABLE_COIL.toStack());
        }
        if (state.getValue(frame.stage()) == FrameStage.SCREEN) {
            drops.add(FrameworkItems.CONSOLE_SCREEN.toStack());
        }
        if (!contents.board().isEmpty()) {
            drops.add(contents.board());
        }
        drops.addAll(contents.parts());
        return drops;
    }

    /**
     * Drops what is in a frame as its block goes, unless it is only changing state or has become
     * its machine. A frame block calls this from its {@code onRemove}.
     *
     * @param state the frame's state
     * @param level the level
     * @param pos the frame's position
     * @param newState what replaces it
     */
    static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FrameBlockEntity frame) {
            for (ItemStack drop : frame.drops(state)) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!contents.isEmpty()) {
            tag.put(CONTENTS, contents.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents = InstalledParts.load(tag.get(CONTENTS), registries);
    }
}
