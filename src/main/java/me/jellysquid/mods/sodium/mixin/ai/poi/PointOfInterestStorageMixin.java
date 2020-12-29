package me.jellysquid.mods.sodium.mixin.ai.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import me.jellysquid.mods.lithium.common.world.interests.PointOfInterestDataExtended;
import me.jellysquid.mods.lithium.common.world.interests.PointOfInterestSetExtended;
import me.jellysquid.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import me.jellysquid.mods.lithium.common.world.interests.iterator.NearbyPointOfInterestStream;
import me.jellysquid.mods.lithium.common.world.interests.iterator.SinglePointOfInterestTypeFilter;
import me.jellysquid.mods.lithium.common.world.interests.types.PointOfInterestTypeHelper;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.village.PointOfInterest;
import net.minecraft.village.PointOfInterestData;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.storage.RegionSectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Mixin(PointOfInterestManager.class)
public abstract class PointOfInterestStorageMixin extends RegionSectionCache<PointOfInterestData>
        implements PointOfInterestDataExtended {
    public PointOfInterestStorageMixin(File directory, Function<Runnable, Codec<PointOfInterestData>> function,
                                       Function<Runnable, PointOfInterestData> function2, DataFixer dataFixer,
                                       DefaultTypeReferences dataFixTypes, boolean bl) {
        super(directory, function, function2, dataFixer, dataFixTypes, bl);
    }

    /**
     * @reason Avoid Stream API
     * @author Jellysquid
     */
    @Overwrite
    public void checkConsistencyWithBlocks(ChunkPos chunkPos_1, ChunkSection section) {
        SectionPos sectionPos = SectionPos.from(chunkPos_1, section.getYLocation() >> 4);

        PointOfInterestData set = this.func_219113_d(sectionPos.asLong()).orElse(null);

        if (set != null) {
            set.refresh((consumer) -> {
                if (PointOfInterestTypeHelper.shouldScan(section)) {
                    this.updateFromSelection(section, sectionPos, consumer);
                }
            });
        } else {
            if (PointOfInterestTypeHelper.shouldScan(section)) {
                set = this.func_235995_e_(sectionPos.asLong());

                this.updateFromSelection(section, sectionPos, set::add);
            }
        }
    }

    /**
     * @reason Retrieve all points of interest in one operation
     * @author JellySquid
     */
    @SuppressWarnings("unchecked")
    @Overwrite
    public Stream<PointOfInterest> getInChunk(Predicate<PointOfInterestType> predicate, ChunkPos pos,
                                              PointOfInterestManager.Status status) {
        return ((RegionBasedStorageSectionExtended<PointOfInterestData>) this)
                .getWithinChunkColumn(pos.x, pos.z)
                .flatMap((set) -> set.getRecords(predicate, status));
    }

    /**
     * @reason Retrieve all points of interest in one operation
     * @author JellySquid
     */
    @Overwrite
    public Optional<BlockPos> getRandom(Predicate<PointOfInterestType> typePredicate, Predicate<BlockPos> posPredicate,
                                          PointOfInterestManager.Status status, BlockPos pos, int radius,
                                          Random rand) {
        List<PointOfInterest> list = this.collectWithinRadius(typePredicate, pos, radius, status);

        Collections.shuffle(list, rand);

        for (PointOfInterest point : list) {
            if (posPredicate.test(point.getPos())) {
                return Optional.of(point.getPos());
            }
        }

        return Optional.empty();
    }

    /**
     * @reason Avoid stream-heavy code, use a faster iterator and callback-based approach
     * @author JellySquid
     */
    @Overwrite
    public Optional<BlockPos> func_234148_d_(Predicate<PointOfInterestType> predicate, BlockPos pos, int radius,
                                                 PointOfInterestManager.Status status) {
        List<PointOfInterest> points = this.collectWithinRadius(predicate, pos, radius, status);

        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (PointOfInterest point : points) {
            double distance = point.getPos().distanceSq(pos);

            if (distance < nearestDistance) {
                nearest = point.getPos();
                nearestDistance = distance;
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * @reason Avoid stream-heavy code, use a faster iterator and callback-based approach
     * @author JellySquid
     */
    @Overwrite
    public long getCountInRange(Predicate<PointOfInterestType> predicate, BlockPos pos, int radius,
                      PointOfInterestManager.Status status) {
        return this.collectWithinRadius(predicate, pos, radius, status).size();
    }

    /**
     * @author JellySquid
     * @reason Avoid stream-heavy code, use faster filtering and fetches
     */
    @Overwrite
    public Stream<PointOfInterest> func_219146_b(Predicate<PointOfInterestType> predicate, BlockPos origin, int radius,
                                               PointOfInterestManager.Status status) {
        double radiusSq = radius * radius;

        return this.iterateSpiral(origin, radius, status, predicate, poi -> {
            return isWithinCircleRadius(poi.getPos(), radiusSq, origin);
        });
    }

    @Override
    public Optional<BlockPos> findNearestInSquare(BlockPos origin, int radius, PointOfInterestType type,
                                                  PointOfInterestManager.Status status,
                                                  Predicate<PointOfInterest> predicate) {
        return this.iterateSpiral(origin, radius, status, new SinglePointOfInterestTypeFilter(type), poi -> {
            return isWithinSquareRadius(origin, radius, poi.getPos()) && predicate.test(poi);
        }).map(PointOfInterest::getPos).findFirst();
    }

    private List<PointOfInterest> collectWithinRadius(Predicate<PointOfInterestType> predicate, BlockPos origin,
                                                      int radius, PointOfInterestManager.Status status) {

        double radiusSq = radius * radius;

        int minChunkX = (origin.getX() - radius - 1) >> 4;
        int minChunkZ = (origin.getZ() - radius - 1) >> 4;

        int maxChunkX = (origin.getX() + radius + 1) >> 4;
        int maxChunkZ = (origin.getZ() + radius + 1) >> 4;

        // noinspection unchecked
        RegionBasedStorageSectionExtended<PointOfInterestData> storage = ((RegionBasedStorageSectionExtended<PointOfInterestData>) this);

        List<PointOfInterest> points = new ArrayList<>();
        Consumer<PointOfInterest> collector = (point) -> {
            if (isWithinCircleRadius(origin, radiusSq, point.getPos())) {
                points.add(point);
            }
        };

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                for (PointOfInterestData set : storage.getInChunkColumn(x, z)) {
                    ((PointOfInterestSetExtended) set).collectMatchingPoints(predicate, status, collector);
                }
            }
        }

        return points;
    }

    private Stream<PointOfInterest> iterateSpiral(BlockPos origin, int radius,
                                                  PointOfInterestManager.Status status,
                                                  Predicate<PointOfInterestType> typePredicate,
                                                  Predicate<PointOfInterest> pointPredicate) {
        // noinspection unchecked
        RegionBasedStorageSectionExtended<PointOfInterestData> storage = ((RegionBasedStorageSectionExtended<PointOfInterestData>) this);

        return StreamSupport.stream(new NearbyPointOfInterestStream(typePredicate, status, pointPredicate, origin, radius, storage), false);
    }

    private static boolean isWithinSquareRadius(BlockPos origin, int radius, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= radius &&
                Math.abs(pos.getZ() - origin.getZ()) <= radius;
    }

    private static boolean isWithinCircleRadius(BlockPos origin, double radiusSq, BlockPos pos) {
        return origin.distanceSq(pos) <= radiusSq;
    }

    @Shadow
    protected abstract void updateFromSelection(ChunkSection section, SectionPos sectionPos, BiConsumer<BlockPos, PointOfInterestType> entryConsumer);
}
