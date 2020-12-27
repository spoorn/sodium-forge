package me.jellysquid.mods.sodium.client.model.vertex;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.transformers.VertexTransformer;

public interface VertexSinkFactory<T extends VertexSink> {
    T createBufferWriter(VertexBufferView buffer, boolean direct);

    T createFallbackWriter(IVertexBuilder consumer);

    T createTransformingSink(T sink, VertexTransformer transformer);
}
