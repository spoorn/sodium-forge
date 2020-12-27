package me.jellysquid.mods.sodium.client.model.vertex;

import com.mojang.blaze3d.vertex.IVertexBuilder;

public interface VertexDrain {
    static VertexDrain of(IVertexBuilder consumer) {
        return (VertexDrain) consumer;
    }

    <T extends VertexSink> T createSink(VertexSinkFactory<T> factory);
}
