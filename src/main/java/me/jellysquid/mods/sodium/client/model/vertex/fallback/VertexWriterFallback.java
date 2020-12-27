package me.jellysquid.mods.sodium.client.model.vertex.fallback;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;

public abstract class VertexWriterFallback implements VertexSink {
    protected final IVertexBuilder consumer;

    protected VertexWriterFallback(IVertexBuilder consumer) {
        this.consumer = consumer;
    }

    @Override
    public void ensureCapacity(int count) {
        // NO-OP
    }

    @Override
    public void flush() {
        // NO-OP
    }
}
