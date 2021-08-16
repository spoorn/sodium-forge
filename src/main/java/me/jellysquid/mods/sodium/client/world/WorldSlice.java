package me.jellysquid.mods.sodium.client.world;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.world.biome.BiomeCache;
import me.jellysquid.mods.sodium.client.world.biome.BiomeColorCache;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import me.jellysquid.mods.sodium.client.world.cloned.PackedIntegerArrayExtended;
import me.jellysquid.mods.sodium.client.world.cloned.palette.ClonedPalette;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BitArray;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.lighting.WorldLightManager;

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
public class WorldSlice implements IBlockDisplayReader, BiomeManager.IBiomeReader {
    // The number of blocks on each axis in a section.
    private static final int SECTION_BLOCK_LENGTH = 16;

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

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

    // The array size for the section lookup table.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // The world this slice has copied data from
    private final World world;

    // The data arrays for this slice
    // These are allocated once and then re-used when the slice is released back to an object pool
    private final BlockState[] blockStates;

    // Local Section->BlockState table.
    //private final BlockState[][] blockStatesArrays;

    // Local section copies. Read-only.
    private ClonedChunkSection[] sections;

    // Biome caches for each chunk section
    private BiomeCache[] biomeCaches;

    // The biome blend caches for each color resolver type
    // This map is always re-initialized, but the caches themselves are taken from an object pool
    private final Map<ColorResolver, BiomeColorCache> biomeColorCaches = new Reference2ObjectOpenHashMap<>();

    // The previously accessed and cached color resolver, used in conjunction with the cached color cache field
    private ColorResolver prevColorResolver;

    // The cached lookup result for the previously accessed color resolver to avoid excess hash table accesses
    // for vertex color blending
    private BiomeColorCache prevColorCache;

    // The starting point from which this slice captures blocks
    private int baseX, baseY, baseZ;

    // The chunk origin of this slice
    private SectionPos origin;

    private ChunkRenderContext context;

    public static ChunkRenderContext prepare(World world, SectionPos origin, ClonedChunkSectionCache sectionCache) {
        Chunk chunk = world.getChunk(origin.getX(), origin.getZ());
        ChunkSection section = chunk.getSections()[origin.getY()];

        // If the chunk section is absent or empty, simply terminate now. There will never be anything in this chunk
        // section to render, so we need to signal that a chunk render task shouldn't created. This saves a considerable
        // amount of time in queueing instant build tasks and greatly accelerates how quickly the world can be loaded.
        if (ChunkSection.isEmpty(section)) {
            return null;
        }

        MutableBoundingBox volume = new MutableBoundingBox(origin.minBlockX() - NEIGHBOR_BLOCK_RADIUS,
                origin.minBlockY() - NEIGHBOR_BLOCK_RADIUS,
                origin.minBlockZ() - NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockX() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockY() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockZ() + NEIGHBOR_BLOCK_RADIUS);

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = origin.getX() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = origin.getY() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = origin.getZ() - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = origin.getX() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = origin.getY() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = origin.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                            sectionCache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        return new ChunkRenderContext(origin, sections, volume);
    }

    public WorldSlice(World world) {
        this.world = world;

        this.sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];
        //this.blockStatesArrays = new BlockState[SECTION_TABLE_ARRAY_SIZE][];
        this.biomeCaches = new BiomeCache[SECTION_TABLE_ARRAY_SIZE];
        this.blockStates = new BlockState[BLOCK_COUNT];

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    int i = getLocalSectionIndex(x, y, z);

