package me.jellysquid.mods.sodium.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.level.LevelPropagatorExtended;
import me.jellysquid.mods.phosphor.common.chunk.light.LightProviderBlockAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.LightStorageAccess;
import me.jellysquid.mods.phosphor.common.util.LightUtil;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import me.jellysquid.mods.phosphor.common.util.math.DirectionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SkyLightEngine;
import net.minecraft.world.lighting.SkyLightStorage;
import org.spongepowered.asm.mixin.*;

import static net.minecraft.util.math.SectionPos.sectionRelative;
import static net.minecraft.util.math.SectionPos.blockToSectionCoord;

@Mixin(SkyLightEngine.class)
public abstract class MixinChunkSkyLightProvider extends LightEngine<SkyLightStorage.StorageMap, SkyLightStorage>
        implements LevelPropagatorExtended, LightProviderBlockAccess {
    private static final BlockState AIR_BLOCK = Blocks.AIR.defaultBlockState();

    @Shadow
    @Final
    private static Direction[] HORIZONTALS;

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    public MixinChunkSkyLightProvider(IChunkLightProvider chunkProvider, LightType type, SkyLightStorage lightStorage) {
        super(chunkProvider, type, lightStorage);
    }

    /**
     * @author JellySquid
     * @reason Use optimized method below
     */
    @Override
    @Overwrite
    public int computeLevelFromNeighbor(long fromId, long toId, int currentLevel) {
        return this.getPropagatedLevel(fromId, null, toId, currentLevel);
    }

    private int counterBranchA, counterBranchB, counterBranchC;

    /**
     * This breaks up the call to method_20479 into smaller parts so we do not have to pass a mutable heap object
     * to the method in order to extract the light result. This has a few other advantages, allowing us to:
     * - Avoid the de-optimization that occurs from allocating and passing a heap object
     * - Avoid unpacking coordinates twice for both the call to method_20479 and method_20710.
     * - Avoid the the specific usage of AtomicInteger, which has additional overhead for the atomic get/set operations.
     * - Avoid checking if the checked block is opaque twice.
     * - Avoid a redundant block state lookup by re-using {@param fromState}
     *
     * Additionally this implements the cleanups discussed in MC-196542. In particular:
     * - The handling of source-skylight is removed and is now handled by ordinary propagation
     * - The handling of three-way propagations is removed, so the two provided positions must now always be adjacent
     *
     * The rest of the implementation has been otherwise copied from vanilla, but is optimized to avoid constantly
     * (un)packing coordinates and to use an optimized direction lookup function.
     *
     * @param fromState The re-usable block state at position {@param fromId}
     */
    @Override
    public int getPropagatedLevel(long fromId, BlockState fromState, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE) {
            return 15;
        } else if (fromId == Long.MAX_VALUE) {
            return 15; // MC-196524: Remove special handling of source-skylight
        } else if (currentLevel >= 15) {
            return currentLevel;
        }

        int toX = BlockPos.getX(toId);
        int toY = BlockPos.getY(toId);
        int toZ = BlockPos.getZ(toId);

        BlockState toState = this.getBlockStateForLighting(toX, toY, toZ);

        if (toState == null) {
            return 15;
        }

        int fromX = BlockPos.getX(fromId);
        int fromY = BlockPos.getY(fromId);
        int fromZ = BlockPos.getZ(fromId);

        if (fromState == null) {
            fromState = this.getBlockStateForLighting(fromX, fromY, fromZ);
        }

        // Most light updates will happen between two empty air blocks, so use this to assume some properties
        boolean airPropagation = toState == AIR_BLOCK && fromState == AIR_BLOCK;
        boolean verticalOnly = fromX == toX && fromZ == toZ;

        // The direction the light update is propagating
        Direction dir = DirectionHelper.getVecDirection(toX - fromX, toY - fromY, toZ - fromZ);

        if (dir == null) {
            return 15; // MC-196542: The provided positions should always be adjacent
        }

        // Shape comparison checks are only meaningful if the blocks involved have non-empty shapes
        // If we're comparing between air blocks, this is meaningless
        if (!airPropagation) {
            VoxelShape toShape = this.getOpaqueShape(toState, toX, toY, toZ, dir.getOpposite());

            if (toShape != VoxelShapes.block()) {
                VoxelShape fromShape = this.getOpaqueShape(fromState, fromX, fromY, fromZ, dir);

                if (LightUtil.unionCoversFullCube(fromShape, toShape)) {
                    return 15;
                }
            }
        }

        int out = this.getSubtractedLight(toState, toX, toY, toZ);

        // MC-196542: No special handling for source-skylight
        if (out == 0 && currentLevel == 0 && verticalOnly && fromY > toY) {
            return 0;
        }

        return currentLevel + Math.max(1, out);
    }

    /**
     * A few key optimizations are made here, in particular:
     * - The code avoids un-packing coordinates as much as possible and stores the results into local variables.
     * - When necessary, coordinate re-packing is reduced to the minimum number of operations. Most of them can be reduced
     * to only updating the Y-coordinate versus re-computing the entire integer.
     * - Coordinate re-packing is removed where unnecessary (such as when only comparing the Y-coordinate of two positions)
     * - A special propagation method is used that allows the BlockState at {@param id} to be passed, allowing the code
     * which follows to simply re-use it instead of redundantly retrieving another block state.
     *
     * Additionally this implements the cleanups discussed in MC-196542. In particular:
     * - This always passes adjacent positions to {@link LevelPropagatorExtended#propagateLevel(long, BlockState, long, int, boolean)}
     * - This reduces map lookups in the skylight optimization code
     *
     * @reason Use faster implementation
     * @author JellySquid, PhiPro
     */
    @Override
    @Overwrite
    public void checkNeighborsAfterUpdate(long id, int targetLevel, boolean mergeAsMin) {
        long chunkId = SectionPos.blockToSection(id);

        int x = BlockPos.getX(id);
        int y = BlockPos.getY(id);
        int z = BlockPos.getZ(id);

        int localX = sectionRelative(x);
        int localY = sectionRelative(y);
        int localZ = sectionRelative(z);

        BlockState fromState = this.getBlockStateForLighting(x, y, z);

        // Fast-path: Use much simpler logic if we do not need to access adjacent chunks
        if (localX > 0 && localX < 15 && localY > 0 && localY < 15 && localZ > 0 && localZ < 15) {
            for (Direction dir : DIRECTIONS) {
                this.propagateLevel(id, fromState, BlockPos.asLong(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ()), targetLevel, mergeAsMin);
            }

            return;
        }

        int chunkY = blockToSectionCoord(y);
        int chunkOffsetY = 0;

        // Skylight optimization: Try to find bottom-most non-empty chunk
        if (localY == 0) {
            while (!this.storage.storingLightForSection(SectionPos.offset(chunkId, 0, -chunkOffsetY - 1, 0))
                    && this.storage.hasSectionsBelow(chunkY - chunkOffsetY - 1)) {
                ++chunkOffsetY;
            }
        }

        int belowY = y + (-1 - chunkOffsetY * 16);
        int belowChunkY = blockToSectionCoord(belowY);

        if (chunkY == belowChunkY || this.storage.storingLightForSection(ChunkSectionPosHelper.updateYLong(chunkId, belowChunkY))) {
            // MC-196542: Pass adjacent source position
            BlockState state = chunkY == belowChunkY ? fromState : AIR_BLOCK;
            this.propagateLevel(BlockPos.asLong(x, belowY + 1, z), state, BlockPos.asLong(x, belowY, z), targetLevel, mergeAsMin);
        }

        int aboveY = y + 1;
        int aboveChunkY = blockToSectionCoord(aboveY);

        if (chunkY == aboveChunkY || this.storage.storingLightForSection(ChunkSectionPosHelper.updateYLong(chunkId, aboveChunkY))) {
            this.propagateLevel(id, fromState, BlockPos.asLong(x, aboveY, z), targetLevel, mergeAsMin);
        }

        for (Direction dir : HORIZONTALS) {
            int adjX = x + dir.getStepX();
            int adjZ = z + dir.getStepZ();

            long offsetId = BlockPos.asLong(adjX, y, adjZ);
            long offsetChunkId = SectionPos.blockToSection(offsetId);

            boolean isWithinOriginChunk = chunkId == offsetChunkId;

            if (isWithinOriginChunk || this.storage.storingLightForSection(offsetChunkId)) {
                this.propagateLevel(id, fromState, offsetId, targetLevel, mergeAsMin);
            }

            if (isWithinOriginChunk) {
                continue;
            }

            // MC-196542: First iterate over sections to reduce map lookups
            for (int offsetChunkY = chunkY - 1; offsetChunkY > belowChunkY; --offsetChunkY) {
                if (!this.storage.storingLightForSection(ChunkSectionPosHelper.updateYLong(offsetChunkId, offsetChunkY))) {
                    continue;
                }

                for (int offsetY = 15; offsetY >= 0; --offsetY) {
                    int adjY = SectionPos.sectionToBlockCoord(offsetChunkY) + offsetY;
                    offsetId = BlockPos.asLong(adjX, adjY, adjZ);

                    // MC-196542: Pass adjacent source position
                    this.propagateLevel(BlockPos.asLong(x, adjY, z), AIR_BLOCK, offsetId, targetLevel, mergeAsMin);
                }
            }
        }
    }

    @Unique
    private int getLightWithoutLightmap(final long blockPos) {
        return 15 - ((LightStorageAccess) this.storage).getLightWithoutLightmap(blockPos);
    }

    /**
     * This implements the cleanups discussed in MC-196542. In particular:
     * - The special handling of source-skylight is removed and is instead taken care of via ordinary propagation
     * - The logic for looking up light values without associated lightmap is now uniformly applied to all directions and moved to {@link LightStorageAccess#getLightWithoutLightmap(long)}
     * - This passes adjacent positions to {@link #getEdgeLevel(long, long, int)}
     * - This fixes a bug when propagating direct skylight from horizontal neighbors
     *
     * @author PhiPro
     * @reason Implement MC-196542
     */
    @Overwrite
    public int getComputedLevel(long id, long excludedId, int maxLevel) {
        int currentLevel = maxLevel;

        // MC-196542: Remove special handling of source-skylight

        long chunkId = SectionPos.blockToSection(id);
        NibbleArray lightmap = ((LightStorageAccess) this.storage).callGetLightSection(chunkId, true);

        for(Direction direction : DIRECTIONS) {
            long adjId = BlockPos.offset(id, direction);

            if (adjId == excludedId) {
                continue;
            }

            long adjChunkId = SectionPos.blockToSection(adjId);

            NibbleArray adjLightmap;
            if (chunkId == adjChunkId) {
                adjLightmap = lightmap;
            } else {
                adjLightmap = ((LightStorageAccess) this.storage).callGetLightSection(adjChunkId, true);
            }

            final int adjLevel;

            if (adjLightmap == null) {
                // MC-196542: Apply this lookup uniformly to all directions and move it into LightStorage
                adjLevel = this.getLightWithoutLightmap(adjId);
            } else {
                adjLevel = this.getLevel(adjLightmap, adjId);
            }

            // MC-196542: Pass adjacent source position
            int propagatedLevel = this.computeLevelFromNeighbor(adjId, id, adjLevel);

            if (currentLevel > propagatedLevel) {
                currentLevel = propagatedLevel;
            }

            if (currentLevel == 0) {
                return currentLevel;
            }
        }

        return currentLevel;
    }

    /**
     * @author PhiPro
     * @reason MC-196542: There is no need to reset any level other than the directly requested position.
     */
    @Overwrite
    public void checkNode(long id) {
        super.checkNode(id);
    }
}
