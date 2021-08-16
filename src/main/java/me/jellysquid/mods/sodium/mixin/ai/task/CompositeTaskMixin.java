package me.jellysquid.mods.sodium.mixin.ai.task;

import me.jellysquid.mods.lithium.common.ai.WeightedListIterable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.util.WeightedList;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(MultiTask.class)
public class CompositeTaskMixin<E extends LivingEntity> {
    @Shadow
    @Final
    private WeightedList<Task<? super E>> behaviors;

    @Shadow
    @Final
    private Set<MemoryModuleType<?>> exitErasedMemories;

    /**
     * @reason Replace stream code with traditional iteration
     * @author JellySquid
     */
    @Overwrite
    public boolean canStillUse(ServerWorld world, E entity, long time) {
        for (Task<? super E> task : WeightedListIterable.cast(this.behaviors)) {
            if (task.getStatus() == Task.Status.RUNNING) {
                if (((TaskAccessorMixin<E>)task).canWeStillUse(world, entity, time)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @reason Replace stream code with traditional iteration
     * @author JellySquid
     */
    @Overwrite
    public void tick(ServerWorld world, E entity, long time) {
        for (Task<? super E> task : WeightedListIterable.cast(this.behaviors)) {
            if (task.getStatus() == Task.Status.RUNNING) {
                task.tickOrStop(world, entity, time);
            }
        }
    }

    /**
     * @reason Replace stream code with traditional iteration
     * @author JellySquid
     */
    @Overwrite
    public void stop(ServerWorld world, E entity, long time) {
        for (Task<? super E> task : WeightedListIterable.cast(this.behaviors)) {
            if (task.getStatus() == Task.Status.RUNNING) {
                task.doStop(world, entity, time);
            }
        }

        Brain<?> brain = entity.getBrain();

        for (MemoryModuleType<?> module : this.exitErasedMemories) {
            brain.eraseMemory(module);
        }
    }
}
