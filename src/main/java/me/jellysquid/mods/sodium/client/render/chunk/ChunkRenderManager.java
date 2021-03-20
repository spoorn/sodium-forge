package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.matrix.MatrixStack;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Setter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.util.GlFogHelper;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkCuller;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkFaceFlags;
import me.jellysquid.mods.sodium.client.render.chunk.cull.graph.ChunkGraphCuller;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import me.jellysquid.mods.sodium.common.util.TranslucentPoolUtil;
import me.jellysquid.mods.sodium.common.util.collections.FutureDequeDrain;
import me.jellysquid.mods.sodium.common.util.collections.IntPool;
import me.jellysquid.mods.sodium.common.util.collections.TrackedArray;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

public class ChunkRenderManager implements ChunkStatusListener {
    /**
     * The maximum distance a chunk can be from the player's camera in order to be eligible for blocking updates.
     */
    private static final float NEARBY_CHUNK_DISTANCE = (float) Math.pow(48, 2.0);

    /**
     * The minimum distance the culling plane can be from the player's camera. This helps to prevent mathematical
     * errors that occur when the fog distance is less than 8 blocks in width, such as when using a blindness potion.
     */
    private static final float FOG_PLANE_MIN_DISTANCE = (float) Math.pow(8.0, 2.0);

    /**
     * The distance past the fog's far plane at which to begin culling. Distance calculations use the center of each
     * chunk from the camera's position, and as such, special care is needed to ensure that the culling plane is pushed
     * back far enough. I'm sure there's a mathematical formula that should be used here in place of the constant,
     * but this value works fine in testing.
     */
    private static final float FOG_PLANE_OFFSET = 12.0f;

    private final ChunkBuilder builder;
    private final ChunkRenderBackend backend;

    private final Long2ObjectOpenHashMap<ChunkRenderColumn> columns = new Long2ObjectOpenHashMap<>();

    private final TrackedArray<ChunkRenderContainer> renders = new TrackedArray<>(ChunkRenderContainer.class, 16384);
    private final IntPool renderIds = new IntPool();

    private final ObjectArrayFIFOQueue<ChunkRenderContainer> importantRebuildQueue = new ObjectArrayFIFOQueue<>();
    private final ObjectArrayFIFOQueue<ChunkRenderContainer> rebuildQueue = new ObjectArrayFIFOQueue<>();

    private final ChunkRenderList[] chunkRenderLists = new ChunkRenderList[BlockRenderPass.COUNT];
    private final ObjectList<ChunkRenderContainer> tickableChunks = new ObjectArrayList<>();

    private final ObjectList<TileEntity> visibleBlockEntities = new ObjectArrayList<>();

    private final SodiumWorldRenderer renderer;
    private final ClientWorld world;

    private final ChunkCuller culler;
    private final boolean useChunkFaceCulling;

    private float cameraX, cameraY, cameraZ;
    private boolean dirty;
    @Setter
    private boolean cameraChanged;

    private final boolean translucencySorting;

    private int visibleChunkCount;

    private boolean useFogCulling;
    private double fogRenderCutoff;

    @Setter
    private Matrix4f projection;

    private FrustumExtended currFrustum;

    public ChunkRenderManager(SodiumWorldRenderer renderer, ChunkRenderBackend backend, BlockRenderPassManager renderPassManager, ClientWorld world, int renderDistance) {
        this.backend = backend;
        this.renderer = renderer;
        this.world = world;

        this.builder = new ChunkBuilder(backend.getVertexType(), this.backend);
        this.builder.init(world, renderPassManager);

        this.dirty = true;

        for (int i = 0; i < this.chunkRenderLists.length; i++) {
            this.chunkRenderLists[i] = new ChunkRenderList();
        }

        this.culler = new ChunkGraphCuller(world, renderDistance);
        this.useChunkFaceCulling = SodiumClientMod.options().advanced.useChunkFaceCulling;
        this.translucencySorting = SodiumClientMod.options().advanced.translucencySorting;

        TranslucentPoolUtil.resetTranslucentRebuilds();
    }

