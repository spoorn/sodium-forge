package me.jellysquid.mods.sodium.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.ServerLightingProviderAccess;
import me.jellysquid.mods.phosphor.common.world.ThreadedAnvilChunkStorageAccess;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorldLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;

@Mixin(ServerWorldLightManager.class)
public abstract class MixinServerLightingProvider extends MixinLightingProvider implements ServerLightingProviderAccess {
    @Shadow
    protected abstract void addTask(int x, int z, IntSupplier completedLevelSupplier, ServerWorldLightManager.Phase stage, Runnable task);

    @Shadow
    protected abstract void addTask(int x, int z, ServerWorldLightManager.Phase stage, Runnable task);

    @Override
    public CompletableFuture<IChunk> setupLightmaps(final IChunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();

        // This evaluates the non-empty subchunks concurrently on the lighting thread...
        this.addTask(chunkPos.x, chunkPos.z, () -> 0, ServerWorldLightManager.Phase.PRE_UPDATE, Util.name(() -> {
            final ChunkSection[] chunkSections = chunk.getSections();

            for (int i = 0; i < chunkSections.length; ++i) {
                if (!ChunkSection.isEmpty(chunkSections[i])) {
                    super.updateSectionStatus(SectionPos.of(chunkPos, i), false);
                }
            }

            if (chunk.isLightCorrect()) {
                super.enableSourceLight(SectionPos.getZeroNode(SectionPos.asLong(chunkPos.x, 0, chunkPos.z)));
            }

            super.enableLightUpdates(SectionPos.getZeroNode(SectionPos.asLong(chunkPos.x, 0, chunkPos.z)));
        },
            () -> "setupLightmaps " + chunkPos
        ));

        return CompletableFuture.supplyAsync(() -> {
            super.retainData(chunkPos, false);
            return chunk;
        },
            (runnable) -> this.addTask(chunkPos.x, chunkPos.z, () -> 0, ServerWorldLightManager.Phase.POST_UPDATE, runnable)
        );
    }

    @Shadow
    @Final
    private ChunkManager chunkMap;

    /**
     * @author PhiPro
     * @reason Move parts of the logic to {@link #setupLightmaps(Chunk)}
     */
    @Overwrite
    public CompletableFuture<IChunk> lightChunk(IChunk chunk, boolean excludeBlocks) {
        final ChunkPos chunkPos = chunk.getPos();

        this.addTask(chunkPos.x, chunkPos.z, ServerWorldLightManager.Phase.PRE_UPDATE, Util.name(() -> {
            if (!chunk.isLightCorrect()) {
                super.enableSourceLight(SectionPos.getZeroNode(SectionPos.asLong(chunkPos.x, 0, chunkPos.z)));
            }

            if (!excludeBlocks) {
                chunk.getLights().forEach((blockPos) -> {
                    super.onBlockEmissionIncrease(blockPos, chunk.getLightEmission(blockPos));
                });
            }
        },
            () -> "lightChunk " + chunkPos + " " + excludeBlocks
        ));

        return CompletableFuture.supplyAsync(() -> {
            chunk.setLightCorrect(true);
            ((ThreadedAnvilChunkStorageAccess) this.chunkMap).invokeReleaseLightTicket(chunkPos);

            return chunk;
        },
            (runnable) -> this.addTask(chunkPos.x, chunkPos.z, ServerWorldLightManager.Phase.POST_UPDATE, runnable)
        );
    }
}
