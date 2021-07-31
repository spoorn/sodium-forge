package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    private SodiumWorldRenderer renderer;

    @Redirect(method = "allChanged", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDistance:I", ordinal = 1), remap = false)
    private int nullifyBuiltChunkStorage(Options options) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft client, RenderBuffers bufferBuilders, CallbackInfo ci) {
        this.renderer = SodiumWorldRenderer.create();
    }

    @Inject(method = "setLevel", at = @At("RETURN"), remap = false)
    private void onWorldChanged(ClientLevel world, CallbackInfo ci) {
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
    @Overwrite(remap = false)
    public int countRenderedChunks() {
        return this.renderer.getVisibleChunkCount();
    }

    /**
     * @reason Redirect the check to our renderer
     * @author JellySquid
     */
    @Overwrite(remap = false)
    public boolean hasRenderedAllChunks() {
        return this.renderer.isTerrainRenderComplete();
    }

    @Inject(method = "needsUpdate", at = @At("RETURN"), remap = false)
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite(remap = false)
    private void renderChunkLayer(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, Matrix4f matrix4f) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.drawChunkLayer(renderLayer, matrixStack, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * Use our own renderer and pass in all necessary information.
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZIZ)V"), remap = false)
    private void setupTerrain(LevelRenderer worldRenderer, Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator,
                              PoseStack matrixStackIn, float partialTicks, long finishTimeNano, boolean drawBlockOutline, Camera activeRenderInfoIn, GameRenderer gameRendererIn, LightTexture lightmapIn, Matrix4f projectionIn) {
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
    @Overwrite(remap = false)
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite(remap = false)
    public void setSectionDirtyWithNeighbors(int x, int y, int z) {
        this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite(remap = false)
    private void setBlockDirty(BlockPos pos, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite(remap = false)
    private void setSectionDirty(int x, int y, int z, boolean important) {
        this.renderer.scheduleRebuildForChunk(x, y, z, important);
    }

    @Inject(method = "allChanged", at = @At("RETURN"), remap = false)
    private void onReload(CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;globalBlockEntities:Ljava/util/Set;", shift = At.Shift.BEFORE, ordinal = 0), remap = false)
    private void onRenderTileEntities(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
        this.renderer.renderTileEntities(matrices, this.renderBuffers, this.destructionProgress, camera, tickDelta);
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite(remap = false)
    public String getChunkStatistics() {
        return this.renderer.getChunksDebugString();
    }
}
