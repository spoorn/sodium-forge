package me.jellysquid.mods.sodium.client.render.chunk.backends.gl20;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.gl.util.MemoryTracker;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.oneshot.ChunkRenderBackendOneshot;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/**
 * A simple chunk rendering backend which mirrors that of vanilla's own pretty closely.
 */
public class GL20ChunkRenderBackend extends ChunkRenderBackendOneshot<GL20GraphicsState> {
    public GL20ChunkRenderBackend(ChunkVertexType format) {
        super(GL20GraphicsState.class, format);
    }

    @Override
    public void begin(MatrixStack matrixStack, Matrix4f projection) {
        super.begin(matrixStack, projection);
        this.vertexFormat.enableVertexAttributes();
    }

    @Override
    public void end(MatrixStack matrixStack) {
        this.vertexFormat.disableVertexAttributes();
        GL20.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        super.end(matrixStack);
    }

    @Override
    protected GL20GraphicsState createGraphicsState(MemoryTracker memoryTracker, ChunkRenderContainer container, int id) {
        return new GL20GraphicsState(memoryTracker, container, id);
    }

    public static boolean isSupported(boolean disableBlacklist) {
        return true;
    }

    @Override
    public String getRendererName() {
        return "Oneshot (GL 2.0)";
    }
}
