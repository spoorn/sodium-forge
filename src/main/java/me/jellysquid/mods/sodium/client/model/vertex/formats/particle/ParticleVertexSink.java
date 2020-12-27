package me.jellysquid.mods.sodium.client.model.vertex.formats.particle;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public interface ParticleVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP;

    void writeParticle(float x, float y, float z, float u, float v, int color, int light);
}
