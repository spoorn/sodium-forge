package me.jellysquid.mods.sodium.client.render.chunk.passes;

import lombok.Getter;
import net.minecraft.client.renderer.RenderType;

// TODO: Move away from using an enum, make this extensible
public enum BlockRenderPass {
    SOLID(RenderType.solid(), false),
    CUTOUT(RenderType.cutout(), false),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), false),
    TRANSLUCENT(RenderType.translucent(), true),
    TRIPWIRE(RenderType.tripwire(), true);

    public static final BlockRenderPass[] VALUES = BlockRenderPass.values();
    public static final BlockRenderPass[] TRANSLUCENTS = new BlockRenderPass[2];
    public static final int COUNT = VALUES.length;

    static {
        TRANSLUCENTS[0] = TRANSLUCENT;
        TRANSLUCENTS[1] = TRIPWIRE;
    }

    @Getter
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
