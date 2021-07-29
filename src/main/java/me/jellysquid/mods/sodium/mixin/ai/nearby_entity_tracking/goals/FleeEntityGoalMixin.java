package me.jellysquid.mods.sodium.mixin.ai.nearby_entity_tracking.goals;

import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityListenerProvider;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityTracker;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(AvoidEntityGoal.class)
public class FleeEntityGoalMixin<T extends LivingEntity> {
    private NearbyEntityTracker<T> tracker;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;Ljava/util/function/Predicate;FDDLjava/util/function/Predicate;)V", at = @At("RETURN"))
    private void init(PathfinderMob entityIn, Class<T> avoidClass, Predicate<LivingEntity> targetPredicate, float distance, double nearSpeedIn, double farSpeedIn, Predicate<LivingEntity> predicate2, CallbackInfo ci) {
        this.tracker = new NearbyEntityTracker<>(avoidClass, entityIn, distance);

        ((NearbyEntityListenerProvider) entityIn).getListener().addListener(this.tracker);
    }

    @Redirect(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getNearestEntity(Ljava/util/List;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/LivingEntity;"))
    private T redirectGetNearestEntity(Level level, List<? extends T> p_45983_, TargetingConditions targetingConditions, LivingEntity entity, double x, double y, double z) {
        return this.tracker.getClosestEntity(box, targetPredicate);
    }
}
