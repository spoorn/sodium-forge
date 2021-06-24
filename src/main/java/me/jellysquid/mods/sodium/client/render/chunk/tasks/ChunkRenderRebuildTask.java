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
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ModelDataManager;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;

import java.util.Arrays;
import java.util.Comparator;
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
    private final Comparator<Coordinate> coordinateComparator;

    public ChunkRenderRebuildTask(ChunkBuilder<T> chunkBuilder, ChunkRenderContainer<T> render, WorldSlice slice, BlockPos offset) {
        this.chunkBuilder = chunkBuilder;
        this.render = render;
        this.camera = chunkBuilder.getCameraPosition();
        this.slice = slice;
        this.offset = offset;
        this.coordinateComparator = (Coordinate a, Coordinate b) -> {
            double distA = sqDistanceToCamera(a);
            double distB = sqDistanceToCamera(b);
            // Reverse so further coordinates are rendered first
            return Double.compare(distB, distA);
        };
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderContext pipeline, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        VisGraph occluder = new VisGraph();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        pipeline.init(this.slice, this.slice.getBlockOffsetX(), this.slice.getBlockOffsetY(), this.slice.getBlockOffsetZ());
        buffers.init(renderData);

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos offset = this.offset;

        boolean shouldSortBackwards = false;

        BlockState[] blockStates = new BlockState[TOTAL_CHUNK_SIZE];
        Coordinate[] coordinates = new Coordinate[TOTAL_CHUNK_SIZE];
        for (int y = minY; y < minY + CHUNK_BUILD_SIZE; y++) {
            for (int z = minZ; z < minZ + CHUNK_BUILD_SIZE; z++) {
                for (int x = minX; x < minX + CHUNK_BUILD_SIZE; x++) {
                    BlockState curr = this.slice.getBlockState(x, y, z);
                    int index = (x-minX)+((y-minY)*CHUNK_BUILD_SIZE)+((z-minZ)*CHUNK_BUILD_SIZE_2D);
                    blockStates[index] = curr;
                    coordinates[index] = new Coordinate(x, y, z);
                    // TODO: Only sort the translucent blocks, instead of entire chunk
                    if (!curr.isAir() && curr.getFluidState().isEmpty() && !curr.isOpaqueCube(slice, new BlockPos(x, y, z))) {
                        shouldSortBackwards = true;
                    }
                }
            }
        }

        // If cancelled, stop before doing more calculations
        if (cancellationSource.isCancelled()) {
            return null;
        }

        // Sort coordinates so we render further coordinates before closer ones.  Ideally this should be done only for
        // translucent quads themselves, but for now we make do with the whole block coordinate.
        // Sorting here isn't perfect as it's not actually sorting the individual vertices, but just the
        // (central?) coordinate of the quad.  But at least it looks better than before.
        // This mostly fixes things like translucent stained blocks behind each other, but fluids within stained glass
        // still looks a bit odd.  Seems like we run into the problem where the fluid behind a glass block can overlap
        // and be in front of the glass block.  May be because again, we aren't working with individual vertices.
        if (shouldSortBackwards) {
            Arrays.sort(coordinates, coordinateComparator);
            render.setHasTranslucentBlocks(true);
        } else {
            render.setHasTranslucentBlocks(false);
        }

        for (Coordinate coordinate : coordinates) {
            if (cancellationSource.isCancelled()) {
                return null;
            }
            setupBlockRender(pipeline, buffers, renderData, occluder, bounds, pos, offset, blockStates,
                    coordinate.x, coordinate.y, coordinate.z, minX, minY, minZ);
        }

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass);

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

        if (block.getRenderType(blockState) == BlockRenderType.MODEL) {
            for (RenderType layer : RenderType.getBlockRenderTypes()) {
                if (!RenderTypeLookup.canRenderInLayer(blockState, layer)) {
                    continue;
                }

                ForgeHooksClient.setRenderLayer(layer);
                ChunkBuildBuffers.ChunkBuildBufferDelegate builder = buffers.get(layer);
                builder.setOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

                IModelData modelData = ModelDataManager.getModelData(Objects.requireNonNull(Minecraft.getInstance().world), pos);
                if (modelData == null) {
                    modelData = EmptyModelData.INSTANCE;
                }
                if (pipeline.renderBlock(this.slice, blockState, pos, builder, true, modelData)) {
                    bounds.addBlock(x, y, z);
                }
                ForgeHooksClient.setRenderLayer(null);
            }
        }

        FluidState fluidState = block.getFluidState(blockState);

        if (!fluidState.isEmpty()) {
            RenderType layer = RenderTypeLookup.getRenderType(fluidState);

            ChunkBuildBuffers.ChunkBuildBufferDelegate builder = buffers.get(layer);
            builder.setOffset(x - offset.getX(), y - offset.getY(), z - offset.getZ());

            if (pipeline.renderFluid(this.slice, fluidState, pos, builder)) {
                bounds.addBlock(x, y, z);
            }
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

        if (blockState.isOpaqueCube(this.slice, pos)) {
            occluder.setOpaqueCube(pos);
        }
    }

    private double sqDistanceToCamera(Coordinate c) {
        return (camera.x - c.x) * (camera.x - c.x) + (camera.y - c.y) * (camera.y - c.y) + (camera.z - c.z) * (camera.z - c.z);
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
