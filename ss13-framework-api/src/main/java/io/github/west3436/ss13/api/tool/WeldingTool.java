package io.github.west3436.ss13.api.tool;

import io.github.west3436.ss13.api.FrameworkApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.jetbrains.annotations.Nullable;

/**
 * A welder: a tool that welds while it is lit, burning fuel, and hurts the eyes of whoever welds
 * without protection. Offered as the item capability {@link #ITEM}; the framework's {@code
 * ss13_framework:welder} has it, and another mod's welder may too. A mod that welds, such as to
 * seal a door or repair a pipe, asks a stack for it and calls {@link #weld}.
 *
 * <p>Each answer reads the stack it came from, so it is current until the stack changes.
 */
public interface WeldingTool {
    /** {@code ss13_framework:welding_tool}: the capability, with no context. */
    ItemCapability<WeldingTool, @Nullable Void> ITEM = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, "welding_tool"), WeldingTool.class);

    /**
     * Whether it is lit, and so can weld.
     *
     * <p>Thread: any thread that may read the stack.
     *
     * @return true if it is lit
     */
    boolean isLit();

    /**
     * The fuel it holds, in units.
     *
     * <p>Thread: any thread that may read the stack.
     *
     * @return the fuel, from 0 to {@link #capacity()}
     */
    int fuel();

    /**
     * The most fuel it holds, in units.
     *
     * <p>Thread: any thread that may read the stack.
     *
     * @return the capacity
     */
    int capacity();

    /**
     * Welds: burns fuel, and unless the welder wears something in {@link
     * ToolTags#WELDING_PROTECTION} on the head, hurts their eyes. Does nothing if it is unlit or
     * holds less fuel than the weld takes.
     *
     * <p>Thread: server thread.
     *
     * @param welder who welds
     * @param fuel the fuel the weld takes, in units, 0 or more
     * @return true if it welded; false, changing nothing, if it is unlit or short of fuel
     * @throws IllegalArgumentException if the fuel is negative
     */
    boolean weld(LivingEntity welder, int fuel);
}
