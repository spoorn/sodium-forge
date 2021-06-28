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
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;
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

    // The number of outward blocks from the origin chunk to slice
    public static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The number of blocks on each axis of this slice.
    private static final int BLOCK_LENGTH = SECTION_BLOCK_LENGTH + (NEIGHBOR_BLOCK_RADIUS * 2);

    // The number of blocks contained by a world slice
    public static final int BLOCK_COUNT = BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH;

    // The number of outward chunks from the origin chunk to slice
    public static final int NEIGHBOR_CHUNK_RADIUS = MathHelper.roundUp(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of sections on each axis of this slice.
    private static final int SECTION_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The size of the lookup tables used for mapping values to coordinate int pairs. The lookup table size is always
    // a power of two so that multiplications can be replaced with simple bit shifts in hot code paths.
    private static final int TABLE_LENGTH = MathHelper.smallestEncompassingPowerOfTwo(SECTION_LENGTH);

    // The number of bits needed for each X/Y/Z component in a lookup table.
    private static final int TABLE_BITS = Integer.bitCount(TABLE_LENGTH - 1);

    // The array size for the chunk lookup table.
    private static final int CHUNK_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH;

    // The array size for the section lookup table.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // The data arrays for this slice
    // These are allocated once and then re-used when the slice is released back to an object pool
    private final BlockState[] blockStates;

    // Local Section->Light table. Read-only.
    private final NibbleArray[] blockLightArrays;
    private final NibbleArray[] skyLightArrays;

    // Local Section->Biome table.
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

        Chunk[] chunks = new Chunk[CHUNK_TABLE_ARRAY_SIZE];

        // Create an array of references to the world chunks in this slice
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks[getLocalChunkIndex(x - minChunkX, z - minChunkZ)] = world.getChunk(x, z);
            }
        }
        return chunks;
    }

    public WorldSlice() {
        this.blockLightArrays = new NibbleArray[SECTION_TABLE_ARRAY_SIZE];
        this.skyLightArrays = new NibbleArray[SECTION_TABLE_ARRAY_SIZE];
        this.biomeCaches = new BiomeCache[CHUNK_TABLE_ARRAY_SIZE];
        this.biomeArrays = new BiomeContainer[CHUNK_TABLE_ARRAY_SIZE];
        this.blockStates = new BlockState[BLOCK_COUNT];
    }

    public void init(ChunkBuilder<?> builder, World world, SectionPos origin, Chunk[] chunks) {
        this.world = world;
        this.chunks = chunks;
        this.origin = origin;

        this.minX = origin.getWorldStartX() - NEIGHBOR_BLOCK_RADIUS;
        this.minY = origin.getWorldStartY() - NEIGHBOR_BLOCK_RADIUS;
        this.minZ = origin.getWorldStartZ() - NEIGHBOR_BLOCK_RADIUS;

        this.maxX = origin.getWorldEndX() + NEIGHBOR_BLOCK_RADIUS;
        this.maxY = origin.getWorldEndY() + NEIGHBOR_BLOCK_RADIUS;
        this.maxZ = origin.getWorldEndZ() + NEIGHBOR_BLOCK_RADIUS;

        final int minChunkX = this.minX >> 4;
        final int minChunkY = this.minY >> 4;
        final int minChunkZ = this.minZ >> 4;

        final int maxChunkX = this.maxX >> 4;
        final int maxChunkY = this.maxY >> 4;
        final int maxChunkZ = this.maxZ >> 4;

        this.baseX = minChunkX << 4;
        this.baseY = minChunkY << 4;
        this.baseZ = minChunkZ << 4;

        // Iterate over all sliced chunks
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {

                // The local index for this chunk in the slice's data arrays
                int chunkIdx = getLocalChunkIndex(chunkX - minChunkX, chunkZ - minChunkZ);

                Chunk chunk = chunks[chunkIdx];

                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    SectionPos pos = SectionPos.of(chunkX, chunkY, chunkZ);

                    // Find the local position of the chunk in the sliced chunk array
                    int sectionIdx = getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ);

                    this.populateLightArrays(sectionIdx, pos);
                    this.populateBlockArrays(pos, chunk);
                }

                this.biomeArrays[chunkIdx] = chunk.getBiomes();
            }
        }

        this.biomeCacheManager = builder.getBiomeCacheManager();
        this.biomeCacheManager.populateArrays(origin.getX(), origin.getY(), origin.getZ(), this.biomeCaches);
    }

    /**
     * Returns the index of a block in global coordinate space for this slice.
     */
    private int getBlockIndex(int x, int y, int z) {
        int x2 = x - this.minX;
        int y2 = y - this.minY;
        int z2 = z - this.minZ;

        return (y2 * BLOCK_LENGTH * BLOCK_LENGTH) + (z2 * BLOCK_LENGTH) + x2;
    }

    private void populateLightArrays(int sectionIdx, SectionPos pos) {
        if (World.isYOutOfBounds(pos.getY())) {
            return;
        }

        IWorldLightListener blockLightProvider = this.world.getLightManager().getLightEngine(LightType.BLOCK);
        IWorldLightListener skyLightProvider = this.world.getLightManager().getLightEngine(LightType.SKY);

        this.blockLightArrays[sectionIdx] = blockLightProvider.getData(pos);
        this.skyLightArrays[sectionIdx] = skyLightProvider.getData(pos);
    }

    private void populateBlockArrays(SectionPos pos, Chunk chunk) {
        ChunkSection section = getChunkSection(chunk, pos);

        if (section == null || section.isEmpty()) {
            section = EMPTY_SECTION;
        }

        int minBlockX = Math.max(this.minX, pos.getWorldStartX());
        int maxBlockX = Math.min(this.maxX, (pos.getSectionX() + 1) << 4);

        int minBlockY = Math.max(this.minY, pos.getWorldStartY());
        int maxBlockY = Math.min(this.maxY, (pos.getSectionY() + 1) << 4);

        int minBlockZ = Math.max(this.minZ, pos.getWorldStartZ());
        int maxBlockZ = Math.min(this.maxZ, (pos.getSectionZ() + 1) << 4);

        // NOTE: This differs from the parent repo as when I was trying to pull
        // https://github.com/CaffeineMC/sodium-fabric/commit/eb664ec9bf22428678691f761fa0a6a73c916410
        // I ran into some NPE, likely due to a compatibility issue with another mod.  Thus, this will just use the
        // official getBlockState() instead of doing fancy optimization by fetching actual object references from the
        // ChunkSection.

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    this.blockStates[this.getBlockIndex(x, y, z)] = section.getBlockState(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState getBlockState(int x, int y, int z) {
        /*  See comment above for why this is commented out

        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return this.blockStatesArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                [getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];*/
        return this.blockStates[this.getBlockIndex(x, y, z)];
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
        int relX = pos.getX() - this.baseX;
        int relZ = pos.getZ() - this.baseZ;

        return this.chunks[getLocalChunkIndex(relX >> 4, relZ >> 4)]
                .getTileEntity(pos, type);
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
        int relX = pos.getX() - this.baseX;
        int relY = pos.getY() - this.baseY;
        int relZ = pos.getZ() - this.baseZ;

        NibbleArray array = arrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)];

        if (array != null) {
            return array.get(relX & 15, relY & 15, relZ & 15);
        }

        return 0;
    }

    // TODO: Is this safe? The biome data arrays should be immutable once loaded into the client
    @Override
    public Biome getNoiseBiome(int x, int y, int z) {
        int x2 = (x >> 2) - (this.baseX >> 4);
        int z2 = (z >> 2) - (this.baseZ >> 4);

        // Coordinates are in biome space!
        // [VanillaCopy] WorldView#getBiomeForNoiseGen(int, int, int)
        BiomeContainer array = this.biomeArrays[getLocalChunkIndex(x2, z2)];

        if (array != null ) {
            return array.getNoiseBiome(x, y, z);
        }

        return this.world.getNoiseBiomeRaw(x, y, z);
    }

    /**
     * Gets or computes the biome at the given global coordinates.
     */
    public Biome getCachedBiome(int x, int z) {
        int relX = x - this.baseX;
        int relZ = z - this.baseZ;

        return this.biomeCaches[getLocalChunkIndex(relX >> 4, relZ >> 4)]
                .getBiome(this, x, z);
    }

    public SectionPos getOrigin() {
        return this.origin;
    }

    @Override
    public void reset() {
        for (BiomeCache cache : this.biomeCaches) {
            if (cache != null) {
                this.biomeCacheManager.release(cache);
            }
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

    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    public static int getLocalChunkIndex(int x, int z) {
        return z << TABLE_BITS | x;
    }

    private static ChunkSection getChunkSection(Chunk chunk, SectionPos pos) {
        ChunkSection section = null;

        if (!World.isYOutOfBounds(pos.getY())) {
            section = chunk.getSections()[pos.getY()];
        }

        return section;
    }
}
