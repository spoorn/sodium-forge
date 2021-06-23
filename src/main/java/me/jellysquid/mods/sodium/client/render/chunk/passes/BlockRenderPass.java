package me.jellysquid.mods.sodium.client.render.chunk.passes;

import net.minecraft.client.renderer.RenderType;

// TODO: Move away from using an enum, make this extensible
public enum BlockRenderPass {
    SOLID(RenderType.getSolid(), false),
    CUTOUT(RenderType.getCutout(), false),
    CUTOUT_MIPPED(RenderType.getCutoutMipped(), false),
    TRANSLUCENT(RenderType.getTranslucent(), true),
    TRIPWIRE(RenderType.getTripwire(), true);

    // TODO: Array just for translucent values
    public static final BlockRenderPass[] VALUES = BlockRenderPass.values();
    public static final int COUNT = VALUES.length;

    private final RenderType layer;
    private final boolean translucent;

    BlockRenderPass(RenderType layer, boolean translucent) {
        this.layer = layer;
        this.translucent = translucent;
    }

    public final boolean isTranslucent() {
        return this.translucent;
    }

    public void endDrawing() {
        this.layer.clearRenderState();
    }

    public void startDrawing() {
        this.layer.setupRenderState();
    }
}