    public void update(ActiveRenderInfo camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.reset();

        this.setup(camera);

        this.currFrustum = frustum;

        this.iterateChunks(camera, frustum, frame, spectator);

        this.dirty = false;
    }

    private void iterateChunks(ActiveRenderInfo camera, FrustumExtended frustum, int frame, boolean spectator) {
        IntList list = this.culler.computeVisible(camera, frustum, frame, spectator);
        IntIterator it = list.iterator();

        int translucentBudget = Math.max(1, builder.getSchedulingBudget()/4);

        while (it.hasNext()) {
            int sectionId = it.nextInt();
            ChunkRenderContainer render = this.renders.get(sectionId);

            this.addChunk(render, translucentBudget);
        }
    }

    private void addChunk(ChunkRenderContainer render, int translucentBudget) {
        boolean rebuild = render.needsRebuild() && render.canRebuild();

        if (this.translucencySorting && render.hasTranslucentBlocks() && this.cameraChanged
                && TranslucentPoolUtil.getTranslucentRebuilds() <= translucentBudget) {
            ChunkRenderBounds bounds = render.getBounds();
            if (bounds != null) {
                // TODO: This should actually check if any part of the chunk is in the frustum
                boolean isInFrustum = currFrustum.fastAabbTest(bounds.x1, bounds.y1, bounds.z1, bounds.x2, bounds.y2, bounds.z2);
                if (isInFrustum) {
                    TranslucentPoolUtil.incrementTranslucentRebuilds();
                    rebuild = true;
                } else {
                    rebuild = false;
                }
            }
        }

        if (rebuild) {
            if (render.needsImportantRebuild()) {
                this.importantRebuildQueue.enqueue(render);
            } else {
                this.rebuildQueue.enqueue(render);
            }
        }

        if (this.useFogCulling && render.getSquaredDistanceXZ(this.cameraX, this.cameraZ) >= this.fogRenderCutoff) {
            return;
        }

        if (!render.isEmpty()) {
            this.addChunkToRenderLists(render);
            this.addEntitiesToRenderLists(render);
        }
    }

    private void setup(ActiveRenderInfo camera) {
        Vector3d cameraPos = camera.getProjectedView();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;

        this.useFogCulling = false;

        if (SodiumClientMod.options().advanced.useFogOcclusion) {
            float dist = GlFogHelper.getFogCutoff() + FOG_PLANE_OFFSET;

            if (dist != 0.0f) {
                this.useFogCulling = true;
                this.fogRenderCutoff = Math.max(FOG_PLANE_MIN_DISTANCE, dist * dist);
            }
        }
    }

    private void addChunkToRenderLists(ChunkRenderContainer render) {
        int visibleFaces = render.getFacesWithData();

        if (visibleFaces == 0) {
            return;
        }

        if (this.useChunkFaceCulling) {
            ChunkRenderBounds bounds = render.getBounds();

            if (this.cameraX < bounds.x1) {
                visibleFaces &= ~ChunkFaceFlags.EAST;
            }

            if (this.cameraX > bounds.x2) {
                visibleFaces &= ~ChunkFaceFlags.WEST;
            }

            if (this.cameraY < bounds.y1) {
                visibleFaces &= ~ChunkFaceFlags.UP;
            }

            if (this.cameraY > bounds.y2) {
                visibleFaces &= ~ChunkFaceFlags.DOWN;
            }

            if (this.cameraZ < bounds.z1) {
                visibleFaces &= ~ChunkFaceFlags.SOUTH;
            }

            if (this.cameraZ > bounds.z2) {
                visibleFaces &= ~ChunkFaceFlags.NORTH;
            }
        }

        if (visibleFaces == 0) {
            return;
        }

        ChunkGraphicsStateArray states = render.getGraphicsStates();

        for (int i = 0; i < states.getSize(); i++) {
            this.chunkRenderLists[states.getKey(i)]
                    .add(states.getValue(i), visibleFaces);
        }

        if (render.isTickable()) {
            this.tickableChunks.add(render);
        }

        this.visibleChunkCount++;
    }

