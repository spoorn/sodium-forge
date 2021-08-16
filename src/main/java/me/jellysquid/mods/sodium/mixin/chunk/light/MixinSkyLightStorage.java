package me.jellysquid.mods.sodium.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import me.jellysquid.mods.phosphor.common.chunk.light.IReadonly;
import me.jellysquid.mods.phosphor.common.chunk.light.LevelPropagatorAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import me.jellysquid.mods.phosphor.common.util.chunk.light.EmptyChunkNibbleArray;
import me.jellysquid.mods.phosphor.common.util.chunk.light.SkyLightChunkNibbleArray;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SkyLightStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage extends MixinLightStorage<SkyLightStorage.StorageMap> {
    /**
     * An optimized implementation which avoids constantly unpacking and repacking integer coordinates.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public int getLightValue(long pos) {
        int posX = BlockPos.getX(pos);
        int posYOrig = BlockPos.getY(pos);
        int posZ = BlockPos.getZ(pos);

        int chunkX = SectionPos.blockToSectionCoord(posX);
        int chunkYOrig = SectionPos.blockToSectionCoord(posYOrig);
        int chunkZ = SectionPos.blockToSectionCoord(posZ);

        long chunkOrig = SectionPos.asLong(chunkX, chunkYOrig, chunkZ);

        StampedLock lock = ((SharedLightStorageAccess<SkyLightStorage.StorageMap>) this).getStorageLock();
        long stamp;

        NibbleArray array;

        optimisticRead:
        while (true) {
            stamp = lock.tryOptimisticRead();

            int posY = posYOrig;
            int chunkY = chunkYOrig;
            long chunk = chunkOrig;

            SkyLightStorage.StorageMap data = ((SharedLightStorageAccess<SkyLightStorage.StorageMap>) this).getStorage();
            SkyLightStorageDataAccess sdata = ((SkyLightStorageDataAccess) (Object) data);

            int height = sdata.getHeight(SectionPos.getZeroNode(chunk));

            if (height == sdata.getDefaultHeight() || chunkY >= height) {
                if (lock.validate(stamp)) {
                    return 15;
                } else {
                    continue;
                }
            }

            array = data.getLayer(chunk);

            while (array == null) {
                ++chunkY;

                if (chunkY >= height) {
                    if (lock.validate(stamp)) {
                        return 15;
                    } else {
                        continue optimisticRead;
                    }
                }

                chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                array = data.getLayer(chunk);

                posY = chunkY << 4;
            }

            if (lock.validate(stamp)) {
                return array.get(
                        SectionPos.sectionRelative(posX),
                        SectionPos.sectionRelative(posY),
                        SectionPos.sectionRelative(posZ)
                );
            }
        }
    }

    @Shadow
    protected abstract boolean isAboveData(long sectionPos);

    @Shadow
    protected abstract boolean lightOnInSection(long sectionPos);

    @Override
    public int getLightWithoutLightmap(final long blockPos) {
        final long sectionPos = SectionPos.blockToSection(blockPos);
        final NibbleArray lightmap = this.getLightmapAbove(sectionPos);

        if (lightmap == null) {
            return this.lightOnInSection(sectionPos) ? 15 : 0;
        }

        return lightmap.get(SectionPos.sectionRelative(BlockPos.getX(blockPos)), 0, SectionPos.sectionRelative(BlockPos.getZ(blockPos)));
    }

    @Redirect(
        method = "createDataLayer(J)Lnet/minecraft/world/chunk/NibbleArray;",
        at = @At(
            value = "NEW",
            target = "net/minecraft/world/chunk/NibbleArray"
        )
    )
    private NibbleArray initializeLightmap(final long pos) {
        final NibbleArray ret = new NibbleArray();

        if (this.lightOnInSection(pos)) {
            Arrays.fill(ret.getData(), (byte) -1);
        }

        return ret;
    }

    @Inject(
        method = "queueRemoveSource(J)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disable_enqueueRemoveSection(final CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(
        method = "queueAddSource(J)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disable_enqueueAddSection(final CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Forceload a lightmap above the world for initial skylight
     */
    @Unique
    private final LongSet preInitSkylightChunks = new LongOpenHashSet();

    @Override
    public void beforeChunkEnabled(final long chunkPos) {
        if (!this.lightOnInSection(chunkPos)) {
            this.preInitSkylightChunks.add(chunkPos);
            this.checkEdge(Long.MAX_VALUE, SectionPos.asLong(SectionPos.x(chunkPos), 16, SectionPos.z(chunkPos)), 1, true);
        }
    }

    @Override
    public void afterChunkDisabled(final long chunkPos) {
        if (this.preInitSkylightChunks.remove(chunkPos)) {
            this.checkEdge(Long.MAX_VALUE, SectionPos.asLong(SectionPos.x(chunkPos), 16, SectionPos.z(chunkPos)), 2, false);
        }
    }

    @Override
    protected int getLevelFromSource(final long id) {
        final int ret = super.getLevelFromSource(id);

        if (ret >= 2 && SectionPos.y(id) == 16 && this.preInitSkylightChunks.contains(SectionPos.getZeroNode(id))) {
            return 1;
        }

        return ret;
    }

    @Unique
    private final LongSet initSkylightChunks = new LongOpenHashSet();

    @Shadow
    @Final
    private LongSet columnsWithSkySources;

    /**
     * @author PhiPro
     * @reason Re-implement completely.
     * This method now schedules initial lighting when enabling source light for a chunk that already has light updates enabled.
     */
    @Override
    @Overwrite
    public void enableLightSources(final long chunkPos, final boolean enabled) {
        if (enabled) {
            if (this.preInitSkylightChunks.contains(chunkPos)) {
                this.initSkylightChunks.add(chunkPos);
                this.recheckInconsistencyFlag();
            } else {
                this.columnsWithSkySources.add(chunkPos);
            }
        } else {
            this.columnsWithSkySources.remove(chunkPos);
            this.initSkylightChunks.remove(chunkPos);
            this.recheckInconsistencyFlag();
        }
    }

    @Unique
    private static void spreadSourceSkylight(final LevelPropagatorAccess lightProvider, final long src, final Direction dir) {
        lightProvider.invokePropagateLevel(src, BlockPos.offset(src, dir), 0, true);
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void markNewInconsistencies(LightEngine<SkyLightStorage.StorageMap, ?> lightProvider, boolean doSkylight, boolean skipEdgeLightPropagation) {
        super.markNewInconsistencies(lightProvider, doSkylight, skipEdgeLightPropagation);

        if (!doSkylight || !this.hasSourceInconsistencies) {
            return;
        }

        for (final LongIterator it = this.initSkylightChunks.iterator(); it.hasNext(); ) {
            final long chunkPos = it.nextLong();

            final LevelPropagatorAccess levelPropagator = (LevelPropagatorAccess) lightProvider;
            final int minY = this.fillSkylightColumn(lightProvider, chunkPos);

            this.columnsWithSkySources.add(chunkPos);
            this.preInitSkylightChunks.remove(chunkPos);
            this.checkEdge(Long.MAX_VALUE, SectionPos.asLong(SectionPos.x(chunkPos), 16, SectionPos.z(chunkPos)), 2, false);

            if (this.storingLightForSection(SectionPos.asLong(SectionPos.x(chunkPos), minY, SectionPos.z(chunkPos)))) {
                final long blockPos = BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(chunkPos)), SectionPos.sectionToBlockCoord(minY), SectionPos.sectionToBlockCoord(SectionPos.z(chunkPos)));

                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        spreadSourceSkylight(levelPropagator, BlockPos.offset(blockPos, x, 16, z), Direction.DOWN);
                    }
                }
            }

            for (final Direction dir : Direction.Plane.HORIZONTAL) {
                // Skip propagations into sections directly exposed to skylight that are initialized in this update cycle
                boolean spread = !this.initSkylightChunks.contains(SectionPos.offset(chunkPos, dir));

                for (int y = 16; y > minY; --y) {
                    final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), y, SectionPos.z(chunkPos));
                    final long neighborSectionPos = SectionPos.offset(sectionPos, dir);

                    if (!this.storingLightForSection(neighborSectionPos)) {
                        continue;
                    }

                    if (!spread) {
                        if (this.dataSectionSet.contains(neighborSectionPos)) {
                            spread = true;
                        } else {
                            continue;
                        }
                    }

                    final long blockPos = BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos)), SectionPos.sectionToBlockCoord(y), SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos)));

                    final int ox = 15 * Math.max(dir.getStepX(), 0);
                    final int oz = 15 * Math.max(dir.getStepZ(), 0);

                    final int dx = Math.abs(dir.getStepZ());
                    final int dz = Math.abs(dir.getStepX());

                    for (int t = 0; t < 16; ++t) {
                        for (int dy = 0; dy < 16; ++dy) {
                            spreadSourceSkylight(levelPropagator, BlockPos.offset(blockPos, ox + t * dx, dy, oz + t * dz), dir);
                        }
                    }
                }
            }
        }

        this.initSkylightChunks.clear();

        if (!this.removedLightmaps.isEmpty()) {
            final LongSet removedLightmaps = new LongOpenHashSet(this.removedLightmaps);

            for (final LongIterator it = removedLightmaps.iterator(); it.hasNext(); ) {
                final long sectionPos = it.nextLong();

                if (!this.enabledChunks.contains(SectionPos.getZeroNode(sectionPos))) {
                    continue;
                }

                if (!this.removedLightmaps.contains(sectionPos)) {
                    continue;
                }

                final long sectionPosAbove = this.getSectionAbove(sectionPos);

                if (sectionPosAbove == Long.MAX_VALUE) {
                    this.updateVanillaLightmapsBelow(sectionPos, this.lightOnInSection(sectionPos) ? DIRECT_SKYLIGHT_MAP : null, true);
                } else {
                    long removedLightmapPosAbove = sectionPos;

                    for (long pos = sectionPos; pos != sectionPosAbove; pos = SectionPos.offset(pos, Direction.UP)) {
                        if (this.removedLightmaps.remove(pos)) {
                            removedLightmapPosAbove = pos;
                        }
                    }

                    this.updateVanillaLightmapsBelow(removedLightmapPosAbove, this.vanillaLightmapComplexities.get(sectionPosAbove) == 0 ? null : this.getDataLayer(sectionPosAbove, true), false);
                }
            }

            this.removedLightmaps.clear();
        }

        this.hasSourceInconsistencies = false;
    }

    /**
     * Fill all sections above the topmost block with source skylight.
     * @return The section containing the topmost block or the section corresponding to {@link SkyLightStorage.StorageMap#minSectionY} if none exists.
     */
    private int fillSkylightColumn(final LightEngine<SkyLightStorage.StorageMap, ?> lightProvider, final long chunkPos) {
        int minY = 16;
        NibbleArray lightmapAbove = null;

        // First need to remove all pending light updates before changing any light value

        for (; this.hasSectionsBelow(minY); --minY) {
            final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), minY, SectionPos.z(chunkPos));

            if (this.dataSectionSet.contains(sectionPos)) {
                break;
            }

            if (this.storingLightForSection(sectionPos)) {
                this.clearQueuedSectionBlocks(lightProvider, sectionPos);
            }

            final NibbleArray lightmap = this.getLightmap(sectionPos);

            if (lightmap != null) {
                lightmapAbove = lightmap;
            }
        }

        // Set up a lightmap and adjust the complexity for the section below

        final long sectionPosBelow = SectionPos.asLong(SectionPos.x(chunkPos), minY, SectionPos.z(chunkPos));

        if (this.storingLightForSection(sectionPosBelow)) {
            final NibbleArray lightmapBelow = this.getLightmap(sectionPosBelow);

            if (lightmapBelow == null) {
                int complexity = 15 * 16 * 16;

                if (lightmapAbove != null) {
                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            complexity -= lightmapAbove.get(x, 0, z);
                        }
                    }
                }

                this.getOrAddLightmap(sectionPosBelow);
                this.setLightmapComplexity(sectionPosBelow, complexity);
            } else {
                int amount = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        amount += getComplexityChange(lightmapBelow.get(x, 15, z), lightmapAbove == null ? 0 : lightmapAbove.get(x, 0, z), 15);
                    }
                }

                this.changeLightmapComplexity(sectionPosBelow, amount);
            }
        }

        // Now light values can be changed
        // Delete lightmaps so the sections inherit direct skylight

        int sections = 0;

        for (int y = 16; y > minY; --y) {
            final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), y, SectionPos.z(chunkPos));

            if (this.removeLightmap(sectionPos)) {
                sections |= 1 << (y + 1);
            }
        }

        // Calling onUnloadSection() after removing all the lightmaps is slightly more efficient

        this.updatingSectionData.clearCache();

        for (int y = 16; y > minY; --y) {
            if ((sections & (1 << (y + 1))) != 0) {
                this.onNodeRemoved(SectionPos.asLong(SectionPos.x(chunkPos), y, SectionPos.z(chunkPos)));
            }
        }

        // Add trivial lightmaps for vanilla compatibility

        for (int y = 16; y > minY; --y) {
            final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), y, SectionPos.z(chunkPos));

            if (this.nonOptimizableSections.contains(sectionPos)) {
                this.updatingSectionData.setLayer(sectionPos, this.createTrivialVanillaLightmap(DIRECT_SKYLIGHT_MAP));
                this.changedSections.add(sectionPos);
            }
        }

        this.updatingSectionData.clearCache();

        return minY;
    }

    @Shadow
    private volatile boolean hasSourceInconsistencies;

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    private void recheckInconsistencyFlag() {
        this.hasSourceInconsistencies = !this.initSkylightChunks.isEmpty();
    }

    @Unique
    private static final NibbleArray DIRECT_SKYLIGHT_MAP = createDirectSkyLightMap();

    @Unique
    private final Long2IntMap vanillaLightmapComplexities = new Long2IntOpenHashMap();
    @Unique
    private final LongSet removedLightmaps = new LongOpenHashSet();

    @Unique
    private static NibbleArray createDirectSkyLightMap() {
        final NibbleArray lightmap = new NibbleArray();
        Arrays.fill(lightmap.getData(), (byte) -1);

        return lightmap;
    }

    @Override
    public boolean storingLightForSection(final long sectionPos) {
        return super.storingLightForSection(sectionPos) && this.getDataLayer(sectionPos, true) != null;
    }

    // Queued lightmaps are only added to the world via updateLightmaps()
    @Redirect(
        method = "createDataLayer(J)Lnet/minecraft/world/chunk/NibbleArray;",
        slice = @Slice(
            from = @At(
                value = "FIELD",
                target = "Lnet/minecraft/world/lighting/SkyLightStorage;queuedSections:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
                opcode = Opcodes.GETFIELD
            )
        ),
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;get(J)Ljava/lang/Object;",
            ordinal = 0,
            remap = false
        )
    )
    private Object cancelLightmapLookupFromQueue(final Long2ObjectMap<NibbleArray> lightmapArray, final long pos) {
        return null;
    }

    @Unique
    private static int getComplexityChange(final int val, final int oldNeighborVal, final int newNeighborVal) {
        return Math.abs(newNeighborVal - val) - Math.abs(oldNeighborVal - val);
    }

    @Override
    protected void beforeLightChange(final long blockPos, final int oldVal, final int newVal, final NibbleArray lightmap) {
        final long sectionPos = SectionPos.blockToSection(blockPos);

        if (SectionPos.sectionRelative(BlockPos.getY(blockPos)) == 0) {
            this.vanillaLightmapComplexities.put(sectionPos, this.vanillaLightmapComplexities.get(sectionPos) + newVal - oldVal);

            final long sectionPosBelow = this.getSectionBelow(sectionPos);

            if (sectionPosBelow != Long.MAX_VALUE) {
                final NibbleArray lightmapBelow = this.getOrAddLightmap(sectionPosBelow);

                final int x = SectionPos.sectionRelative(BlockPos.getX(blockPos));
                final int z = SectionPos.sectionRelative(BlockPos.getZ(blockPos));

                this.changeLightmapComplexity(sectionPosBelow, getComplexityChange(lightmapBelow.get(x, 15, z), oldVal, newVal));
            }
        }

        // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap

        if (this.changedSections.add(sectionPos)) {
            this.updatingSectionData.copyDataLayer(sectionPos);
            this.updateVanillaLightmapsBelow(sectionPos, this.getDataLayer(sectionPos, true), false);
        }
    }

    @Shadow
    protected abstract boolean hasSectionsBelow(final int sectionY);

    /**
     * Returns the first section below the provided <code>sectionPos</code> that {@link #storingLightForSection(long) supports light propagations} or {@link Long#MAX_VALUE} if no such section exists.
     */
    @Unique
    private long getSectionBelow(long sectionPos) {
        for (int y = SectionPos.y(sectionPos); this.hasSectionsBelow(y); --y) {
            if (this.storingLightForSection(sectionPos = SectionPos.offset(sectionPos, Direction.DOWN))) {
                return sectionPos;
            }
        }

        return Long.MAX_VALUE;
    }

    @Override
    protected int getLightmapComplexityChange(final long blockPos, final int oldVal, final int newVal, final NibbleArray lightmap) {
        final long sectionPos = SectionPos.blockToSection(blockPos);
        final int x = SectionPos.sectionRelative(BlockPos.getX(blockPos));
        final int y = SectionPos.sectionRelative(BlockPos.getY(blockPos));
        final int z = SectionPos.sectionRelative(BlockPos.getZ(blockPos));

        final int valAbove;

        if (y < 15) {
            valAbove = lightmap.get(x, y + 1, z);
        } else {
            final NibbleArray lightmapAbove = this.getLightmapAbove(sectionPos);
            valAbove = lightmapAbove == null ? this.getDirectSkylight(sectionPos) : lightmapAbove.get(x, 0, z);
        }

        int amount = getComplexityChange(valAbove, oldVal, newVal);

        if (y > 0) {
            amount += getComplexityChange(lightmap.get(x, y - 1, z), oldVal, newVal);
        }

        return amount;
    }

    /**
     * Returns the first lightmap above the provided <code>sectionPos</code> or <code>null</code> if none exists.
     */
    @Unique
    private NibbleArray getLightmapAbove(long sectionPos) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);

        return sectionPosAbove == Long.MAX_VALUE ? null : this.getDataLayer(sectionPosAbove, true);
    }

    /**
     * Returns the first section above the provided <code>sectionPos</code> that {@link #hasLightmap(long)}  has a lightmap} or {@link Long#MAX_VALUE} if none exists.
     */
    @Unique
    private long getSectionAbove(long sectionPos) {
        sectionPos = SectionPos.offset(sectionPos, Direction.UP);

        if (this.isAboveData(sectionPos)) {
            return Long.MAX_VALUE;
        }

        while (!this.hasLightmap(sectionPos)) {
            sectionPos = SectionPos.offset(sectionPos, Direction.UP);
        }

        return sectionPos;
    }

    @Unique
    private int getDirectSkylight(final long sectionPos) {
        return this.lightOnInSection(sectionPos) ? 15 : 0;
    }

    @Override
    protected void beforeLightmapChange(final long sectionPos, final NibbleArray oldLightmap, final NibbleArray newLightmap) {
        final long sectionPosBelow = this.getSectionBelow(sectionPos);

        if (sectionPosBelow != Long.MAX_VALUE) {
            final NibbleArray lightmapBelow = this.getDataLayer(sectionPosBelow, true);
            final NibbleArray lightmapAbove = oldLightmap == null ? this.getLightmapAbove(sectionPos) : oldLightmap;

            final int skyLight = this.getDirectSkylight(sectionPos);

            if (lightmapBelow == null) {
                int complexity = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        complexity += Math.abs(newLightmap.get(x, 0, z) - (lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z)));
                    }
                }

                if (complexity != 0) {
                    this.getOrAddLightmap(sectionPosBelow);
                    this.setLightmapComplexity(sectionPosBelow, complexity);
                }
            } else {
                int amount = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        amount += getComplexityChange(lightmapBelow.get(x, 15, z), lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z), newLightmap.get(x, 0, z));
                    }
                }

                this.changeLightmapComplexity(sectionPosBelow, amount);
            }
        }

        // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap

        this.updateVanillaLightmapsOnLightmapCreation(sectionPos, newLightmap);
    }

    @Override
    protected int getInitialLightmapComplexity(final long sectionPos, final NibbleArray lightmap) {
        int complexity = 0;

        for (int y = 0; y < 15; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    complexity += Math.abs(lightmap.get(x, y + 1, z) - lightmap.get(x, y, z));
                }
            }
        }

        final NibbleArray lightmapAbove = this.getLightmapAbove(sectionPos);
        final int skyLight = this.getDirectSkylight(sectionPos);

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                complexity += Math.abs((lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z)) - lightmap.get(x, 15, z));
            }
        }

        return complexity;
    }

    @Redirect(
        method = "onNodeRemoved(J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/lighting/SkyLightStorage;storingLightForSection(J)Z"
        )
    )
    private boolean hasActualLightmap(final SkyLightStorage lightStorage, long sectionPos) {
        return this.hasLightmap(sectionPos);
    }

    @Override
    public void setLevel(final long id, final int level) {
        final int oldLevel = this.getLevel(id);

        if (oldLevel >= 2 && level < 2) {
            ((SkyLightStorageDataAccess) (Object) this.updatingSectionData).updateMinHeight(SectionPos.y(id));
        }

        super.setLevel(id, level);
    }

    @Override
    protected NibbleArray createInitialVanillaLightmap(final long sectionPos) {
        // Attempt to restore data stripped from vanilla saves. See MC-198987

        if (!this.dataSectionSet.contains(sectionPos) && !this.dataSectionSet.contains(SectionPos.offset(sectionPos, Direction.UP))) {
            return this.createTrivialVanillaLightmap(sectionPos);
        }

        // A lightmap should have been present in this case unless it was stripped from the vanilla save or the chunk is loaded for the first time.
        // In both cases the lightmap should be initialized with zero.

        final long sectionPosAbove = this.getSectionAbove(sectionPos);
        final int complexity;

        if (sectionPosAbove == Long.MAX_VALUE) {
            complexity = this.lightOnInSection(sectionPos) ? 15 * 16 * 16 : 0;
        } else {
            complexity = this.vanillaLightmapComplexities.get(sectionPosAbove);
        }

        if (complexity == 0) {
            return this.createTrivialVanillaLightmap(null);
        }

        // Need to create an actual lightmap in this case as it is non-trivial

        final NibbleArray lightmap = new NibbleArray(new byte[2048]);
        this.updatingSectionData.setLayer(sectionPos, lightmap);
        this.updatingSectionData.clearCache();

        this.onNodeAdded(sectionPos);
        this.setLightmapComplexity(sectionPos, complexity);

        return lightmap;
    }

    @Override
    protected NibbleArray createTrivialVanillaLightmap(final long sectionPos) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);

        if (sectionPosAbove == Long.MAX_VALUE) {
            return this.createTrivialVanillaLightmap(this.lightOnInSection(sectionPos) ? DIRECT_SKYLIGHT_MAP : null);
        }

        return this.createTrivialVanillaLightmap(this.vanillaLightmapComplexities.get(sectionPosAbove) == 0 ? null : this.getDataLayer(sectionPosAbove, true));
    }

    @Unique
    private NibbleArray createTrivialVanillaLightmap(final NibbleArray lightmapAbove) {
        return lightmapAbove == null ? new EmptyChunkNibbleArray() : new SkyLightChunkNibbleArray(lightmapAbove);
    }

    @Inject(
        method = "onNodeAdded(J)V",
        at = @At("HEAD")
    )
    private void updateVanillaLightmapsOnLightmapCreation(final long sectionPos, final CallbackInfo ci) {
        this.updateVanillaLightmapsOnLightmapCreation(sectionPos, this.getDataLayer(sectionPos, true));
    }

    @Unique
    private void updateVanillaLightmapsOnLightmapCreation(final long sectionPos, final NibbleArray lightmap) {
        int complexity = 0;

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                complexity += lightmap.get(x, 0, z);
            }
        }

        this.vanillaLightmapComplexities.put(sectionPos, complexity);
        this.removedLightmaps.remove(sectionPos);

        // Enabling the chunk already creates all relevant vanilla lightmaps

        if (!this.enabledChunks.contains(SectionPos.getZeroNode(sectionPos))) {
            return;
        }

        // Vanilla lightmaps need to be re-parented immediately as the old parent can now be modified without informing them

        this.updateVanillaLightmapsBelow(sectionPos, complexity == 0 ? null : lightmap, false);
    }

    @Inject(
        method = "onNodeRemoved(J)V",
        at = @At("HEAD")
    )
    private void updateVanillaLightmapsOnLightmapRemoval(final long sectionPos, final CallbackInfo ci) {
        this.vanillaLightmapComplexities.remove(sectionPos);

        if (!this.enabledChunks.contains(SectionPos.getZeroNode(sectionPos))) {
            return;
        }

        // Re-parenting can be deferred as the removed parent is now unmodifiable

        this.removedLightmaps.add(sectionPos);
    }

    @Unique
    private void updateVanillaLightmapsBelow(final long sectionPos, final NibbleArray lightmapAbove, final boolean stopOnRemovedLightmap) {
        for (int y = SectionPos.y(sectionPos) - 1; this.hasSectionsBelow(y); --y) {
            final long sectionPosBelow = SectionPos.asLong(SectionPos.x(sectionPos), y, SectionPos.z(sectionPos));

            if (stopOnRemovedLightmap) {
                if (this.removedLightmaps.contains(sectionPosBelow)) {
                    break;
                }
            } else {
                this.removedLightmaps.remove(sectionPosBelow);
            }

            final NibbleArray lightmapBelow = this.getDataLayer(sectionPosBelow, true);

            if (lightmapBelow == null) {
                continue;
            }

            if (!((IReadonly) lightmapBelow).isReadonly()) {
                break;
            }

            this.updatingSectionData.setLayer(sectionPosBelow, this.createTrivialVanillaLightmap(lightmapAbove));
            this.changedSections.add(sectionPosBelow);
        }

        this.updatingSectionData.clearCache();
    }
}
