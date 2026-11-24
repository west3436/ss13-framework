package io.github.west3436.ss13.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.construction.InstalledParts;
import io.github.west3436.ss13.construction.ComputerFrameBlock;
import io.github.west3436.ss13.construction.FrameBlockEntity;
import io.github.west3436.ss13.construction.MachineFrameBlock;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's blocks: the machine and computer frames, their block entity, and the data
 * component a mined machine's item keeps its board and parts in.
 */
public final class FrameworkBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FrameworkApi.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FrameworkApi.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FrameworkApi.MOD_ID);

    /** The machine frame; its item keeps the id ZAS³ aliases {@code zas_atmos:machine_frame} to. */
    public static final DeferredBlock<MachineFrameBlock> MACHINE_FRAME =
            BLOCKS.register("machine_frame", () -> new MachineFrameBlock(frameProperties()));

    /** The computer frame. */
    public static final DeferredBlock<ComputerFrameBlock> COMPUTER_FRAME =
            BLOCKS.register("computer_frame", () -> new ComputerFrameBlock(frameProperties()));

    /** Both frames' block entity, which holds the board and parts put in so far. */
    public static final Supplier<BlockEntityType<FrameBlockEntity>> FRAME = BLOCK_ENTITIES.register(
            "frame",
            () -> BlockEntityType.Builder.of(FrameBlockEntity::new, MACHINE_FRAME.get(), COMPUTER_FRAME.get())
                    .build(null));

    /** The board and parts a mined machine's item carries, so placing it puts them back. */
    public static final Supplier<DataComponentType<InstalledParts>> INSTALLED_PARTS = COMPONENTS.registerComponentType(
            "installed_parts",
            b -> b.persistent(InstalledParts.CODEC).networkSynchronized(InstalledParts.STREAM_CODEC));

    private FrameworkBlocks() {}

    private static BlockBehaviour.Properties frameProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    /**
     * Registers the blocks, the block entity and the data component.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
    }
}
