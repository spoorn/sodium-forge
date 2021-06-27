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
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.client.model.ModelDataManager;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;

import java.util.Objects;

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
    private static final int CHUNK_BUILD_SIZE_2D = CHUNK_BUILD_SIZE * CHUNK_BUILD_SIZE;
    private static final int TOTAL_CHUNK_SIZE = CHUNK_BUILD_SIZE * CHUNK_BUILD_SIZE * CHUNK_BUILD_SIZE;

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

        pipeline.init(this.slice, this.slice.getOrigin());
        buffers.init(renderData);

        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos offset = this.offset;

        boolean shouldSortBackwards = false;

        // TODO: Since we're not sorting here anymore, no need to loop through coordinates twice
        BlockState[] blockStates = new BlockState[TOTAL_CHUNK_SIZE];
        Coordinate[] coordinates = new Coordinate[TOTAL_CHUNK_SIZE];
        for (int y = baseY; y < baseY + CHUNK_BUILD_SIZE; y++) {
            for (int z = baseZ; z < baseZ + CHUNK_BUILD_SIZE; z++) {
                for (int x = baseX; x < baseX + CHUNK_BUILD_SIZE; x++) {
                    BlockState curr = this.slice.getOriginBlockState(x-baseX, y-baseY, z-baseZ);
                    int index = (x-baseX)+((y-baseY)*CHUNK_BUILD_SIZE)+((z-baseZ)*CHUNK_BUILD_SIZE_2D);
                    blockStates[index] = curr;
                    coordinates[index] = new Coordinate(x, y, z);
                    for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                        if (!shouldSortBackwards && pass.isTranslucent()
                                && RenderTypeLookupUtil.canRenderInLayer(curr, pass.getLayer())) {
                            shouldSortBackwards = true;
                        }
                    }
                }
            }
        }

        // If cancelled, stop before doing more calculations
        if (cancellationSource.isCancelled()) {
            return null;
        }

        if (shouldSortBackwards) {
            render.setHasTranslucentBlocks(true);
        } else {
            render.setHasTranslucentBlocks(false);
        }

        for (Coordinate coordinate : coordinates) {
            if (cancellationSource.isCancelled()) {
                return null;
            }
            setupBlockRender(pipeline, buffers, renderData, occluder, bounds, pos, offset, blockStates,
                    coordinate.x, coordinate.y, coordinate.z, baseX, baseY, baseZ);
        }

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass, (float)camera.x - offset.getX(),
                    (float)camera.y - offset.getY(),
                    (float)camera.z - offset.getZ());

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
        VisGraph occluder, ChunkRenderBounds.Builder bounds, BlockPos.Mutable pos, BlockPos offset, BlockState[] blockStates,
        int x, int y, int z, int minX, int minY, int minZ) {
        BlockState blockState = blockStates[(x-minX)+((y-minY)*CHUNK_BUILD_SIZE)+((z-minZ)*CHUNK_BUILD_SIZE_2D)];
        Block block = blockState.getBlock();

        if (blockState.isAir()) {
            return;
        }

        pos.setPos(x, y, z);

        if (blockState.isOpaqueCube(this.slice, pos)) {
            occluder.setOpaqueCube(pos);
        }

        if (blockState.hasTileEntity()) {
            TileEntity entity = this.slice.getBlockEntity(pos, Chunk.CreateEntityType.CHECK);

            if (entity != null) {
                TileEntityRenderer<TileEntity> renderer = TileEntityRendererDispatcher.instance.getRenderer(entity);

                if (renderer != null) {
                    renderData.addBlockEntity(entity, !renderer.isGlobalRenderer(entity));

                    bounds.addBlock(x, y, z);
                }
            }
        }

        for (RenderType layer : RenderType.getBlockRenderTypes()) {
            ForgeHooksClient.setRenderLayer(layer);
            // Fluids
            FluidState fluidState = block.getFluidState(blockState);

            if (!fluidState.isEmpty() && RenderTypeLookupUtil.canRenderInLayer(fluidState, layer)) {
                ChunkBuildBuffers.ChunkBuildBufferDelegate builder2 = buffers.get(layer);
                builder2.setOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

                if (pipeline.renderFluid(this.slice, fluidState, pos, builder2)) {
                    bounds.addBlock(x, y, z);
                }
            }

            if (blockState.getRenderType() != BlockRenderType.MODEL || !RenderTypeLookupUtil.canRenderInLayer(blockState, layer)) {
                continue;
            }

            // Solid blocks
            ChunkBuildBuffers.ChunkBuildBufferDelegate builder = buffers.get(layer);
            builder.setOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

            IModelData modelData = ModelDataManager.getModelData(Objects.requireNonNull(Minecraft.getInstance().world), pos);
            if (modelData == null) {
                modelData = EmptyModelData.INSTANCE;
            }
            if (pipeline.renderBlock(this.slice, blockState, pos, builder, true, modelData)) {
                bounds.addBlock(x, y, z);
            }
        }
        ForgeHooksClient.setRenderLayer(null);
    }

    public static class Coordinate {
        public int x, y, z;

        public Coordinate(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