    private void addEntitiesToRenderLists(ChunkRenderContainer render) {
        Collection<TileEntity> blockEntities = render.getData().getBlockEntities();

        if (!blockEntities.isEmpty()) {
            this.visibleBlockEntities.addAll(blockEntities);
        }
    }

    public ChunkRenderContainer getRender(int x, int y, int z) {
        ChunkRenderColumn column = this.columns.get(ChunkPos.asLong(x, z));

        if (column == null) {
            return null;
        }

        return column.getRender(y);
    }

    private void reset() {
        this.rebuildQueue.clear();
        this.importantRebuildQueue.clear();

        this.visibleBlockEntities.clear();

        for (ChunkRenderList list : this.chunkRenderLists) {
            list.reset();
        }

        this.tickableChunks.clear();

        this.visibleChunkCount = 0;
    }

    public Collection<TileEntity> getVisibleBlockEntities() {
        return this.visibleBlockEntities;
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.builder.onChunkStatusChanged(x, z);
        this.loadChunk(x, z);
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.builder.onChunkStatusChanged(x, z);
        this.unloadChunk(x, z);
    }

    private void loadChunk(int x, int z) {
        ChunkRenderColumn column = new ChunkRenderColumn(x, z);
        ChunkRenderColumn prev;

        if ((prev = this.columns.put(ChunkPos.asLong(x, z), column)) != null) {
            this.unloadSections(prev);
        }

        this.connectNeighborColumns(column);
        this.loadSections(column);

        this.dirty = true;
    }

    private void unloadChunk(int x, int z) {
        ChunkRenderColumn column = this.columns.remove(ChunkPos.asLong(x, z));

        if (column == null) {
            return;
        }

        this.disconnectNeighborColumns(column);
        this.unloadSections(column);

        this.dirty = true;
    }

    private void loadSections(ChunkRenderColumn column) {
        int x = column.getX();
        int z = column.getZ();

        for (int y = 0; y < 16; y++) {
            ChunkRenderContainer render = this.createChunkRender(column, x, y, z);
            column.setRender(y, render);

            this.culler.onSectionLoaded(x, y, z, render.getId());
        }
    }

    private void unloadSections(ChunkRenderColumn column) {
        for (int y = 0; y < 16; y++) {
            ChunkRenderContainer render = column.getRender(y);

            if (render != null) {
                render.delete();

                this.renders.remove(render);
                this.renderIds.deallocateId(render.getId());

                this.culler.onSectionUnloaded(render.getId());
            }
        }
    }

    private void connectNeighborColumns(ChunkRenderColumn column) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkRenderColumn adj = this.getAdjacentColumn(column, dir);

            if (adj != null) {
                adj.setAdjacentColumn(dir.getOpposite(), column);
            }

