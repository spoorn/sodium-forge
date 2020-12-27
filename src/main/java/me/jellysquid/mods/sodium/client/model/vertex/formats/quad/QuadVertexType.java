package me.jellysquid.mods.sodium.client.model.vertex.formats.quad;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.VertexType;
import me.jellysquid.mods.sodium.client.model.vertex.VertexTypeBlittable;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer.QuadVertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer.QuadVertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer.QuadVertexWriterFallback;
import net.minecraft.client.renderer.vertex.VertexFormat;

public class QuadVertexType implements VertexType<QuadVertexSink>, VertexTypeBlittable<QuadVertexSink> {
    @Override
    public QuadVertexSink createFallbackWriter(IVertexBuilder consumer) {
        return new QuadVertexWriterFallback(consumer);
    }

    @Override
    public QuadVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new QuadVertexBufferWriterUnsafe(buffer) : new QuadVertexBufferWriterNio(buffer);
    }

    @Override
    public VertexFormat getBufferVertexFormat() {
        return QuadVertexSink.VERTEX_FORMAT;
    }

    @Override
    public VertexTypeBlittable<QuadVertexSink> asBlittable() {
        return this;
    }
}
