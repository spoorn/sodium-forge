package me.jellysquid.mods.sodium.mixin.features.particle.cull;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public class MixinParticleManager {
    @Shadow
    @Final
    private Map<ParticleRenderType, Queue<Particle>> particles;

    private final Queue<Particle> cachedQueue = new ArrayDeque<>();

    private Frustum cullingFrustum;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("HEAD"), remap = false)
    private void preRenderParticles(PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f, Frustum clippingHelper, CallbackInfo ci) {
        Frustum frustum = SodiumWorldRenderer.getInstance().getFrustum();
        boolean useCulling = SodiumClientMod.options().advanced.useParticleCulling;

        // Setup the frustum state before rendering particles
        if (useCulling && frustum != null) {
            this.cullingFrustum = frustum;
        } else {
            this.cullingFrustum = null;
        }
    }

    @SuppressWarnings({ "SuspiciousMethodCalls", "unchecked" })
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private <V> V filterParticleList(Map<ParticleRenderType, Queue<Particle>> map, Object key, PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f, Frustum clippingHelper) {
        Queue<Particle> queue = this.particles.get(key);

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // If the frustum isn't available (whether disabled or some other issue arose), simply return the queue as-is
        if (this.cullingFrustum == null) {
            return (V) queue;
        }

        // Filter particles which are not visible
        Queue<Particle> filtered = this.cachedQueue;
        filtered.clear();

        SodiumWorldRenderer worldRenderer = SodiumWorldRenderer.getInstance();

        for (Particle particle : queue) {
            AABB box = particle.getBoundingBox();

            // Hack: Grow the particle's bounding box in order to work around mis-behaved particles
            if (worldRenderer.isBoxVisible(box.minX - 1.0D, box.minY - 1.0D, box.minZ - 1.0D, box.maxX + 1.0D, box.maxY + 1.0D, box.maxZ + 1.0D)) {
                filtered.add(particle);
            }
        }

        return (V) filtered;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"), remap = false)
    private void postRenderParticles(PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f, CallbackInfo ci) {
        // Ensure particles don't linger in the temporary collection
        this.cachedQueue.clear();
    }
}
