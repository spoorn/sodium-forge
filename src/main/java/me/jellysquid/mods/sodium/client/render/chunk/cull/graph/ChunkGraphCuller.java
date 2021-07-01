package me.jellysquid.mods.sodium.client.render.chunk.cull.graph;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkCuller;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import me.jellysquid.mods.sodium.common.util.IdTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChunkGraphCuller implements ChunkCuller {
    private final Long2ObjectMap<ChunkGraphNode> nodes = new Long2ObjectOpenHashMap<>();

    private final ChunkGraphIterationQueue visible = new ChunkGraphIterationQueue();
    private final World world;
    private final int renderDistance;

    private FrustumExtended frustum;
    private boolean useOcclusionCulling;

    private int activeFrame = 0;

    public ChunkGraphCuller(World world, int renderDistance) {
        this.world = world;
        this.renderDistance = renderDistance;
    }

    @Override
    public IntArrayList computeVisible(ActiveRenderInfo camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.initSearch(camera, frustum, frame, spectator);

        ChunkGraphIterationQueue queue = this.visible;

        for (int i = 0; i < queue.size(); i++) {
            ChunkGraphNode node = queue.getNode(i);
            Direction flow = queue.getDirection(i);

            for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
                if (this.isCulled(node, flow, dir)) {
                    continue;
                }

                ChunkGraphNode adj = node.getConnectedNode(dir);

                if (adj != null) {
                    this.bfsEnqueue(node, adj, dir.getOpposite());
                }
            }
        }

        return this.visible.getOrderedIdList();
    }

    private boolean isCulled(ChunkGraphNode node, Direction from, Direction to) {
        if (node.canCull(to)) {
            return true;
        }

        return this.useOcclusionCulling && from != null && !node.isVisibleThrough(from, to);
    }

    private void initSearch(ActiveRenderInfo camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.activeFrame = frame;
        this.frustum = frustum;
        this.useOcclusionCulling = Minecraft.getInstance().renderChunksMany;

        this.visible.clear();

        BlockPos origin = camera.getBlockPos();

        int chunkX = origin.getX() >> 4;
        int chunkY = origin.getY() >> 4;
        int chunkZ = origin.getZ() >> 4;

        ChunkGraphNode rootNode = this.getNode(chunkX, chunkY, chunkZ);

        if (rootNode != null) {
            rootNode.resetCullingState();
            rootNode.setLastVisibleFrame(frame);

            if (spectator && this.world.getBlockState(origin).isOpaqueCube(this.world, origin)) {
                this.useOcclusionCulling = false;
            }

            this.visible.add(rootNode, null);
        } else {
            chunkY = MathHelper.clamp(origin.getY() >> 4, 0, 15);

            List<ChunkGraphNode> bestNodes = new ArrayList<>();

            for (int x2 = -this.renderDistance; x2 <= this.renderDistance; ++x2) {
                for (int z2 = -this.renderDistance; z2 <= this.renderDistance; ++z2) {
                    ChunkGraphNode node = this.getNode(chunkX + x2, chunkY, chunkZ + z2);

                    if (node == null || node.isCulledByFrustum(frustum)) {
                        continue;
                    }

                    node.resetCullingState();
                    node.setLastVisibleFrame(frame);

                    bestNodes.add(node);
                }
            }

            bestNodes.sort(Comparator.comparingDouble(node -> node.getSquaredDistance(origin)));

            for (ChunkGraphNode node : bestNodes) {
                this.visible.add(node, null);
            }
        }
    }


    private void bfsEnqueue(ChunkGraphNode parent, ChunkGraphNode node, Direction flow) {
        if (node.getLastVisibleFrame() == this.activeFrame) {
            return;
        }

        if (node.isCulledByFrustum(this.frustum)) {
            return;
        }

        node.setLastVisibleFrame(this.activeFrame);
        node.setCullingState(parent.getCullingState(), flow);

        this.visible.add(node, flow);
    }

    private void connectNeighborNodes(ChunkGraphNode node) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkGraphNode adj = this.findAdjacentNode(node, dir);

            if (adj != null) {
                adj.setAdjacentNode(dir.getOpposite(), node);
            }

            node.setAdjacentNode(dir, adj);
        }
    }

    private void disconnectNeighborNodes(ChunkGraphNode node) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkGraphNode adj = node.getConnectedNode(dir);

            if (adj != null) {
                adj.setAdjacentNode(dir.getOpposite(), null);
            }

            node.setAdjacentNode(dir, null);
        }
    }

    private ChunkGraphNode findAdjacentNode(ChunkGraphNode node, Direction dir) {
        return this.getNode(node.getChunkX() + dir.getXOffset(), node.getChunkY() + dir.getYOffset(), node.getChunkZ() + dir.getZOffset());
    }

    private ChunkGraphNode getNode(int x, int y, int z) {
        return this.nodes.get(SectionPos.asLong(x, y, z));
    }

    @Override
    public void onSectionStateChanged(int x, int y, int z, SetVisibility occlusionData) {
        ChunkGraphNode node = this.getNode(x, y, z);

        if (node != null) {
            node.setOcclusionData(occlusionData);
        }
    }

    @Override
    public void onSectionLoaded(int x, int y, int z, int id) {
        ChunkGraphNode node = new ChunkGraphNode(x, y, z, id);
        ChunkGraphNode prev;

        if ((prev = this.nodes.put(SectionPos.asLong(x, y, z), node)) != null) {
            this.disconnectNeighborNodes(prev);
        }

        this.connectNeighborNodes(node);
    }

    @Override
    public void onSectionUnloaded(int x, int y, int z) {
        ChunkGraphNode node = this.nodes.remove(SectionPos.asLong(x, y, z));

        if (node != null) {
            this.disconnectNeighborNodes(node);
        }
    }

    @Override
    public boolean isSectionVisible(int x, int y, int z) {
        ChunkGraphNode render = this.getNode(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getLastVisibleFrame() == this.activeFrame;
    }

    @Override
    public <T extends ChunkGraphicsState> boolean isInDirectView(IdTable<ChunkRenderContainer<T>> renders,
        ChunkRenderContainer<T> render, float camX, float camY, float camZ) {
        List<BlockPos> srcTranslucent = render.getData().getTranslucentBlocks();

        int minX = MathHelper.floor(camX);
        int minY = MathHelper.floor(camY);
        int minZ = MathHelper.floor(camZ);

        Set<Set<BlockPos>> intersects = new HashSet<>();

        for (BlockPos pos : srcTranslucent) {
            intersects.add(getIntersectingGrids(minX, minY, minZ, pos.getX(), pos.getY(), pos.getZ()));
        }

        if (intersects.isEmpty()) {
            return true;
        }

        for (int id : this.visible.getOrderedIdList()) {
            if (id == render.getId()) {
                return true;
            }

            ChunkRenderContainer<T> other = renders.get(id);
            List<BlockPos> otherBlocks = other.getData().getOpaqueBlocks();

            for (BlockPos otherPos : otherBlocks) {
                intersects.removeIf(blockPos -> blockPos.contains(otherPos));

                if (intersects.isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Ray trace to find all block positions from one coordinate to another.
     *
     * From http://playtechs.blogspot.com/2007/03/raytracing-on-grid.html.
     */
    private Set<BlockPos> getIntersectingGrids(int x, int y, int z, int maxX, int maxY, int maxZ) {
        float dx = Math.abs(maxX - x);
        float dy = Math.abs(maxY - y);
        float dz = Math.abs(maxZ - z);

        float xWeight = 1.0f/dx;
        float yWeight = 1.0f/dy;
        float zWeight = 1.0f/dz;

        int x_inc = Integer.compare(maxX, x);
        int y_inc = Integer.compare(maxY, y);
        int z_inc = Integer.compare(maxZ, z);

        float n = 1 + dx + dy + dz;
        float errorX = xWeight;
        float errorY = yWeight;
        float errorZ = zWeight;

        Set<BlockPos> ret = new HashSet<>();

        for (; n > 0; n--) {
            if (x == maxX && y == maxY && z == maxZ) {
                return ret;
            }
            ret.add(new BlockPos(x, y, z));

            if (errorX < errorY && errorX < errorZ) {
                x += x_inc;
                errorX += xWeight;
            } else if (errorY < errorX && errorY < errorZ) {
                y += y_inc;
                errorY += yWeight;
            } else {
                z += z_inc;
                errorZ += zWeight;
            }
        }

        return ret;
    }
}
