package me.jellysquid.mods.sodium.mixin.ai.nearby_entity_tracking;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISpawnWorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Extends the base world class to provide a {@link EntityTrackerEngine}.
 */
@Mixin(World.class)
public class WorldMixin implements EntityTrackerEngineProvider {
    private EntityTrackerEngine tracker;

    /**
     * Initialize the {@link EntityTrackerEngine} which all entities of the world will interact with.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(ISpawnWorldInfo worldInfo, RegistryKey<World> dimension, final DimensionType dimensionType, Supplier<IProfiler> profiler, boolean isRemote, boolean isDebug, long seed, CallbackInfo ci) {
        this.tracker = new EntityTrackerEngine();
    }

    @Override
    public EntityTrackerEngine getEntityTracker() {
        return this.tracker;
    }
}
