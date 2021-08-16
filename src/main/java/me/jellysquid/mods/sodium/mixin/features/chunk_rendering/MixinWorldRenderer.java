package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import com.mojang.blaze3d.matrix.MatrixStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.util.MixinWorldRendererSodiumAccessor;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(value = WorldRenderer.class)
public abstract class MixinWorldRenderer implements MixinWorldRendererSodiumAccessor {
    @Shadow
    @Final
    private RenderTypeBuffers renderBuffers;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<DestroyBlockProgress>> destructionProgress;

    private SodiumWorldRenderer renderer;

    @Override
    public SodiumWorldRenderer getRenderer() {
        return renderer;
    }

    @Redirect(method = "allChanged", at = @At(value = "FIELD", target = "Lnet/minecraft/client/GameSettings;renderDistance:I", ordinal = 1))
    private int nullifyBuiltChunkStorage(GameSettings options) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft client, RenderTypeBuffers bufferBuilders, CallbackInfo ci) {
        this.renderer = SodiumWorldRenderer.create();
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void onWorldChanged(ClientWorld world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int countRenderedChunks() {
        return this.renderer.getVisibleChunkCount();
    }

    /**
     * @reason Redirect the check to our renderer
     * @author JellySquid
     */
    @Overwrite
    public boolean hasRenderedAllChunks() {
        return this.renderer.isTerrainRenderComplete();
    }

    @Inject(method = "needsUpdate", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }



    /**
     * Use our own renderer and pass in all necessary information.
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target="Lnet/minecraft/client/renderer/WorldRenderer;" +
        "setupRender(Lnet/minecraft/client/renderer/ActiveRenderInfo;Lnet/minecraft/client/renderer/culling/ClippingHelper;ZIZ)V"))
    private void setupTerrain(WorldRenderer worldRenderer, ActiveRenderInfo camera, ClippingHelper frustum, boolean hasForcedFrustum, int frame, boolean spectator,
    MatrixStack matrixStackIn, float partialTicks, long finishTimeNano, boolean drawBlockOutline, ActiveRenderInfo activeRenderInfoIn, GameRenderer gameRendererIn, LightTexture lightmapIn, Matrix4f projectionIn) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.updateChunks(camera, frustum, hasForcedFrustum, frame, spectator, projectionIn);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setSectionDirtyWithNeighbors(int x, int y, int z) {
        this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setSectionDirty(int x, int y, int z, boolean important) {
        this.renderer.scheduleRebuildForChunk(x, y, z, important);
    }

    @Inject(method = "allChanged", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/WorldRenderer;globalBlockEntities:Ljava/util/Set;", shift = At.Shift.BEFORE, ordinal = 0))
    private void onRenderTileEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, ActiveRenderInfo camera, GameRenderer gameRenderer, LightTexture lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
        this.renderer.renderTileEntities(matrices, this.renderBuffers, this.destructionProgress, camera, tickDelta);
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String getChunkStatistics() {
        return this.renderer.getChunksDebugString();
    }
}
