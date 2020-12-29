package me.jellysquid.mods.lithium.common.util.tuples;

import net.minecraft.util.math.BlockPos;
import net.minecraft.village.PointOfInterest;

public class SortedPointOfInterest {
    public final PointOfInterest poi;
    public final double distance;

    public SortedPointOfInterest(PointOfInterest poi, BlockPos origin) {
        this.poi = poi;
        this.distance = poi.getPos().distanceSq(origin);
    }

    public BlockPos getPos() {
        return this.poi.getPos();
    }

    public int getY() {
        return this.getPos().getY();
    }
}
