package me.jellysquid.mods.sodium.client.model.vertex.formats.particle;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.VertexType;
import me.jellysquid.mods.sodium.client.model.vertex.VertexTypeBlittable;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.writer.ParticleVertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.writer.ParticleVertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.writer.ParticleVertexWriterFallback;
import net.minecraft.client.renderer.vertex.VertexFormat;

public class ParticleVertexType implements VertexType<ParticleVertexSink>, VertexTypeBlittable<ParticleVertexSink> {
    @Override
    public ParticleVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new ParticleVertexBufferWriterUnsafe(buffer) : new ParticleVertexBufferWriterNio(buffer);
    }

    @Override
    public ParticleVertexSink createFallbackWriter(IVertexBuilder consumer) {
        return new ParticleVertexWriterFallback(consumer);
    }

    @Override
    public VertexTypeBlittable<ParticleVertexSink> asBlittable() {
        return this;
    }

    @Override
    public VertexFormat getBufferVertexFormat() {
        return ParticleVertexSink.VERTEX_FORMAT;
    }
}