            column.setAdjacentColumn(dir, adj);
        }
    }

    private void disconnectNeighborColumns(ChunkRenderColumn column) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkRenderColumn adj = column.getAdjacentColumn(dir);

            if (adj != null) {
                adj.setAdjacentColumn(dir.getOpposite(), null);
            }

            column.setAdjacentColumn(dir, null);
        }
    }

    private ChunkRenderColumn getAdjacentColumn(ChunkRenderColumn column, Direction dir) {
        return this.getColumn(column.getX() + dir.getXOffset(), column.getZ() + dir.getZOffset());
    }

    private ChunkRenderColumn getColumn(int x, int z) {
        return this.columns.get(ChunkPos.asLong(x, z));
    }

    private ChunkRenderContainer createChunkRender(ChunkRenderColumn column, int x, int y, int z) {
        ChunkRenderContainer render = new ChunkRenderContainer(this.backend, this.renderer, x, y, z, column, this.renderIds.allocateId());
        this.renders.add(render);

        if (ChunkSection.isEmpty(this.world.getChunk(x, z).getSections()[y])) {
            render.setData(ChunkRenderData.EMPTY);
        } else {
            render.scheduleRebuild(false);
        }

        return render;
    }

    public void renderLayer(MatrixStack matrixStack, BlockRenderPass pass, double x, double y, double z) {
        ChunkRenderList chunkRenderList = this.chunkRenderLists[pass.ordinal()];
        ChunkRenderListIterator iterator = chunkRenderList.iterator(pass.isTranslucent());

        this.backend.begin(matrixStack, projection);
        this.backend.render(iterator, new ChunkCameraContext(x, y, z), projection);
        this.backend.end(matrixStack);
    }

    public void tickVisibleRenders() {
        for (ChunkRenderContainer render : this.tickableChunks) {
            render.tick();
        }
    }

    public boolean isChunkVisible(int x, int y, int z) {
        ChunkRenderContainer render = this.getRender(x, y, z);

        if (render == null) {
            return false;
        }

        return this.culler.isSectionVisible(render.getId());
    }

    public void updateChunks() {
        Deque<CompletableFuture<ChunkBuildResult>> futures = new ArrayDeque<>();

        int budget = this.builder.getSchedulingBudget();
        int submitted = 0;

        while (!this.importantRebuildQueue.isEmpty()) {
            ChunkRenderContainer render = this.importantRebuildQueue.dequeue();

            // Do not allow distant chunks to block rendering
            if (!this.isChunkPrioritized(render)) {
                this.builder.deferRebuild(render);
            } else {
                futures.add(this.builder.scheduleRebuildTaskAsync(render));
            }

            this.dirty = true;
            submitted++;
        }

        while (submitted < budget && !this.rebuildQueue.isEmpty()) {
            ChunkRenderContainer render = this.rebuildQueue.dequeue();

            this.builder.deferRebuild(render);
            submitted++;
        }

        this.dirty |= submitted > 0;

        // Try to complete some other work on the main thread while we wait for rebuilds to complete
        this.dirty |= this.builder.performPendingUploads();

        if (!futures.isEmpty()) {
            this.backend.upload(new FutureDequeDrain<>(futures));
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markCameraChanged() {
        this.cameraChanged = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void restoreChunks(LongCollection chunks) {
        LongIterator it = chunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            this.loadChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos));
        }
    }

    public boolean isBuildComplete() {
        return this.builder.isBuildQueueEmpty();
    }

    public void setCameraPosition(double x, double y, double z) {
        this.builder.setCameraPosition(x, y, z);
    }

    public void destroy() {
        this.reset();

        for (ChunkRenderColumn column : this.columns.values()) {
            this.unloadSections(column);
        }

        this.columns.clear();

        this.builder.stopWorkers();
    }

    public int getTotalSections() {
        return this.columns.size() * 16;
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        ChunkRenderContainer render = this.getRender(x, y, z);

        if (render != null) {
            // Nearby chunks are always rendered immediately
            important = important || this.isChunkPrioritized(render);

            // Only enqueue chunks for updates during the next frame if it is visible and wasn't already dirty
            if (render.scheduleRebuild(important) && this.culler.isSectionVisible(render.getId())) {
                (render.needsImportantRebuild() ? this.importantRebuildQueue : this.rebuildQueue)
                        .enqueue(render);
            }

            this.dirty = true;
        }
    }

    public boolean isChunkPrioritized(ChunkRenderContainer render) {
        return render.getSquaredDistance(this.cameraX, this.cameraY, this.cameraZ) <= NEARBY_CHUNK_DISTANCE;
    }

    public int getVisibleChunkCount() {
        return this.visibleChunkCount;
    }

    public void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        ChunkRenderContainer render = this.getRender(x, y, z);

        if (render != null) {
            this.culler.onSectionStateChanged(render.getId(), data);
        }
    }
}
