package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.util.MixinWorldRendererSodiumAccessor;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * We overwrite that method with a slightly lower priority so Flywheel's tail inject can succeed.
 */
@Mixin(value = WorldRenderer.class, priority = 999)
public class MixinCompatWorldRenderer {
    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void renderChunkLayer(RenderType renderLayer, MatrixStack matrixStack, double x, double y, double z) {
        RenderDevice.enterManagedCode();

        try {
            ((MixinWorldRendererSodiumAccessor)this).getRenderer().drawChunkLayer(renderLayer, matrixStack, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
}
