package me.jellysquid.mods.lithium.common.world.interests;

import net.minecraft.village.PointOfInterest;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.village.PointOfInterestType;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface PointOfInterestSetExtended {
    void collectMatchingPoints(Predicate<PointOfInterestType> type, PointOfInterestManager.Status status,
                               Consumer<PointOfInterest> consumer);
}