package me.jellysquid.mods.sodium.client.render.chunk.multidraw;

import me.jellysquid.mods.sodium.client.gl.SodiumVertexFormats;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import net.minecraft.util.ResourceLocation;

public abstract class ChunkRenderBackendMultiDraw<T extends ChunkGraphicsState> extends ChunkRenderShaderBackend<T, ChunkProgramMultiDraw> {
    public ChunkRenderBackendMultiDraw(GlVertexFormat<SodiumVertexFormats.ChunkMeshAttribute> format) {
        super(format);
    }

    @Override
    protected ChunkProgramMultiDraw createShaderProgram(ResourceLocation name, int handle, ChunkFogMode fogMode) {
        return new ChunkProgramMultiDraw(name, handle, fogMode.getFactory());
    }
}
