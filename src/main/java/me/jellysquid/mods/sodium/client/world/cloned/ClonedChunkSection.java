package me.jellysquid.mods.sodium.client.world.cloned;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.world.cloned.palette.ClonedPalette;
import me.jellysquid.mods.sodium.client.world.cloned.palette.ClonedPaletteFallback;
import me.jellysquid.mods.sodium.client.world.cloned.palette.ClonedPalleteArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClonedChunkSection {
    private static final LightLayer[] LIGHT_TYPES = LightLayer.values();
    private static final LevelChunkSection EMPTY_SECTION = new LevelChunkSection(0);

    private final AtomicInteger referenceCount = new AtomicInteger(0);
    private final ClonedChunkSectionCache backingCache;

    private final Long2ObjectOpenHashMap<BlockEntity> blockEntities;
    private final DataLayer[] lightDataArrays;
    private final Level world;

    private SectionPos pos;

    private BitStorage blockStateData;
    private ClonedPalette<BlockState> blockStatePalette;

    private ChunkBiomeContainer biomeData;

    ClonedChunkSection(ClonedChunkSectionCache backingCache, Level world) {
        this.backingCache = backingCache;
        this.world = world;
        this.blockEntities = new Long2ObjectOpenHashMap<>(8);
        this.lightDataArrays = new DataLayer[LIGHT_TYPES.length];
    }

    public void init(SectionPos pos) {
        LevelChunk chunk = world.getChunk(pos.getX(), pos.getZ());

        if (chunk == null) {
            throw new RuntimeException("Couldn't retrieve chunk at " + pos.chunk());
        }

        LevelChunkSection section = getChunkSection(world, chunk, pos);

        if (LevelChunkSection.isEmpty(section)) {
            section = EMPTY_SECTION;
        }

        this.pos = pos;

        PalettedContainerExtended<BlockState> container = PalettedContainerExtended.cast(section.getStates());;

        this.blockStateData = copyBlockData(container);
        this.blockStatePalette = copyPalette(container);

        for (LightLayer type : LIGHT_TYPES) {
            this.lightDataArrays[type.ordinal()] = world.getLightEngine()
                    .getLayerListener(type)
                    .getDataLayerData(pos);
        }

        this.biomeData = chunk.getBiomes();

        BoundingBox box = new BoundingBox(pos.minBlockX(), pos.minBlockY(), pos.minBlockZ(),
                pos.maxBlockX(), pos.maxBlockY(), pos.maxBlockZ());

        this.blockEntities.clear();

        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos entityPos = entry.getKey();

            if (box.isInside(entityPos)) {
                this.blockEntities.put(BlockPos.asLong(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), entry.getValue());
            }
        }
    }

    public BlockState getBlockState(int x, int y, int z) {
        return this.blockStatePalette.get(this.blockStateData.get(y << 8 | z << 4 | x));
    }

    public int getLightLevel(LightLayer type, int x, int y, int z) {
        DataLayer array = this.lightDataArrays[type.ordinal()];

        if (array != null) {
            return array.get(x, y, z);
        }

        return 0;
    }

    public Biome getBiomeForNoiseGen(int x, int y, int z) {
        return this.biomeData.getNoiseBiome(x, y, z);
    }

    public BlockEntity getBlockEntity(int x, int y, int z) {
        return this.blockEntities.get(BlockPos.asLong(x, y, z));
    }

    public BitStorage getBlockData() {
        return this.blockStateData;
    }

    public ClonedPalette<BlockState> getBlockPalette() {
        return this.blockStatePalette;
    }

    public SectionPos getPosition() {
        return this.pos;
    }

    private static ClonedPalette<BlockState> copyPalette(PalettedContainerExtended<BlockState> container) {
        Palette<BlockState> palette = container.getPalette();

        if (palette instanceof GlobalPalette) {
            // TODO: Use GameRegistry
            return new ClonedPaletteFallback<>(Block.BLOCK_STATE_REGISTRY);
        }

        BlockState[] array = new BlockState[1 << container.getPaletteSize()];

        for (int i = 0; i < array.length; i++) {
            array[i] = palette.valueFor(i);

            if (array[i] == null) {
                break;
            }
        }

        return new ClonedPalleteArray<>(array, container.getDefaultValue());
    }

    private static BitStorage copyBlockData(PalettedContainerExtended<BlockState> container) {
        BitStorage array = container.getDataArray();
        long[] storage = array.getRaw();

        return new BitStorage(container.getPaletteSize(), array.getSize(), storage.clone());
    }

    private static LevelChunkSection getChunkSection(Level level, LevelChunk chunk, SectionPos pos) {
        LevelChunkSection section = null;

        if (!level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(pos.getY()))) {
            section = chunk.getSections()[pos.getY()];
        }

        return section;
    }

    public void acquireReference() {
        this.referenceCount.incrementAndGet();
    }

    public boolean releaseReference() {
        return this.referenceCount.decrementAndGet() <= 0;
    }

    public ClonedChunkSectionCache getBackingCache() {
        return this.backingCache;
    }
}
