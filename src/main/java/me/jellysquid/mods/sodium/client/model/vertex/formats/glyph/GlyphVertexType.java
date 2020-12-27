package me.jellysquid.mods.sodium.client.model.vertex.formats.glyph;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.VertexType;
import me.jellysquid.mods.sodium.client.model.vertex.VertexTypeBlittable;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer.GlyphVertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer.GlyphVertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer.GlyphVertexWriterFallback;
import net.minecraft.client.renderer.vertex.VertexFormat;

public class GlyphVertexType implements VertexType<GlyphVertexSink>, VertexTypeBlittable<GlyphVertexSink> {
    @Override
    public GlyphVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new GlyphVertexBufferWriterUnsafe(buffer) : new GlyphVertexBufferWriterNio(buffer);
    }

    @Override
    public GlyphVertexSink createFallbackWriter(IVertexBuilder consumer) {
        return new GlyphVertexWriterFallback(consumer);
    }

    @Override
    public VertexFormat getBufferVertexFormat() {
        return GlyphVertexSink.VERTEX_FORMAT;
    }

    @Override
    public VertexTypeBlittable<GlyphVertexSink> asBlittable() {
        return this;
    }
}
