package me.jellysquid.mods.sodium.mixin.ai.task;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.memory.MemoryModuleStatus;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.FindInteractionAndLookTargetTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.util.math.EntityPosWrapper;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(FindInteractionAndLookTargetTask.class)
public abstract class FindInteractionTargetTaskMixin extends Task<LivingEntity> {
    @Shadow
    @Final
    private Predicate<LivingEntity> selfFilter;

    @Shadow
    protected abstract List<LivingEntity> getVisibleEntities(LivingEntity entity);

    @Shadow
    protected abstract boolean isMatchingTarget(LivingEntity entity);

    @Shadow
    @Final
    private int interactionRangeSqr;

    public FindInteractionTargetTaskMixin(Map<MemoryModuleType<?>, MemoryModuleStatus> memories) {
        super(memories);
    }

    /**
     * @reason Replace stream code with traditional iteration
     * @author JellySquid
     */
    @Overwrite
    public boolean checkExtraStartConditions(ServerWorld world, LivingEntity entity) {
        if (!this.selfFilter.test(entity)) {
            return false;
        }

        List<LivingEntity> visibleEntities = this.getVisibleEntities(entity);

        for (LivingEntity otherEntity : visibleEntities) {
            if (this.isMatchingTarget(otherEntity)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @reason Replace stream code with traditional iteration
     * @author JellySquid
     */
    @Overwrite
    public void start(ServerWorld world, LivingEntity entity, long time) {
        super.start(world, entity, time);

        Brain<?> brain = entity.getBrain();

        List<LivingEntity> visibleEntities = brain.getMemory(MemoryModuleType.VISIBLE_LIVING_ENTITIES)
                .orElse(Collections.emptyList());

        for (LivingEntity otherEntity : visibleEntities) {
            if (otherEntity.distanceToSqr(entity) > (double) this.interactionRangeSqr) {
                continue;
            }

            if (this.isMatchingTarget(otherEntity)) {
                brain.setMemory(MemoryModuleType.INTERACTION_TARGET, otherEntity);
                brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityPosWrapper(otherEntity, true));

                break;
            }
        }
    }

}
