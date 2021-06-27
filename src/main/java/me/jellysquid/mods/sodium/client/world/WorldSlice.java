package me.jellysquid.mods.sodium.client.world;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.world.biome.BiomeCache;
import me.jellysquid.mods.sodium.client.world.biome.BiomeCacheManager;
import me.jellysquid.mods.sodium.client.world.biome.BiomeColorCache;
import me.jellysquid.mods.sodium.common.util.pool.ReusableObject;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BitArray;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.palette.IPalette;
import net.minecraft.util.palette.PalettedContainer;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.lighting.WorldLightManager;

import java.util.Arrays;
import java.util.Map;

/**
 * Takes a slice of world state (block states, biome and light data arrays) and copies the data for use in off-thread
 * operations. This allows chunk build tasks to see a consistent snapshot of chunk data at the exact moment the task was
 * created.
 *
 * World slices are not safe to use from multiple threads at once, but the data they contain is safe from modification
 * by the main client thread.
 *
 * You should use object pooling with this type to avoid huge allocations as instances of this class contain many large
 * arrays.
 */
public class WorldSlice extends ReusableObject implements IBlockDisplayReader, BiomeManager.IBiomeReader {
    private static final ChunkSection EMPTY_SECTION = new ChunkSection(0);

    // The number of blocks on each axis in a section.
    private static final int SECTION_BLOCK_LENGTH = 16;

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

    // The number of outward blocks from the origin chunk to slice
    public static final int NEIGHBOR_BLOCK_RADIUS = 1;

    // The number of outward chunks from the origin chunk to slice
    public static final int NEIGHBOR_CHUNK_RADIUS = MathHelper.roundUp(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of blocks on each axis of this slice.
    private static final int BLOCK_LENGTH = SECTION_BLOCK_LENGTH + (NEIGHBOR_BLOCK_RADIUS * 2);

    // The number of sections on each axis of this slice.
    private static final int SECTION_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The number of blocks contained by a world slice
    public static final int BLOCK_COUNT = BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH;

    // The number of chunk sections contained by a world slice
    public static final int SECTION_COUNT = SECTION_LENGTH * SECTION_LENGTH * SECTION_LENGTH;

    // The number of chunks contained by a world slice
    public static final int CHUNK_COUNT = SECTION_LENGTH * SECTION_LENGTH;

    // The size of the lookup tables used for mapping values to coordinate int pairs. The lookup table size is always
    // a power of two so that multiplications can be replaced with simple bit shifts in hot code paths.
    private static final int TABLE_LENGTH = MathHelper.smallestEncompassingPowerOfTwo(SECTION_LENGTH);

    // The number of bits needed for each X/Y/Z component in a lookup table.
    private static final int TABLE_BITS = Integer.bitCount(TABLE_LENGTH - 1);

    // The array size for the chunk lookup table.
    private static final int CHUNK_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH;

    // The array size for the section lookup table.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // Local Section->BlockState table. Read-only.
    private final BlockState[][] blockStatesArrays;

    // A pointer to the BlockState array for the origin section.
    private final BlockState[] originBlockStates;

    // The data arrays for this slice
    // These are allocated once and then re-used when the slice is released back to an object pool
    private final BlockState[] blockStates;
    private final NibbleArray[] blockLightArrays;
    private final NibbleArray[] skyLightArrays;
    private final BiomeCache[] biomeCaches;
    private final BiomeContainer[] biomeArrays;

    // The biome blend caches for each color resolver type
    // This map is always re-initialized, but the caches themselves are taken from an object pool
    private final Map<ColorResolver, BiomeColorCache> colorResolvers = new Reference2ObjectOpenHashMap<>();

    // The previously accessed and cached color resolver, used in conjunction with the cached color cache field
    private ColorResolver prevColorResolver;

    // The cached lookup result for the previously accessed color resolver to avoid excess hash table accesses
    // for vertex color blending
    private BiomeColorCache prevColorCache;

    // The world this slice has copied data from
    private World world;

    // Pointers to the chunks this slice encompasses
    private Chunk[] chunks;

    private BiomeCacheManager biomeCacheManager;

    // The starting point from which this slice captures chunks
    private int chunkOffsetX, chunkOffsetY, chunkOffsetZ;

    // The starting point from which this slice captures blocks
    private int blockOffsetX, blockOffsetY, blockOffsetZ;

    // The starting point from which this slice captures blocks
    private int baseX, baseY, baseZ;

    // The min/max bounds of the blocks copied by this slice
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    // The chunk origin of this slice
    private SectionPos origin;

    public static Chunk[] createChunkSlice(World world, SectionPos pos) {
        Chunk chunk = world.getChunk(pos.getX(), pos.getZ());
        ChunkSection section = chunk.getSections()[pos.getY()];

        // If the chunk section is absent or empty, simply terminate now. There will never be anything in this chunk
        // section to render, so we need to signal that a chunk render task shouldn't created. This saves a considerable
        // amount of time in queueing instant build tasks and greatly accelerates how quickly the world can be loaded.
        if (section == null || section.isEmpty()) {
            return null;
        }

        int minChunkX = pos.getX() - NEIGHBOR_CHUNK_RADIUS;
        int minChunkZ = pos.getZ() - NEIGHBOR_CHUNK_RADIUS;

        int maxChunkX = pos.getX() + NEIGHBOR_CHUNK_RADIUS;
        int maxChunkZ = pos.getZ() + NEIGHBOR_CHUNK_RADIUS;

        Chunk[] chunks = new Chunk[SECTION_LENGTH * SECTION_LENGTH];

        // Create an array of references to the world chunks in this slice
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks[getLocalChunkIndex(x - minChunkX, z - minChunkZ)] = world.getChunk(x, z);
            }
        }

