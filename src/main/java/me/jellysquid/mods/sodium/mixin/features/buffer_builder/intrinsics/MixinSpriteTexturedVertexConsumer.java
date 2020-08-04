package me.jellysquid.mods.sodium.mixin.features.buffer_builder.intrinsics;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.consumer.ParticleVertexConsumer;
import me.jellysquid.mods.sodium.client.model.consumer.QuadVertexConsumer;
import net.minecraft.client.renderer.SpriteAwareVertexBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteAwareVertexBuilder.class)
public abstract class MixinSpriteTexturedVertexConsumer implements QuadVertexConsumer, ParticleVertexConsumer {
    @Shadow
    @Final
    private IVertexBuilder vertexBuilder;

    @Shadow
    @Final
    private TextureAtlasSprite atlasSprite;

    @Override
    public void vertexQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int norm) {
        u = this.atlasSprite.getInterpolatedU(u * 16.0F);
        v = this.atlasSprite.getInterpolatedV(v * 16.0F);

        ((QuadVertexConsumer) this.vertexBuilder).vertexQuad(x, y, z, color, u, v, light, overlay, norm);
    }

    @Override
    public void vertexParticle(float x, float y, float z, float u, float v, int color, int light) {
        u = this.atlasSprite.getInterpolatedU(u * 16.0F);
        v = this.atlasSprite.getInterpolatedV(v * 16.0F);

        ((ParticleVertexConsumer) this.vertexBuilder).vertexParticle(x, y, z, u, v, color, light);
    }

}
