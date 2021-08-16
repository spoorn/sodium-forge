package me.jellysquid.mods.sodium.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.jellysquid.mods.phosphor.common.chunk.light.*;
import me.jellysquid.mods.phosphor.common.util.chunk.light.EmptyChunkNibbleArray;
import net.minecraft.util.Direction;
import net.minecraft.util.SectionDistanceGraph;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightDataMap;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SectionLightStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.StampedLock;

@SuppressWarnings("OverwriteModifiers")
@Mixin(SectionLightStorage.class)
public abstract class MixinLightStorage<M extends LightDataMap<M>> extends SectionDistanceGraph implements SharedLightStorageAccess<M>, LightStorageAccess {
    protected MixinLightStorage() {
        super(0, 0, 0);
    }

    @Shadow
    @Final
    protected M updatingSectionData;

    @Mutable
    @Shadow
    @Final
    protected LongSet changedSections;

    @Mutable
    @Shadow
    @Final
    protected LongSet sectionsAffectedByLightUpdates;

    @Shadow
    protected abstract int getLevel(long id);

    @Mutable
    @Shadow
    @Final
    protected LongSet dataSectionSet;

    @Mutable
    @Shadow
    @Final
    protected LongSet toMarkData;

    @Mutable
    @Shadow
    @Final
    protected LongSet toMarkNoData;

    @Mutable
    @Shadow
    @Final
    private LongSet toRemove;

    @Shadow
    protected abstract void onNodeAdded(long blockPos);

    @SuppressWarnings("unused")
    @Shadow
    protected volatile boolean hasToRemove;

    @Shadow
    protected volatile M visibleSectionData;

    @Shadow
    protected abstract NibbleArray createDataLayer(long pos);

    @Shadow
    @Final
    protected Long2ObjectMap<NibbleArray> queuedSections;

    @Shadow
    protected abstract boolean hasInconsistencies();

    @Shadow
    protected abstract void onNodeRemoved(long l);

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    @Shadow
    protected abstract void clearQueuedSectionBlocks(LightEngine<?, ?> storage, long blockChunkPos);

    @Shadow
    protected abstract NibbleArray getDataLayer(long sectionPos, boolean cached);

    @Shadow
    @Final
    private IChunkLightProvider chunkSource;

    @Shadow
    @Final
    private LightType layer;

    @Shadow
    @Final
    private LongSet untrustedSections;

    @Override
    @Invoker("getDataLayer")
    public abstract NibbleArray callGetLightSection(final long sectionPos, final boolean cached);

    @Shadow
    protected int getLevelFromSource(long id) {
        return 0;
    }

    private final StampedLock uncachedLightArraysLock = new StampedLock();

