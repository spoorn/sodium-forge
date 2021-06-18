package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.texture.ChunkProgramTextureComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * A forward-rendering shader program for chunks.
 */
public abstract class ChunkProgram extends GlProgram {
    // The model size of a chunk (16^3)
    protected static final float MODEL_SIZE = 32.0f;

    // Uniform variable binding indexes
    private final int uModelViewProjectionMatrix;
    private final int uModelScale;

    public final ChunkProgramTextureComponent texture;
    public final ChunkShaderFogComponent fog;

    private final List<ShaderComponent> components;

    protected ChunkProgram(ResourceLocation name, int handle, ChunkProgramComponentBuilder components) {
        super(name, handle);

        this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");
        this.uModelScale = this.getUniformLocation("u_ModelScale");

        this.texture = components.texture.create(this);
        this.fog = components.fog.create(this);

        this.components = ImmutableList.of(this.texture, this.fog);
    }

    public void bind(MatrixStack matrixStack, Matrix4f projection) {
        super.bind(matrixStack);

        for (ShaderComponent component : this.components) {
            component.bind();
        }

        GL20.glUniform3f(this.uModelScale, MODEL_SIZE, MODEL_SIZE, MODEL_SIZE);

        MatrixStack.Entry matrices = matrixStack.getLast();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer bufModelViewProjection = stack.mallocFloat(16);

            Matrix4f modelView = matrices.getMatrix().copy();
            modelView.multiplyBackward(projection);
            modelView.write(bufModelViewProjection);
            GL20.glUniformMatrix4fv(this.uModelViewProjectionMatrix, false, bufModelViewProjection);
            // If for some reason vanilla minecraft doesn't expose the projection matrix anymore, we can fetch it
            // if it was pushed onto the GL stack with below code
            /*else {

            GL15.glGetFloatv(GL15.GL_PROJECTION_MATRIX, bufProjection);
            matrices.getMatrix().write(bufModelView);

            GL11.glPushMatrix();
            GL11.glLoadMatrixf(bufProjection);
            GL11.glMultMatrixf(bufModelView);
            GL15.glGetFloatv(GL15.GL_MODELVIEW_MATRIX, bufModelViewProjection);
            GL11.glPopMatrix();

            GL20.glUniformMatrix4fv(this.uModelViewProjectionMatrix, false, bufModelViewProjection);
            }*/
        }
    }

    @Override
    public void unbind() {
        super.unbind();

        for (ShaderComponent component : this.components) {
            component.unbind();
        }
    }

    @Override
    public void delete() {
        super.delete();

        for (ShaderComponent component : this.components) {
            component.delete();
        }
    }
}
