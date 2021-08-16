package me.jellysquid.mods.sodium.mixin.features.entity.smooth_lighting;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.light.EntityLighter;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.entity.EntityLightSampler;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity> implements EntityLightSampler<T> {
    @Shadow
    protected abstract int getBlockLightLevel(T entity, BlockPos blockPos);

    @Shadow
    protected abstract int getSkyLightLevel(T entity, BlockPos blockPos);

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private void preGetLight(T entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        // Use smooth entity lighting if enabled
        if (SodiumClientMod.options().quality.smoothLighting == SodiumGameOptions.LightingQuality.HIGH) {
            cir.setReturnValue(EntityLighter.getBlendedLight(this, entity, tickDelta));
        }
    }

    @Inject(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelper;isVisible(Lnet/minecraft/util/math/AxisAlignedBB;)Z", shift = At.Shift.AFTER), cancellable = true)
    private void preShouldRender(T entity, ClippingHelper frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        // If the entity isn't culled already by other means, try to perform a second pass
        if (cir.getReturnValue() && !SodiumWorldRenderer.getInstance().isEntityVisible(entity)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public int bridge$getBlockLight(T entity, BlockPos pos) {
        return this.getBlockLightLevel(entity, pos);
    }

    @Override
    public int bridge$getSkyLight(T entity, BlockPos pos) {
        return this.getSkyLightLevel(entity, pos);
    }
}
