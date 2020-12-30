package me.jellysquid.mods.sodium.mixin.ai.task;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(Task.class)
public interface TaskAccessorMixin<E extends LivingEntity> {

    @Invoker("shouldContinueExecuting")
    boolean ishouldContinueExecuting(ServerWorld worldIn, E entityIn, long gameTimeIn);
}