                    //this.blockStatesArrays[i] = new BlockState[SECTION_BLOCK_COUNT];
                    this.biomeCaches[i] = new BiomeCache(this.world);
                }
            }
        }
    }

    public void copyData(ChunkRenderContext context) {
        this.origin = context.getOrigin();
        this.sections = context.getSections();
        this.context = context;

        this.prevColorCache = null;
        this.prevColorResolver = null;

        this.biomeColorCaches.clear();

        this.baseX = (this.origin.getX() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseY = (this.origin.getY() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseZ = (this.origin.getZ() - NEIGHBOR_CHUNK_RADIUS) << 4;

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    int idx = getLocalSectionIndex(x, y, z);

                    this.biomeCaches[idx].reset();

                    this.unpackBlockData(null/*this.blockStatesArrays[idx]*/, this.sections[idx], context.getVolume());
                }
            }
        }
    }

    private void unpackBlockData(BlockState[] states, ClonedChunkSection section, MutableBoundingBox box) {
       /* if (this.origin.equals(section.getPosition()))  {
            this.unpackBlockDataZ(states, section);
        } else {*/
            this.unpackBlockDataR(states, section, box);
        //}
    }

    private void unpackBlockDataR(BlockState[] states, ClonedChunkSection section, MutableBoundingBox box) {
        BitArray intArray = section.getBlockData();
        ClonedPalette<BlockState> palette = section.getBlockPalette();

        SectionPos pos = section.getPosition();

        int minBlockX = Math.max(box.x0, pos.minBlockX());
        int maxBlockX = Math.min(box.x1, pos.maxBlockX());

        int minBlockY = Math.max(box.y0, pos.minBlockY());
        int maxBlockY = Math.min(box.y1, pos.maxBlockY());

        int minBlockZ = Math.max(box.z0, pos.minBlockZ());
        int maxBlockZ = Math.min(box.z1, pos.maxBlockZ());

        /*for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    int value = intArray.getAt(blockIdx);

                    states[blockIdx] = palette.get(value);
                }
            }
        }*/

        // NOTE: This differs from the parent repo as when I was trying to pull
        // https://github.com/CaffeineMC/sodium-fabric/commit/eb664ec9bf22428678691f761fa0a6a73c916410
        // I ran into some NPE, likely due to a compatibility issue with another mod.

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    this.blockStates[this.getBlockIndex(box, x, y, z)] = section.getBlockState(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    /**
     * Returns the index of a block in global coordinate space for this slice.
     */
    private int getBlockIndex(MutableBoundingBox box, int x, int y, int z) {
        int x2 = x - box.x0;
        int y2 = y - box.y0;
        int z2 = z - box.z0;
        return (y2 * BLOCK_LENGTH * BLOCK_LENGTH) + (z2 * BLOCK_LENGTH) + x2;
    }

    private void unpackBlockDataZ(BlockState[] states, ClonedChunkSection section) {
        ((PackedIntegerArrayExtended) section.getBlockData())
                .copyUsingPalette(states, section.getBlockPalette());
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ(), pos);
    }

    public BlockState getBlockState(int x, int y, int z, BlockPos pos) {
        if (World.isOutsideBuildHeight(y)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }

        // Some mods such as Inspirations that modifies block colors traverse around the current BlockPos to look for
        // neighboring blocks.  While traversing, they may move out of this current slice's range causing an out of bounds
        // error.  When this happens, we can default to the World's getBlockState
        // See https://github.com/spoorn/sodium-forge/issues?q=is%3Aissue+arrayindexoutofboundsexception+
        int index = this.getBlockIndex(this.context.getVolume(), x, y, z);
        if (index < 0 || index >= this.blockStates.length) {
            return this.world.getBlockState(pos == null ? new BlockPos(x, y, z) : pos);
        }

        return this.blockStates[index];
       /* int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return this.blockStatesArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                [getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];*/
    }

    /*public BlockState getBlockStateRelative(int x, int y, int z) {
        return this.blockStatesArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)]
                [getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }*/

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return this.world.getShade(direction, shaded);
    }

    @Override
    public WorldLightManager getLightEngine() {
        return this.world.getLightEngine();
    }

    @Override
    public TileEntity getBlockEntity(BlockPos pos) {
        int relX = pos.getX() - this.baseX;
        int relY = pos.getY() - this.baseY;
        int relZ = pos.getZ() - this.baseZ;

        // Some mods such as Dimensional Portals that modifies block colors traverse around the current BlockPos to look for
        // neighboring blocks.  While traversing, they may move out of this current slice's range causing an out of bounds
        // error.  When this happens, we can default to the World's getBlockState
        int index = getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4);
        if (index < 0 || index >= this.sections.length) {
            return this.world.getBlockEntity(pos);
        }

        return this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                .getBlockEntity(relX & 15, relY & 15, relZ & 15);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        BiomeColorCache cache;

        if (this.prevColorResolver == resolver) {
            cache = this.prevColorCache;
        } else {
            cache = this.biomeColorCaches.get(resolver);

            if (cache == null) {
                this.biomeColorCaches.put(resolver, cache = new BiomeColorCache(resolver, this));
            }

            this.prevColorResolver = resolver;
            this.prevColorCache = cache;
        }

        return cache.getBlendedColor(pos);
    }

    @Override
    public int getBrightness(LightType type, BlockPos pos) {
        int relX = pos.getX() - this.baseX;
        int relY = pos.getY() - this.baseY;
        int relZ = pos.getZ() - this.baseZ;

        return this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                .getLightLevel(type, relX & 15, relY & 15, relZ & 15);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return 0;
    }


    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    @Override
    public Biome getNoiseBiome(int x, int y, int z) {
        int x2 = (x >> 2) - (this.baseX >> 4);
        int z2 = (z >> 2) - (this.baseZ >> 4);

        // Coordinates are in biome space!
        // [VanillaCopy] WorldView#getBiomeForNoiseGen(int, int, int)
        ClonedChunkSection section = this.sections[getLocalChunkIndex(x2, z2)];

        if (section != null ) {
            return section.getBiomeForNoiseGen(x, y, z);
        }

        return this.world.getUncachedNoiseBiome(x, y, z);
    }

    /**
     * Gets or computes the biome at the given global coordinates.
     */
    public Biome getBiome(int x, int y, int z) {
        int relX = x - this.baseX;
        int relY = y - this.baseY;
        int relZ = z - this.baseZ;

        return this.biomeCaches[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                .getBiome(this, x, relY >> 4, z);
    }

    public SectionPos getOrigin() {
        return this.origin;
    }

    // [VanillaCopy] PalettedContainer#toIndex
    public static int getLocalBlockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    public static int getLocalChunkIndex(int x, int z) {
        return z << TABLE_BITS | x;
    }
}