        return chunks;
    }

    public WorldSlice() {
        this.blockStates = new BlockState[BLOCK_COUNT];
        this.blockLightArrays = new NibbleArray[SECTION_COUNT];
        this.skyLightArrays = new NibbleArray[SECTION_COUNT];
        this.biomeCaches = new BiomeCache[CHUNK_COUNT];
        this.biomeArrays = new BiomeContainer[CHUNK_COUNT];

        this.blockStatesArrays = new BlockState[SECTION_TABLE_ARRAY_SIZE][];

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    this.blockStatesArrays[getLocalSectionIndex(x, y, z)] = new BlockState[SECTION_BLOCK_COUNT];
                }
            }
        }

        this.originBlockStates = this.blockStatesArrays[getLocalSectionIndex((SECTION_LENGTH / 2), (SECTION_LENGTH / 2), (SECTION_LENGTH / 2))];
    }

    public void init(ChunkBuilder<?> builder, World world, SectionPos origin, Chunk[] chunks) {
        this.origin = origin;

        final int minX = origin.getWorldStartX() - NEIGHBOR_BLOCK_RADIUS;
        final int minY = origin.getWorldStartY() - NEIGHBOR_BLOCK_RADIUS;
        final int minZ = origin.getWorldStartZ() - NEIGHBOR_BLOCK_RADIUS;

        final int maxX = origin.getWorldEndX() + NEIGHBOR_BLOCK_RADIUS + 1;
        final int maxY = origin.getWorldEndY() + NEIGHBOR_BLOCK_RADIUS + 1;
        final int maxZ = origin.getWorldEndZ() + NEIGHBOR_BLOCK_RADIUS + 1;

        final int minChunkX = minX >> 4;
        final int maxChunkX = maxX >> 4;

        final int minChunkY = minY >> 4;
        final int maxChunkY = maxY >> 4;

        final int minChunkZ = minZ >> 4;
        final int maxChunkZ = maxZ >> 4;

        this.world = world;
        this.chunks = chunks;

        this.blockOffsetX = minX;
        this.blockOffsetY = minY;
        this.blockOffsetZ = minZ;

        this.chunkOffsetX = origin.getX() - NEIGHBOR_CHUNK_RADIUS;
        this.chunkOffsetY = origin.getY() - NEIGHBOR_CHUNK_RADIUS;
        this.chunkOffsetZ = origin.getZ() - NEIGHBOR_CHUNK_RADIUS;

        // Hoist the lighting providers so that they can be directly accessed
        IWorldLightListener blockLightProvider = this.world.getLightManager().getLightEngine(LightType.BLOCK);
        IWorldLightListener skyLightProvider = this.world.getLightManager().getLightEngine(LightType.SKY);

        // Iterate over all sliced chunks
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Find the local position of the chunk in the sliced chunk array
                int chunkXLocal = chunkX - this.chunkOffsetX;
                int chunkZLocal = chunkZ - this.chunkOffsetZ;

                // The local index for this chunk in the slice's data arrays
                int chunkIdx = getLocalChunkIndex(chunkXLocal, chunkZLocal);

                Chunk chunk = chunks[chunkIdx];

                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    SectionPos pos = SectionPos.of(chunkX, chunkY, chunkZ);

                    // Find the local position of the chunk in the sliced chunk array
                    int sectionIdx = getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ);

                    //this.populateLightArrays(sectionIdx, pos);
                    this.populateBlockArrays(sectionIdx, pos, chunk);
                }

                this.biomeArrays[chunkIdx] = chunk.getBiomes();

                int minBlockX = Math.max(minX, chunkX << 4);
                int maxBlockX = Math.min(maxX, (chunkX + 1) << 4);

                int minBlockZ = Math.max(minZ, chunkZ << 4);
                int maxBlockZ = Math.min(maxZ, (chunkZ + 1) << 4);

                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    int chunkYLocal = chunkY - this.chunkOffsetY;

                    SectionPos sectionPos = SectionPos.of(chunkX, chunkY, chunkZ);
                    int sectionIdx = getLocalSectionIndex2(chunkXLocal, chunkYLocal, chunkZLocal);

                    this.blockLightArrays[sectionIdx] = blockLightProvider.getData(sectionPos);
                    this.skyLightArrays[sectionIdx] = skyLightProvider.getData(sectionPos);

                    ChunkSection section = null;

                    // Fetch the chunk section for this position if it's within bounds
                    if (chunkY >= 0 && chunkY < 16) {
                        section = chunk.getSections()[chunkY];
                    }

                    // If no chunk section has been fetched, use an empty one which will return air blocks in the copy below
                    if (section == null) {
                        section = EMPTY_SECTION;
                    }

                    int minBlockY = Math.max(minY, chunkY << 4);
                    int maxBlockY = Math.min(maxY, (chunkY + 1) << 4);

                    // Iterate over all block states in the overlapping section between this world slice and chunk section
                    for (int y = minBlockY; y < maxBlockY; y++) {
                        for (int z = minBlockZ; z < maxBlockZ; z++) {
                            for (int x = minBlockX; x < maxBlockX; x++) {
                                this.blockStates[this.getBlockIndex(x, y, z)] = section.getBlockState(x & 15, y & 15, z & 15);
                            }
                        }
                    }
                }
            }
        }

        this.biomeCacheManager = builder.getBiomeCacheManager();
        this.biomeCacheManager.populateArrays(origin.getX(), origin.getY(), origin.getZ(), this.biomeCaches);
    }

    private void populateBlockArrays(int sectionIdx, SectionPos pos, Chunk chunk) {
        ChunkSection section = getChunkSection(chunk, pos);

        if (section == null || section.isEmpty()) {
            section = EMPTY_SECTION;
        }

        PalettedContainer<BlockState> container = section.getData();

        BitArray intArray = container.storage;
        IPalette<BlockState> palette = container.palette;

        BlockState[] dst = this.blockStatesArrays[sectionIdx];

        int minBlockX = Math.max(this.minX, pos.getWorldStartX());
        int maxBlockX = Math.min(this.maxX, pos.getWorldEndX());

        int minBlockY = Math.max(this.minY, pos.getWorldStartY());
        int maxBlockY = Math.min(this.maxY, pos.getWorldEndY());

        int minBlockZ = Math.max(this.minZ, pos.getWorldStartZ());
        int maxBlockZ = Math.min(this.maxZ, pos.getWorldEndZ());

        int prevPaletteId = -1;
        BlockState prevPaletteState = null;

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    int paletteId = intArray.getAt(blockIdx);

                    BlockState state;

                    if (prevPaletteId == paletteId) {
                        state = prevPaletteState;
                    } else {
                        state = palette.get(paletteId);

                        prevPaletteState = state;
                        prevPaletteId = paletteId;
                    }

                    dst[blockIdx] = state;
                }
            }
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.blockStates[this.getBlockIndex(pos.getX(), pos.getY(), pos.getZ())];
    }

    public BlockState getBlockState(int x, int y, int z) {
        return this.blockStates[this.getBlockIndex(x, y, z)];
    }

    public BlockState getOriginBlockState(int x, int y, int z) {
        return this.originBlockStates[getLocalBlockIndex(x, y, z)];
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    public FluidState getFluidState(int x, int y, int z) {
        return this.getBlockState(x, y, z).getFluidState();
    }

    @Override
    public float func_230487_a_(Direction direction, boolean shaded) {
        return this.world.func_230487_a_(direction, shaded);
    }

    @Override
    public WorldLightManager getLightManager() {
        return this.world.getLightManager();
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.getBlockEntity(pos, Chunk.CreateEntityType.IMMEDIATE);
    }

    public TileEntity getBlockEntity(BlockPos pos, Chunk.CreateEntityType type) {
        return this.chunks[this.getChunkIndexForBlock(pos)].getTileEntity(pos, type);
    }

    @Override
    public int getBlockColor(BlockPos pos, ColorResolver resolver) {
        BiomeColorCache cache;

        if (this.prevColorResolver == resolver) {
            cache = this.prevColorCache;
        } else {
            cache = this.colorResolvers.get(resolver);

            if (cache == null) {
                this.colorResolvers.put(resolver, cache = new BiomeColorCache(resolver, this));
            }

            this.prevColorResolver = resolver;
            this.prevColorCache = cache;
        }

        return cache.getBlendedColor(pos);
    }

    @Override
    public int getLightFor(LightType type, BlockPos pos) {
        switch (type) {
            case SKY:
                return this.getLightLevel(this.skyLightArrays, pos);
            case BLOCK:
                return this.getLightLevel(this.blockLightArrays, pos);
            default:
                return 0;
        }
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int ambientDarkness) {
        return 0;
    }


    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    private int getLightLevel(NibbleArray[] arrays, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        NibbleArray array = arrays[this.getSectionIndexForBlock(x, y, z)];

        if (array != null) {
            return array.get(x & 15, y & 15, z & 15);
        }

        return 0;
    }

    // TODO: Is this safe? The biome data arrays should be immutable once loaded into the client
    @Override
    public Biome getNoiseBiome(int x, int y, int z) {
        BiomeContainer array = this.biomeArrays[this.getBiomeIndexForBlock(x, z)];

        if (array != null ) {
            return array.getNoiseBiome(x, y, z);
        }

        return this.world.getNoiseBiomeRaw(x, y, z);
    }

    /**
     * Gets or computes the biome at the given global coordinates.
     */
    public Biome getCachedBiome(int x, int z) {
        return this.biomeCaches[this.getChunkIndexForBlock(x, z)].getBiome(this, x, z);
    }

    /**
     * Returns the index of a block in global coordinate space for this slice.
     */
    private int getBlockIndex(int x, int y, int z) {
        int x2 = x - this.blockOffsetX;
        int y2 = y - this.blockOffsetY;
        int z2 = z - this.blockOffsetZ;

        return (y2 * BLOCK_LENGTH * BLOCK_LENGTH) + (z2 * BLOCK_LENGTH) + x2;
    }

    /**
     * {@link WorldSlice#getChunkIndexForBlock(int, int)}
     */
    private int getChunkIndexForBlock(BlockPos pos) {
        return this.getChunkIndexForBlock(pos.getX(), pos.getZ());
    }

    /**
     * Returns the index of a chunk in global coordinate space for this slice.
     */
    private int getChunkIndexForBlock(int x, int z) {
        int x2 = (x >> 4) - this.chunkOffsetX;
        int z2 = (z >> 4) - this.chunkOffsetZ;

        return getLocalChunkIndex(x2, z2);
    }

    /**
     * Returns the index of a biome in the global coordinate space for this slice.
     */
    private int getBiomeIndexForBlock(int x, int z) {
        // Coordinates are in biome space!
        // [VanillaCopy] WorldView#getBiomeForNoiseGen(int, int, int)
        int x2 = (x >> 2) - this.chunkOffsetX;
        int z2 = (z >> 2) - this.chunkOffsetZ;

        return getLocalChunkIndex(x2, z2);
    }

    /**
     * Returns the index of a chunk section in global coordinate space for this slice.
     */
    private int getSectionIndexForBlock(int x, int y, int z) {
        int x2 = (x >> 4) - this.chunkOffsetX;
        int y2 = (y >> 4) - this.chunkOffsetY;
        int z2 = (z >> 4) - this.chunkOffsetZ;

        return getLocalSectionIndex2(x2, y2, z2);
    }

    /**
     * Returns the index of a chunk in local coordinate space to this slice.
     */
    public static int getLocalChunkIndex(int x, int z) {
        return (z * SECTION_LENGTH) + x;
    }

    /**
     * Returns the index of a chunk section in local coordinate space to this slice.
     */
    public static int getLocalSectionIndex2(int x, int y, int z) {
        return (y * SECTION_LENGTH * SECTION_LENGTH) + (z * SECTION_LENGTH) + x;
    }

    public SectionPos getOrigin() {
        return this.origin;
    }

    @Override
    public void reset() {
        for (BiomeCache cache : this.biomeCaches) {
            this.biomeCacheManager.release(cache);
        }

        Arrays.fill(this.biomeCaches, null);
        Arrays.fill(this.biomeArrays, null);
        Arrays.fill(this.blockLightArrays, null);
        Arrays.fill(this.skyLightArrays, null);

        this.biomeCacheManager = null;
        this.chunks = null;
        this.world = null;

        this.colorResolvers.clear();
        this.prevColorCache = null;
        this.prevColorResolver = null;
    }

    // [VanillaCopy] PalettedContainer#toIndex
    public static int getLocalBlockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    private static ChunkSection getChunkSection(Chunk chunk, SectionPos pos) {
        ChunkSection section = null;

        if (!World.isYOutOfBounds(pos.getY())) {
            section = chunk.getSections()[pos.getY()];
        }

        return section;
    }
}
