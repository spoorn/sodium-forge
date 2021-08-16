package me.jellysquid.mods.sodium.mixin.ai.nearby_entity_tracking;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public class WorldChunkMixin {

    @Shadow
    @Final
    private World level;

    @Shadow
    @Final
    private ChunkPos chunkPos;

    @Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInheritanceMultiMap;add(Ljava/lang/Object;)Z"))
    private void onEntityAdded(Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity) {
            EntityTrackerEngineProvider.getEntityTracker(this.level).onEntityAdded(entity.xChunk, entity.yChunk, entity.zChunk, (LivingEntity) entity);
        }
    }

    @Inject(method = "removeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInheritanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private void onEntityRemoved(Entity entity, int section, CallbackInfo ci) {
        if (entity instanceof LivingEntity) {
            EntityTrackerEngineProvider.getEntityTracker(this.level).onEntityRemoved(this.chunkPos.x, section, this.chunkPos.z, (LivingEntity) entity);
        }
    }
}