    /**
     * Replaces the two set of calls to unpack the XYZ coordinates from the input to just one, storing the result as local
     * variables.
     *
     * Additionally, this handles lookups for positions without an associated lightmap.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public int getStoredLevel(long blockPos) {
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);

        long chunk = SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));

        NibbleArray array = this.getDataLayer(chunk, true);

        if (array == null) {
            return this.getLightWithoutLightmap(blockPos);
        }

        return array.get(SectionPos.sectionRelative(x), SectionPos.sectionRelative(y),SectionPos.sectionRelative(z));
    }

    /**
     * An extremely important optimization is made here in regards to adding items to the pending notification set. The
     * original implementation attempts to add the coordinate of every chunk which contains a neighboring block position
     * even though a huge number of loop iterations will simply map to block positions within the same updating chunk.
     * <p>
     * Our implementation here avoids this by pre-calculating the min/max chunk coordinates so we can iterate over only
     * the relevant chunk positions once. This reduces what would always be 27 iterations to just 1-8 iterations.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public void setStoredLevel(long blockPos, int value) {
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);

        long chunkPos = SectionPos.asLong(x >> 4, y >> 4, z >> 4);

        final NibbleArray lightmap = this.getOrAddLightmap(chunkPos);
        final int oldVal = lightmap.get(x & 15, y & 15, z & 15);

        this.beforeLightChange(blockPos, oldVal, value, lightmap);
        this.changeLightmapComplexity(chunkPos, this.getLightmapComplexityChange(blockPos, oldVal, value, lightmap));

        if (this.changedSections.add(chunkPos)) {
            this.updatingSectionData.copyDataLayer(chunkPos);
        }

        NibbleArray nibble = this.getDataLayer(chunkPos, true);
        nibble.set(x & 15, y & 15, z & 15, value);

        for (int z2 = (z - 1) >> 4; z2 <= (z + 1) >> 4; ++z2) {
            for (int x2 = (x - 1) >> 4; x2 <= (x + 1) >> 4; ++x2) {
                for (int y2 = (y - 1) >> 4; y2 <= (y + 1) >> 4; ++y2) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(x2, y2, z2));
                }
            }
        }
    }

    /**
     * @author PhiPro
     * @reason Move large parts of the logic to other methods
     */
    @Overwrite
    public void setLevel(long id, int level) {
        int oldLevel = this.getLevel(id);

        if (oldLevel != 0 && level == 0) {
            this.dataSectionSet.add(id);
            this.toMarkData.remove(id);
        }

        if (oldLevel == 0 && level != 0) {
            this.dataSectionSet.remove(id);
            this.toMarkNoData.remove(id);
        }

        if (oldLevel >= 2 && level < 2) {
            this.nonOptimizableSections.add(id);

            if (this.enabledChunks.contains(SectionPos.getZeroNode(id)) && !this.vanillaLightmapsToRemove.remove(id) && this.getDataLayer(id, true) == null) {
                this.updatingSectionData.setLayer(id, this.createTrivialVanillaLightmap(id));
                this.changedSections.add(id);
                this.updatingSectionData.clearCache();
            }
        }

        if (oldLevel < 2 && level >= 2) {
            this.nonOptimizableSections.remove(id);

            if (this.enabledChunks.contains(id)) {
                final NibbleArray lightmap = this.getDataLayer(id, true);

                if (lightmap != null && ((IReadonly) lightmap).isReadonly()) {
                    this.vanillaLightmapsToRemove.add(id);
                }
            }
        }
    }

