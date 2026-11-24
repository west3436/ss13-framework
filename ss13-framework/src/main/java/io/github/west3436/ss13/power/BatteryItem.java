package io.github.west3436.ss13.power;

import io.github.west3436.ss13.registry.FrameworkComponents;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A battery, also the power cell of an SS13 machine: an item holding up to {@link #capacity()} FE in
 * its {@code ss13_framework:energy} component, which other mods reach through the FE item capability.
 * One to a stack, so each holds its own charge. Its durability bar and tooltip show the charge, and
 * its model the {@code ss13_framework:charge} property: 0 empty, 0.5 part charged, 1 full.
 */
public class BatteryItem extends Item {
    /** The bar's colour: vanilla's durability green. */
    private static final int BAR = 0x55FF55;

    private final int capacity;

    /**
     * Makes a battery.
     *
     * @param capacity the most FE it holds
     * @param properties its properties; it stacks to 1 whatever they say
     */
    public BatteryItem(int capacity, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
    }

    /**
     * The most FE it holds.
     *
     * @return FE
     */
    public int capacity() {
        return capacity;
    }

    /**
     * The FE a battery stack holds.
     *
     * @param stack the stack
     * @return FE, 0 to its capacity
     */
    public static int energy(ItemStack stack) {
        int stored = stack.getOrDefault(FrameworkComponents.ENERGY.get(), 0);
        return stack.getItem() instanceof BatteryItem b ? Math.max(0, Math.min(b.capacity, stored)) : stored;
    }

    /**
     * A new stack of this battery, charged full.
     *
     * @return the stack
     */
    public ItemStack full() {
        ItemStack stack = new ItemStack(this);
        stack.set(FrameworkComponents.ENERGY.get(), capacity);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.ss13_framework.battery.charge", fe(energy(stack)), fe(capacity)));
    }

    /**
     * FE with thousands separators, as windows show it.
     *
     * @param fe the FE
     * @return such as {@code 100,000}
     */
    public static String fe(long fe) {
        return String.format(Locale.ROOT, "%,d", fe);
    }

    /**
     * The charge as a fraction.
     *
     * @param stack the stack
     * @return 0 to 1; 0 for a stack that isn't a battery
     */
    public static float charge(ItemStack stack) {
        return stack.getItem() instanceof BatteryItem b ? Mth.clamp((float) energy(stack) / b.capacity, 0, 1) : 0;
    }
}
