package me.jellysquid.mods.sodium.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.InitialLightingAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.WorldLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldLightManager.class)
public abstract class MixinLightingProvider implements InitialLightingAccess
{
    @Shadow
    @Final
    private LightEngine<?, ?> blockEngine;

    @Shadow
    @Final
    private LightEngine<?, ?> skyEngine;

    @Shadow
    public void updateSectionStatus(SectionPos pos, boolean notReady) {
    }

    @Shadow
    public void enableLightSources(ChunkPos pos, boolean lightEnabled) {
    }

    @Shadow
    public void retainData(ChunkPos pos, boolean retainData) {
    }

    @Shadow
    public void onBlockEmissionIncrease(BlockPos pos, int level) {
    }

    @Override
    public void enableSourceLight(final long chunkPos) {
        if (this.blockEngine != null) {
            ((InitialLightingAccess) this.blockEngine).enableSourceLight(chunkPos);
        }

        if (this.skyEngine != null) {
            ((InitialLightingAccess) this.skyEngine).enableSourceLight(chunkPos);
        }
    }

    @Override
    public void enableLightUpdates(final long chunkPos) {
        if (this.blockEngine != null) {
            ((InitialLightingAccess) this.blockEngine).enableLightUpdates(chunkPos);
        }

        if (this.skyEngine != null) {
            ((InitialLightingAccess) this.skyEngine).enableLightUpdates(chunkPos);
        }
    }
}

