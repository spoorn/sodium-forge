package me.jellysquid.mods.sodium.client.model.vertex.formats.quad;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import me.jellysquid.mods.sodium.client.util.math.Matrix4fExtended;
import me.jellysquid.mods.sodium.client.util.math.MatrixUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public interface QuadVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = DefaultVertexFormats.ENTITY;

    void writeQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int normal);

    default void writeQuad(MatrixStack.Entry entry, float x, float y, float z, int color, float u, float v, int light, int overlay, int normal) {
        Matrix4fExtended modelMatrix = MatrixUtil.getExtendedMatrix(entry.getMatrix());

        float x2 = modelMatrix.transformVecX(x, y, z);
        float y2 = modelMatrix.transformVecY(x, y, z);
        float z2 = modelMatrix.transformVecZ(x, y, z);

        int norm = MatrixUtil.transformPackedNormal(normal, entry.getNormal());

        this.writeQuad(x2, y2, z2, color, u, v, light, overlay, norm);
    }
}
