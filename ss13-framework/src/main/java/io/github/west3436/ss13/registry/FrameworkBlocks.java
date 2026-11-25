package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.construction.ComputerFrameBlock;
import io.github.west3436.ss13.construction.FrameBlockEntity;
import io.github.west3436.ss13.construction.MachineFrameBlock;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's blocks: the machine and computer frames and their block entity.
 */
public final class FrameworkBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FrameworkApi.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FrameworkApi.MOD_ID);

    /** The machine frame; its item keeps the id ZAS³ aliases {@code zas_atmos:machine_frame} to. */
    public static final DeferredBlock<MachineFrameBlock> MACHINE_FRAME =
            BLOCKS.register("machine_frame", () -> new MachineFrameBlock(frameProperties()));

    /** The computer frame, a console 2 blocks wide, 2 tall and 1 deep. */
    public static final DeferredBlock<ComputerFrameBlock> COMPUTER_FRAME =
            BLOCKS.register("computer_frame", () -> new ComputerFrameBlock(frameProperties()));

    /** Both frames' block entity, which holds the board and parts put in so far. */
    public static final Supplier<BlockEntityType<FrameBlockEntity>> FRAME = BLOCK_ENTITIES.register(
            "frame",
            () -> BlockEntityType.Builder.of(FrameBlockEntity::new, MACHINE_FRAME.get(), COMPUTER_FRAME.get())
                    .build(null));

    private FrameworkBlocks() {}

    private static BlockBehaviour.Properties frameProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    /**
     * Registers the blocks and the block entity.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