    /**
     * @reason Drastically improve efficiency by making removals O(n) instead of O(16*16*16)
     * @author JellySquid
     */
    @Inject(method = "clearQueuedSectionBlocks", at = @At("HEAD"), cancellable = true)
    protected void preRemoveSection(LightEngine<?, ?> provider, long pos, CallbackInfo ci) {
        if (provider instanceof LightProviderUpdateTracker) {
            ((LightProviderUpdateTracker) provider).cancelUpdatesForChunk(pos);

            ci.cancel();
        }
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void markNewInconsistencies(LightEngine<M, ?> chunkLightProvider, boolean doSkylight, boolean skipEdgeLightPropagation) {
        if (!this.hasInconsistencies()) {
            return;
        }

        this.initializeChunks();
        this.removeChunks(chunkLightProvider);
        this.removeTrivialLightmaps(chunkLightProvider);
        this.removeVanillaLightmaps(chunkLightProvider);
        this.addQueuedLightmaps(chunkLightProvider);

        final LongIterator it;

        if (!skipEdgeLightPropagation) {
            it = this.queuedSections.keySet().iterator();
        } else {
            it = this.untrustedSections.iterator();
        }
        
        while (it.hasNext()) {
            checkEdgesForSection(chunkLightProvider, it.nextLong());
        }

        this.untrustedSections.clear();
        this.queuedSections.clear();

        // Vanilla would normally iterate back over the map of light arrays to remove those we worked on, but
        // that is unneeded now because we removed them earlier.

        this.hasToRemove = false;
    }

    /**
     * @reason Avoid integer boxing, reduce map lookups and iteration as much as possible
     * @author JellySquid
     */
    @Overwrite
    private void checkEdgesForSection(LightEngine<M, ?> chunkLightProvider, long pos) {
        if (this.storingLightForSection(pos)) {
            int x = SectionPos.sectionToBlockCoord(SectionPos.x(pos));
            int y = SectionPos.sectionToBlockCoord(SectionPos.x(pos));
            int z = SectionPos.sectionToBlockCoord(SectionPos.x(pos));

            for (Direction dir : DIRECTIONS) {
                long adjPos = SectionPos.offset(pos, dir);

                // Avoid updating initializing chunks unnecessarily
                if (this.queuedSections.containsKey(adjPos)) {
                    continue;
                }

                // If there is no light data for this section yet, skip it
                if (!this.storingLightForSection(adjPos)) {
                    continue;
                }

                for (int u1 = 0; u1 < 16; ++u1) {
                    for (int u2 = 0; u2 < 16; ++u2) {
                        long a;
                        long b;

                        switch (dir) {
                            case DOWN:
                                a = BlockPos.asLong(x + u2, y, z + u1);
                                b = BlockPos.asLong(x + u2, y - 1, z + u1);
                                break;
                            case UP:
                                a = BlockPos.asLong(x + u2, y + 15, z + u1);
                                b = BlockPos.asLong(x + u2, y + 16, z + u1);
                                break;
                            case NORTH:
                                a = BlockPos.asLong(x + u1, y + u2, z);
                                b = BlockPos.asLong(x + u1, y + u2, z - 1);
                                break;
                            case SOUTH:
                                a = BlockPos.asLong(x + u1, y + u2, z + 15);
                                b = BlockPos.asLong(x + u1, y + u2, z + 16);
                                break;
                            case WEST:
                                a = BlockPos.asLong(x, y + u1, z + u2);
                                b = BlockPos.asLong(x - 1, y + u1, z + u2);
                                break;
                            case EAST:
                                a = BlockPos.asLong(x + 15, y + u1, z + u2);
                                b = BlockPos.asLong(x + 16, y + u1, z + u2);
                                break;
                            default:
                                continue;
                        }

                        ((LightInitializer) chunkLightProvider).spreadLightInto(a, b);
                    }
                }
            }
        }
    }

    /**
     * @reason
     * @author JellySquid
     */
    @Overwrite
    public void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            // This could result in changes being flushed to various arrays, so write lock.
            long stamp = this.uncachedLightArraysLock.writeLock();

            try {
                // This only performs a shallow copy compared to before
                M map = this.updatingSectionData.copy();
                map.disableCache();

                this.visibleSectionData = map;
            } finally {
                this.uncachedLightArraysLock.unlockWrite(stamp);
            }

            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator it = this.sectionsAffectedByLightUpdates.iterator();

            while(it.hasNext()) {
                long pos = it.nextLong();

                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(pos));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    @Override
    public M getStorage() {
        return this.visibleSectionData;
    }

    @Override
    public StampedLock getStorageLock() {
        return this.uncachedLightArraysLock;
    }

    @Override
    public int getLightWithoutLightmap(final long blockPos) {
        return 0;
    }

    @Unique
    protected void beforeChunkEnabled(final long chunkPos) {
    }

    @Unique
    protected void afterChunkDisabled(final long chunkPos) {
    }

    @Unique
    protected final LongSet enabledChunks = new LongOpenHashSet();
    @Unique
    protected final Long2IntMap lightmapComplexities = setDefaultReturnValue(new Long2IntOpenHashMap(), -1);

    @Unique
    private final LongSet markedEnabledChunks = new LongOpenHashSet();
    @Unique
    private final LongSet markedDisabledChunks = new LongOpenHashSet();
    @Unique
    private final LongSet trivialLightmaps = new LongOpenHashSet();
    @Unique
    private final LongSet vanillaLightmapsToRemove = new LongOpenHashSet();

    // This is put here since the relevant methods to overwrite are located in LightStorage
    @Unique
    protected LongSet nonOptimizableSections = new LongOpenHashSet();

    @Unique
    private static Long2IntMap setDefaultReturnValue(final Long2IntMap map, final int rv) {
        map.defaultReturnValue(rv);
        return map;
    }

    @Unique
    protected NibbleArray getOrAddLightmap(final long sectionPos) {
        NibbleArray lightmap = this.getDataLayer(sectionPos, true);

        if (lightmap == null) {
            lightmap = this.createDataLayer(sectionPos);
        } else {
            if (((IReadonly) lightmap).isReadonly()) {
                lightmap = lightmap.copy();
                this.vanillaLightmapsToRemove.remove(sectionPos);
            } else {
                return lightmap;
            }
        }

        this.updatingSectionData.setLayer(sectionPos, lightmap);
        this.updatingSectionData.clearCache();
        this.changedSections.add(sectionPos);

        this.onNodeAdded(sectionPos);
        this.setLightmapComplexity(sectionPos, 0);

        return lightmap;
    }

    @Unique
    protected void setLightmapComplexity(final long sectionPos, final int complexity) {
        int oldComplexity = this.lightmapComplexities.put(sectionPos, complexity);

        if (oldComplexity == 0) {
            this.trivialLightmaps.remove(sectionPos);
        }

        if (complexity == 0) {
            this.trivialLightmaps.add(sectionPos);
            this.markForLightUpdates();
        }
    }

    @Unique
    private void markForLightUpdates() {
        // Avoid volatile writes
        if (!this.hasToRemove) {
            this.hasToRemove = true;
        }
    }

    @Unique
    protected void changeLightmapComplexity(final long sectionPos, final int amount) {
        int complexity = this.lightmapComplexities.get(sectionPos);

        if (complexity == 0) {
            this.trivialLightmaps.remove(sectionPos);
        }

        complexity += amount;
        this.lightmapComplexities.put(sectionPos, complexity);

        if (complexity == 0) {
            this.trivialLightmaps.add(sectionPos);
            this.markForLightUpdates();
        }
    }

    @Unique
    protected NibbleArray getLightmap(final long sectionPos) {
        final NibbleArray lightmap = this.getDataLayer(sectionPos, true);
        return lightmap == null || ((IReadonly) lightmap).isReadonly() ? null : lightmap;
    }

    @Unique
    protected boolean hasLightmap(final long sectionPos) {
        return this.getLightmap(sectionPos) != null;
    }

    /**
     * Set up lightmaps and adjust complexities as needed for the given light change.
     * Actions are only required for other affected positions, not for the given <code>blockPos</code> directly.
     */
    @Unique
    protected void beforeLightChange(final long blockPos, final int oldVal, final int newVal, final NibbleArray lightmap) {
    }

    @Unique
    protected int getLightmapComplexityChange(final long blockPos, final int oldVal, final int newVal, final NibbleArray lightmap) {
        return 0;
    }

    /**
     * Set up lightmaps and adjust complexities as needed for the given lightmap change.
     * Actions are only required for other affected sections, not for the given <code>sectionPos</code> directly.
     */
    @Unique
    protected void beforeLightmapChange(final long sectionPos, final NibbleArray oldLightmap, final NibbleArray newLightmap) {
    }

    @Unique
    protected int getInitialLightmapComplexity(final long sectionPos, final NibbleArray lightmap) {
        return 0;
    }

    /**
     * Determines whether light updates should be propagated into the given section.
     * @author PhiPro
     * @reason Method completely changed. Allow child mixins to properly extend this.
     */
    @Overwrite
    public boolean storingLightForSection(final long sectionPos) {
        return this.enabledChunks.contains(SectionPos.getZeroNode(sectionPos));
    }

    @Shadow
    protected abstract void enableLightSources(long columnPos, boolean enabled);

    @Override
    @Invoker("enableLightSources")
    public abstract void invokeSetColumnEnabled(final long chunkPos, final boolean enabled);

    @Override
    public void setLightUpdatesEnabled(final long chunkPos, final boolean enabled) {
        if (enabled) {
            if (this.markedDisabledChunks.remove(chunkPos) || this.enabledChunks.contains(chunkPos)) {
                return;
            }

            this.markedEnabledChunks.add(chunkPos);
            this.markForLightUpdates();
        } else {
            if (this.markedEnabledChunks.remove(chunkPos) || !this.enabledChunks.contains(chunkPos)) {
                for (int i = -1; i < 17; ++i) {
                    final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                    if (this.updatingSectionData.removeLayer(sectionPos) != null) {
                        this.changedSections.add(sectionPos);
                    }
                }

                this.enableLightSources(chunkPos, false);
            } else {
                this.markedDisabledChunks.add(chunkPos);
                this.markForLightUpdates();
            }
        }
    }

    @Unique
    private void initializeChunks() {
        this.updatingSectionData.clearCache();

        for (final LongIterator it = this.markedEnabledChunks.iterator(); it.hasNext(); ) {
            final long chunkPos = it.nextLong();

            this.beforeChunkEnabled(chunkPos);

            // First need to register all lightmaps via onLoadSection() as this data is needed for calculating the initial complexity

            for (int i = -1; i < 17; ++i) {
                final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                if (this.hasLightmap(sectionPos)) {
                    this.onNodeAdded(sectionPos);
                }
            }

            // Now the initial complexities can be computed

            for (int i = -1; i < 17; ++i) {
                final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                if (this.hasLightmap(sectionPos)) {
                    this.setLightmapComplexity(sectionPos, this.getInitialLightmapComplexity(sectionPos, this.getDataLayer(sectionPos, true)));
                }
            }

            // Add lightmaps for vanilla compatibility and try to recover stripped data from vanilla saves

            for (int i = -1; i < 17; ++i) {
                final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                if (this.nonOptimizableSections.contains(sectionPos) && this.getDataLayer(sectionPos, true) == null) {
                    this.updatingSectionData.setLayer(sectionPos, this.createInitialVanillaLightmap(sectionPos));
                    this.changedSections.add(sectionPos);
                }
            }

            this.enabledChunks.add(chunkPos);
        }

        this.updatingSectionData.clearCache();

        this.markedEnabledChunks.clear();
    }

    @Unique
    protected NibbleArray createInitialVanillaLightmap(final long sectionPos) {
        return this.createTrivialVanillaLightmap(sectionPos);
    }

    @Unique
    protected NibbleArray createTrivialVanillaLightmap(final long sectionPos) {
        return new EmptyChunkNibbleArray();
    }

    @Unique
    private void removeChunks(final LightEngine<?, ?> lightProvider) {
        for (final LongIterator it = this.markedDisabledChunks.iterator(); it.hasNext(); ) {
            final long chunkPos = it.nextLong();

            // First need to remove all pending light updates before changing any light value

            for (int i = -1; i < 17; ++i) {
                final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                if (this.storingLightForSection(sectionPos)) {
                    this.clearQueuedSectionBlocks(lightProvider, sectionPos);
                }
            }

            // Now the chunk can be disabled

            this.enabledChunks.remove(chunkPos);

            // Now lightmaps can be removed

            int sections = 0;

            for (int i = -1; i < 17; ++i) {
                final long sectionPos = SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos));

                this.queuedSections.remove(sectionPos);

                if (this.removeLightmap(sectionPos)) {
                    sections |= 1 << (i + 1);
                }
            }

            // Calling onUnloadSection() after removing all the lightmaps is slightly more efficient

            this.updatingSectionData.clearCache();

            for (int i = -1; i < 17; ++i) {
                if ((sections & (1 << (i + 1))) != 0) {
                    this.onNodeRemoved(SectionPos.asLong(SectionPos.x(chunkPos), i, SectionPos.z(chunkPos)));
                }
            }

            this.enableLightSources(chunkPos, false);
            this.afterChunkDisabled(chunkPos);
        }

