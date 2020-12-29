package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.common.util.RenderTypeLookupUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the world from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkRenderRebuildTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {

    // 16x16x16
    private static final int CHUNK_BUILD_SIZE = 16;

    private final ChunkRenderContainer<T> render;
    private final ChunkBuilder<T> chunkBuilder;
    private final Vector3d camera;
    private final WorldSlice slice;
    private final BlockPos offset;

    public ChunkRenderRebuildTask(ChunkBuilder<T> chunkBuilder, ChunkRenderContainer<T> render, WorldSlice slice, BlockPos offset) {
        this.chunkBuilder = chunkBuilder;
        this.render = render;
        this.camera = chunkBuilder.getCameraPosition();
        this.slice = slice;
        this.offset = offset;
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderContext pipeline, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        VisGraph occluder = new VisGraph();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        buffers.init();
        pipeline.init(this.slice, this.slice.getOrigin());

        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos offset = this.offset;

        boolean shouldSortBackwards = false;

        for (int y = baseY; y < baseY + CHUNK_BUILD_SIZE; y++) {
            if (cancellationSource.isCancelled()) {
                return null;
            }
            for (int z = baseZ; z < baseZ + CHUNK_BUILD_SIZE; z++) {
                for (int x = baseX; x < baseX + CHUNK_BUILD_SIZE; x++) {
                    BlockState state = this.slice.getBlockState(x, y, z);

                    if (!shouldSortBackwards) {
                        for (BlockRenderPass pass : BlockRenderPass.TRANSLUCENTS) {
                            if (RenderTypeLookupUtil.canRenderInLayer(state, pass.getLayer())) {
                                shouldSortBackwards = true;
                                break;
                            }
                        }
                    }

                    setupBlockRender(pipeline, buffers, renderData, occluder, bounds, pos, offset, state, x, y, z,
                            baseX, baseY, baseZ);
                }
            }
        }

        render.setHasTranslucentBlocks(shouldSortBackwards);

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass, (float)camera.x - offset.getX(),
                    (float)camera.y - offset.getY(),
                    (float)camera.z - offset.getZ(), shouldSortBackwards);

            if (mesh != null) {
                renderData.setMesh(pass, mesh);
            }
        }

        renderData.setOcclusionData(occluder.computeVisibility());
        renderData.setBounds(bounds.build(this.render.getChunkPos()));

        return new ChunkBuildResult<>(this.render, renderData.build());
    }

    @Override
    public void releaseResources() {
        this.chunkBuilder.releaseWorldSlice(this.slice);
    }

    private void setupBlockRender(ChunkRenderContext pipeline, ChunkBuildBuffers buffers, ChunkRenderData.Builder renderData,
        VisGraph occluder, ChunkRenderBounds.Builder bounds, BlockPos.Mutable pos, BlockPos offset, BlockState blockState,
        int x, int y, int z, int baseX, int baseY, int baseZ) {
        if (blockState.isAir()) {
            return;
        }

        Block block = blockState.getBlock();

        pos.setPos(x, y, z);

        if (blockState.isOpaqueCube(this.slice, pos)) {
            occluder.setOpaqueCube(pos);
        }

        int boundsX = x-baseX, boundsY = y-baseY, boundsZ = z-baseZ;

        if (blockState.hasTileEntity()) {
            TileEntity entity = this.slice.getBlockEntity(pos, Chunk.CreateEntityType.CHECK);

            if (entity != null) {
                TileEntityRenderer<TileEntity> renderer = TileEntityRendererDispatcher.instance.getRenderer(entity);

                if (renderer != null) {
                    renderData.addBlockEntity(entity, !renderer.isGlobalRenderer(entity));

                    bounds.addBlock(boundsX, boundsY, boundsZ);
                }
            }
        }

        for (RenderType layer : RenderType.getBlockRenderTypes()) {
            ForgeHooksClient.setRenderLayer(layer);
            // Fluids
            FluidState fluidState = block.getFluidState(blockState);

            if (!fluidState.isEmpty() && RenderTypeLookupUtil.canRenderInLayer(fluidState, layer)) {
                buffers.setRenderOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

                if (pipeline.renderFluid(this.slice, fluidState, pos, buffers.get(layer))) {
                    bounds.addBlock(boundsX, boundsY, boundsZ);
                }
            }

            if (blockState.getRenderType() != BlockRenderType.MODEL || !RenderTypeLookupUtil.canRenderInLayer(blockState, layer)) {
                continue;
            }

            // Solid blocks
            buffers.setRenderOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

            if (pipeline.renderBlock(this.slice, blockState, pos, buffers.get(layer), true)) {
                bounds.addBlock(boundsX, boundsY, boundsZ);
            }
        }
        ForgeHooksClient.setRenderLayer(null);
    }
}
