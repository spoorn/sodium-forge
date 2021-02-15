package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.function.Function;

/**
 * A forward-rendering shader program for chunks.
 */
public abstract class ChunkProgram extends GlProgram {
    // The model size of a chunk (16^3)

    // TODO: Pull over https://github.com/CaffeineMC/sodium-fabric/commit/2fb1d28aa61680170e44a138c296f3b5534314e0
    protected static final float MODEL_SIZE = 32.0f;

    protected static final float TEXTURE_SIZE = 1.0f;

    protected static final float CVF_MODEL_SIZE = (32.0f / 65536.0f);

    protected static final float CVF_TEXTURE_SIZE = (1.0f / 32768.0f);

    // Uniform variable binding indexes
    private final int uModelViewProjectionMatrix;
    private final int uModelScale;
    private final int uTextureScale;
    private final int uBlockTex;
    private final int uLightTex;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    protected ChunkProgram(ResourceLocation name, int handle, Function<ChunkProgram, ChunkShaderFogComponent> fogShaderFunction) {
        super(name, handle);

        this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");
        this.uBlockTex = this.getUniformLocation("u_BlockTex");
        this.uLightTex = this.getUniformLocation("u_LightTex");
        this.uModelScale = this.getUniformLocation("u_ModelScale");
        this.uTextureScale = this.getUniformLocation("u_TextureScale");

        this.fogShader = fogShaderFunction.apply(this);
    }

    public void setup(MatrixStack matrixStack, Matrix4f projection) {
        GL20.glUniform1i(this.uBlockTex, 0);
        GL20.glUniform1i(this.uLightTex, 2);

        if (SodiumClientMod.options().advanced.useCompactVertexFormat) {
            GL20.glUniform3f(this.uModelScale, CVF_MODEL_SIZE, CVF_MODEL_SIZE, CVF_MODEL_SIZE);
            GL20.glUniform2f(this.uTextureScale, CVF_TEXTURE_SIZE, CVF_TEXTURE_SIZE);
        } else {
            GL20.glUniform3f(this.uModelScale, MODEL_SIZE, MODEL_SIZE, MODEL_SIZE);
            GL20.glUniform2f(this.uTextureScale, TEXTURE_SIZE, TEXTURE_SIZE);
        }

        this.fogShader.setup();

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
}