        this.markedDisabledChunks.clear();
    }

    /**
     * Removes the lightmap associated to the provided <code>sectionPos</code>, but does not call {@link #onNodeRemoved(long)} or {@link LightDataMap#invalidateCaches()}
     * @return Whether a lightmap was removed
     */
    @Unique
    protected boolean removeLightmap(final long sectionPos) {
        if (this.updatingSectionData.removeLayer(sectionPos) == null) {
            return false;
        }

        this.changedSections.add(sectionPos);

        if (this.lightmapComplexities.remove(sectionPos) == -1) {
            this.vanillaLightmapsToRemove.remove(sectionPos);
            return false;
        } else {
            this.trivialLightmaps.remove(sectionPos);
            return true;
        }
    }

    @Unique
    private void removeTrivialLightmaps(final LightEngine<?, ?> lightProvider) {
        for (final LongIterator it = this.trivialLightmaps.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            this.updatingSectionData.removeLayer(sectionPos);
            this.lightmapComplexities.remove(sectionPos);
            this.changedSections.add(sectionPos);
        }

        this.updatingSectionData.clearCache();

        // Calling onUnloadSection() after removing all the lightmaps is slightly more efficient

        for (final LongIterator it = this.trivialLightmaps.iterator(); it.hasNext(); ) {
            this.onNodeRemoved(it.nextLong());
        }

        // Add trivial lightmaps for vanilla compatibility

        for (final LongIterator it = this.trivialLightmaps.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            if (this.nonOptimizableSections.contains(sectionPos)) {
                this.updatingSectionData.setLayer(sectionPos, this.createTrivialVanillaLightmap(sectionPos));
            }
        }

        this.updatingSectionData.clearCache();

        // Remove pending light updates for sections that no longer support light propagations

        for (final LongIterator it = this.trivialLightmaps.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            if (!this.storingLightForSection(sectionPos)) {
                this.clearQueuedSectionBlocks(lightProvider, sectionPos);
            }
        }

        this.trivialLightmaps.clear();
    }

    @Unique
    private void removeVanillaLightmaps(final LightEngine<?, ?> lightProvider) {
        for (final LongIterator it = this.vanillaLightmapsToRemove.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            this.updatingSectionData.removeLayer(sectionPos);
            this.changedSections.add(sectionPos);
        }

        this.updatingSectionData.clearCache();

        // Remove pending light updates for sections that no longer support light propagations

        for (final LongIterator it = this.vanillaLightmapsToRemove.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            if (!this.storingLightForSection(sectionPos)) {
                this.clearQueuedSectionBlocks(lightProvider, sectionPos);
            }
        }

        this.vanillaLightmapsToRemove.clear();
    }

    @Unique
    private void addQueuedLightmaps(final LightEngine<?, ?> lightProvider) {
        for (final ObjectIterator<Long2ObjectMap.Entry<NibbleArray>> it = Long2ObjectMaps.fastIterator(this.queuedSections); it.hasNext(); ) {
            final Long2ObjectMap.Entry<NibbleArray> entry = it.next();

            final long sectionPos = entry.getLongKey();
            final NibbleArray lightmap = entry.getValue();

            final NibbleArray oldLightmap = this.getLightmap(sectionPos);

            if (lightmap != oldLightmap) {
                this.clearQueuedSectionBlocks(lightProvider, sectionPos);

                this.beforeLightmapChange(sectionPos, oldLightmap, lightmap);

                this.updatingSectionData.setLayer(sectionPos, lightmap);
                this.updatingSectionData.clearCache();
                this.changedSections.add(sectionPos);

                if (oldLightmap == null) {
                    this.onNodeAdded(sectionPos);
                }

                this.vanillaLightmapsToRemove.remove(sectionPos);
                this.setLightmapComplexity(sectionPos, this.getInitialLightmapComplexity(sectionPos, lightmap));
            }
        }
    }

    /**
     * @author PhiPro
     * @reason Add lightmaps for disabled chunks directly to the world
     */
    @Overwrite
    public void queueSectionData(final long sectionPos, final NibbleArray array, final boolean bl) {
        final boolean chunkEnabled = this.enabledChunks.contains(SectionPos.getZeroNode(sectionPos));

        if (array != null) {
            if (chunkEnabled) {
                this.queuedSections.put(sectionPos, array);
                this.markForLightUpdates();
            } else {
                this.updatingSectionData.setLayer(sectionPos, array);
                this.changedSections.add(sectionPos);
            }

            if (!bl) {
                this.untrustedSections.add(sectionPos);
            }
        } else {
            if (chunkEnabled) {
                this.queuedSections.remove(sectionPos);
            } else {
                this.updatingSectionData.removeLayer(sectionPos);
                this.changedSections.add(sectionPos);
            }
        }
    }

    // Queued lightmaps are only added to the world via updateLightmaps()
    @Redirect(
        method = "createDataLayer(J)Lnet/minecraft/world/chunk/NibbleArray;",
        slice = @Slice(
            from = @At(
                value = "FIELD",
                target = "Lnet/minecraft/world/lighting/SectionLightStorage;queuedSections:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
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

    @Redirect(
        method = "getLevel(J)I",
        slice = @Slice(
            from = @At(
                value = "FIELD",
                target = "Lnet/minecraft/world/lighting/SectionLightStorage;updatingSectionData:Lnet/minecraft/world/lighting/LightDataMap;",
                opcode = Opcodes.GETFIELD
            )
        ),
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/lighting/LightDataMap;hasLayer(J)Z",
            ordinal = 0
        )
    )
    private boolean isNonOptimizable(final LightDataMap<?> lightmapArray, final long sectionPos) {
        return this.nonOptimizableSections.contains(sectionPos);
    }
}
