package io.github.west3436.ss13.mixin;

import io.github.west3436.ss13.api.gravity.Gravity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link Gravity} on the entities that have no gravity attribute: dropped items, falling blocks,
 * projectiles, minecarts, boats and the like fall with their usual gravity times the gravity at
 * their position. Living entities are left alone: their gravity is the attribute, which {@code
 * GravityEntities} modifies. It runs on both sides, each asking its own side's providers, so a
 * client's prediction of a falling item matches the server where the providers agree.
 */
@Mixin(Entity.class)
abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void ss13_framework$gravity(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity) {
            return;
        }
        double gravity = Gravity.at(self.level(), self.blockPosition());
        if (gravity != Gravity.NORMAL) {
            cir.setReturnValue(cir.getReturnValueD() * gravity);
        }
    }
}
