package io.github.west3436.ss13.gravity;

import io.github.west3436.ss13.api.gravity.Gravity;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * {@link Gravity} on living entities, as its documentation describes: three transient modifiers,
 * all of operation {@code add_multiplied_total}, so a gravity {@code g} multiplies each attribute's
 * value:
 *
 * <ul>
 *   <li>{@link Gravity#MODIFIER_ID} on {@link Attributes#GRAVITY}, by {@code g}
 *   <li>{@link Gravity#SAFE_FALL_ID} on {@link Attributes#SAFE_FALL_DISTANCE}, by {@code 1 / g}: a
 *       jump goes {@code 1 / g} times as high, so landing from one never hurts
 *   <li>{@link Gravity#FALL_DAMAGE_ID} on {@link Attributes#FALL_DAMAGE_MULTIPLIER}, by {@code g}
 * </ul>
 *
 * <p>They are set as an entity joins a server level, again after every tick it moved in and once a
 * second whether it moved or not, and removed as it leaves the level. A gravity of 1 leaves none.
 * A modifier is only replaced when its amount changes, so an entity walking through a field
 * doesn't resend its attributes every tick. Things without attributes are scaled by {@code
 * EntityGravityMixin}.
 */
public final class GravityEntities {
    /**
     * The safe fall distance's largest multiple, for a gravity of 0 or nearly 0: past the
     * attribute's own limit, so such a fall never hurts.
     */
    private static final double MAX_SAFE_FALL_FACTOR = 1_000.0;

    /** How often a living entity that stays still is looked at again, in ticks. */
    private static final int REFRESH_TICKS = 20;

    private GravityEntities() {}

    /**
     * Listens on the game bus.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(GravityEntities::onJoin);
        gameBus.addListener(GravityEntities::onLeave);
        gameBus.addListener(GravityEntities::onTick);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            apply(living, Gravity.at(event.getLevel(), living.blockPosition()));
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            apply(living, Gravity.NORMAL);
        }
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        boolean moved = living.getX() != living.xo || living.getY() != living.yo || living.getZ() != living.zo;
        // Staggered by id, so the entities that stay still don't all ask in the same tick.
        if (moved || (living.tickCount + living.getId()) % REFRESH_TICKS == 0) {
            apply(living, Gravity.at(living.level(), living.blockPosition()));
        }
    }

    /**
     * Sets every living entity's modifiers again from the gravity where it is, after providers
     * have changed, such as after a {@code /reload} that changed a planet. Without it they catch up
     * within a second.
     *
     * @param server the server
     */
    public static void refresh(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) {
                    apply(living, Gravity.at(level, living.blockPosition()));
                }
            }
        }
    }

    /**
     * Sets an entity's modifiers for a gravity, or removes them for a gravity of 1.
     *
     * @param entity the entity
     * @param gravity the gravity, as a multiple of the Overworld's, 0 or more
     */
    public static void apply(LivingEntity entity, double gravity) {
        set(entity, Attributes.GRAVITY, Gravity.MODIFIER_ID, gravity);
        double safeFall = gravity > 1.0 / MAX_SAFE_FALL_FACTOR ? 1.0 / gravity : MAX_SAFE_FALL_FACTOR;
        set(entity, Attributes.SAFE_FALL_DISTANCE, Gravity.SAFE_FALL_ID, safeFall);
        set(entity, Attributes.FALL_DAMAGE_MULTIPLIER, Gravity.FALL_DAMAGE_ID, gravity);
    }

    /** Puts a modifier on an attribute that multiplies its value, or takes it off for a factor of 1. */
    private static void set(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (factor == 1.0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != factor - 1.0) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
