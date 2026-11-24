package io.github.west3436.ss13.tool;

import io.github.west3436.ss13.api.structure.Dismantling;
import io.github.west3436.ss13.api.structure.StructureTags;
import io.github.west3436.ss13.api.tool.ToolTags;
import io.github.west3436.ss13.api.tool.WeldingTool;
import io.github.west3436.ss13.registry.FrameworkComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The welder: use it in the air to light it or put it out. It holds {@link #CAPACITY} units of
 * fuel, crafted full and refilled by crafting it with coal or charcoal; while lit it burns a unit
 * every {@link #BURN_TICKS} ticks in a player's inventory, and goes out when it runs dry. A weld
 * ({@link WeldingTool#weld}, its capability) burns what the weld takes and, unless the welder wears
 * something in {@link ToolTags#WELDING_PROTECTION} on the head, blinds them a little longer each
 * time, up to {@link #MAX_BLIND_TICKS}.
 *
 * <p>Held on a wall ({@link StructureTags#WALLS}) while lit, it cuts the wall apart: after {@link
 * #WALL_TICKS} ticks for a wall, or {@link #REINFORCED_TICKS} for a reinforced one, it welds,
 * burning {@link #WALL_FUEL} or {@link #REINFORCED_FUEL} units, and takes the wall apart through
 * {@link Dismantling#takeApart}. Looking away or letting go stops it.
 */
public class WelderItem extends Item {
    /** The fuel a full welder holds, in units. */
    public static final int CAPACITY = 20;

    /** How often a lit welder burns a unit of fuel, in ticks: every 10 s. */
    public static final int BURN_TICKS = 200;

    /** How much longer each unprotected weld blinds the welder, in ticks. */
    public static final int BLIND_TICKS = 40;

    /** The longest blindness unprotected welds add up to, in ticks. */
    public static final int MAX_BLIND_TICKS = 200;

    /** How long cutting a wall apart takes, in ticks: 2 s. */
    public static final int WALL_TICKS = 40;

    /** How long cutting a reinforced wall apart takes, in ticks: 5 s. */
    public static final int REINFORCED_TICKS = 100;

    /** The fuel cutting a wall apart burns, in units. */
    public static final int WALL_FUEL = 1;

    /** The fuel cutting a reinforced wall apart burns, in units. */
    public static final int REINFORCED_FUEL = 2;

    public WelderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** On a wall, while lit and with the fuel for it: starts cutting it apart. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (player == null || !isLit(stack) || !state.is(StructureTags.WALLS)) {
            return InteractionResult.PASS;
        }
        if (fuel(stack) < fuelFor(state)) {
            if (!context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("item.ss13_framework.welder.short"), true);
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        BlockState wall = wall(entity);
        return wall != null && wall.is(StructureTags.REINFORCED_WALLS) ? REINFORCED_TICKS : WALL_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** Sparks at the wall while cutting; stops if the welder looks away or goes out. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        BlockHitResult hit = hit(entity);
        if (hit == null
                || !isLit(stack)
                || !level.getBlockState(hit.getBlockPos()).is(StructureTags.WALLS)) {
            entity.releaseUsingItem();
            return;
        }
        if (level.isClientSide) {
            Vec3 at = hit.getLocation();
            for (int i = 0; i < 2; i++) {
                level.addParticle(
                        ParticleTypes.ELECTRIC_SPARK,
                        at.x,
                        at.y,
                        at.z,
                        (level.random.nextDouble() - 0.5) * 0.3,
                        level.random.nextDouble() * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.3);
            }
        } else if (remaining % 10 == 0) {
            level.playSound(null, hit.getBlockPos(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.3F, 1.6F);
        }
    }

    /** Once the welder has held on long enough: welds and takes the wall apart. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        BlockHitResult hit = hit(entity);
        if (!(level instanceof ServerLevel server) || hit == null) {
            return stack;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = server.getBlockState(pos);
        int fuel = fuelFor(state);
        if (!state.is(StructureTags.WALLS) || !isLit(stack) || fuel(stack) < fuel) {
            return stack;
        }
        if (Dismantling.takeApart(server, pos, entity instanceof Player player ? player : null, stack)) {
            tool(stack).weld(entity, fuel);
        }
        return stack;
    }

    /** The fuel cutting a wall apart burns. */
    private static int fuelFor(BlockState state) {
        return state.is(StructureTags.REINFORCED_WALLS) ? REINFORCED_FUEL : WALL_FUEL;
    }

    /** The block a welder is aimed at, within its reach, if it is a player. */
    private static @Nullable BlockHitResult hit(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        BlockHitResult hit = getPlayerPOVHitResult(player.level(), player, ClipContext.Fluid.NONE);
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** The wall a welder is aimed at, if any. */
    private static @Nullable BlockState wall(LivingEntity entity) {
        BlockHitResult hit = hit(entity);
        if (hit == null) {
            return null;
        }
        BlockState state = entity.level().getBlockState(hit.getBlockPos());
        return state.is(StructureTags.WALLS) ? state : null;
    }

    /** Lights it, if it has fuel, or puts it out. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean light = !isLit(stack);
        if (light && fuel(stack) == 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("item.ss13_framework.welder.empty"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            stack.set(FrameworkComponents.LIT.get(), light);
            level.playSound(
                    null,
                    player.blockPosition(),
                    light ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS,
                    light ? 1.0F : 0.3F,
                    1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** A lit welder in a player's inventory burns its fuel. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && isLit(stack) && level.getGameTime() % BURN_TICKS == 0) {
            burn(stack, 1);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return fuel(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * fuel(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08F, 0.9F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.ss13_framework.welder.fuel", fuel(stack), CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        if (isLit(stack)) {
            lines.add(Component.translatable("item.ss13_framework.welder.lit").withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * The fuel a welder holds.
     *
     * @param stack the welder
     * @return the fuel, in units; a welder that never burned any is full
     */
    public static int fuel(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(FrameworkComponents.FUEL.get(), CAPACITY), 0, CAPACITY);
    }

    /**
     * Whether a welder is lit.
     *
     * @param stack the welder
     * @return true if it is lit
     */
    public static boolean isLit(ItemStack stack) {
        return stack.getOrDefault(FrameworkComponents.LIT.get(), false);
    }

    /**
     * Puts fuel into a welder, as a fuel tank would.
     *
     * @param stack the welder
     * @param units the most to put in, 0 or more
     * @return how much went in, which is less than {@code units} if it filled up
     */
    public static int refuel(ItemStack stack, int units) {
        int in = Math.min(Math.max(units, 0), CAPACITY - fuel(stack));
        stack.set(FrameworkComponents.FUEL.get(), fuel(stack) + in);
        return in;
    }

    /** Burns fuel; a welder that runs dry goes out. The caller has checked there's enough. */
    private static void burn(ItemStack stack, int units) {
        int left = Math.max(fuel(stack) - units, 0);
        stack.set(FrameworkComponents.FUEL.get(), left);
        if (left == 0) {
            stack.set(FrameworkComponents.LIT.get(), false);
        }
    }

    /**
     * The welding capability of a welder stack.
     *
     * @param stack the welder
     * @return its {@link WeldingTool}
     */
    public static WeldingTool tool(ItemStack stack) {
        return new Welder(stack);
    }

    /** A welder stack as a {@link WeldingTool}. */
    private record Welder(ItemStack stack) implements WeldingTool {
        @Override
        public boolean isLit() {
            return WelderItem.isLit(stack);
        }

        @Override
        public int fuel() {
            return WelderItem.fuel(stack);
        }

        @Override
        public int capacity() {
            return CAPACITY;
        }

        @Override
        public boolean weld(LivingEntity welder, int fuel) {
            if (fuel < 0) {
                throw new IllegalArgumentException("A weld can't take negative fuel: " + fuel);
            }
            if (!isLit() || fuel() < fuel) {
                return false;
            }
            burn(stack, fuel);
            if (!welder.getItemBySlot(EquipmentSlot.HEAD).is(ToolTags.WELDING_PROTECTION)) {
                MobEffectInstance blind = welder.getEffect(MobEffects.BLINDNESS);
                int ticks = Math.min((blind == null ? 0 : blind.getDuration()) + BLIND_TICKS, MAX_BLIND_TICKS);
                welder.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, true));
                if (welder instanceof Player player) {
                    player.displayClientMessage(Component.translatable("item.ss13_framework.welder.eyes"), true);
                }
            }
            return true;
        }
    }
}
