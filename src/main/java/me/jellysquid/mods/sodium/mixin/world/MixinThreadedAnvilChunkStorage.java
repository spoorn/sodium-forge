package me.jellysquid.mods.sodium.mixin.world;

import com.mojang.datafixers.util.Either;
import me.jellysquid.mods.phosphor.common.world.ThreadedAnvilChunkStorageAccess;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

@Mixin(ChunkManager.class)
public abstract class MixinThreadedAnvilChunkStorage implements ThreadedAnvilChunkStorageAccess {
    @Shadow
    protected abstract CompletableFuture<Either<List<Chunk>, ChunkHolder.IChunkLoadingError>> getChunkRangeFuture(final ChunkPos centerChunk, final int margin, final IntFunction<ChunkStatus> distanceToStatus);

    @Override
    @Invoker("releaseLightTicket")
    public abstract void invokeReleaseLightTicket(ChunkPos pos);

    @Shadow
    @Final
    private ThreadTaskExecutor<Runnable> mainThreadExecutor;

    @Redirect(
        method = "unpackTicks(Lnet/minecraft/world/server/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/server/ChunkHolder;getOrScheduleFuture(Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/world/server/ChunkManager;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<Either<Chunk, ChunkHolder.IChunkLoadingError>> enforceNeighborsLoaded(final ChunkHolder holder, final ChunkStatus targetStatus, final ChunkManager chunkStorage) {
        return holder.getOrScheduleFuture(ChunkStatus.FULL, (ChunkManager) (Object) this).thenComposeAsync(
            either -> either.map(
                chunk -> this.getChunkRangeFuture(holder.getPos(), 1, ChunkStatus::getStatus).thenApply(
                    either_ -> either_.mapLeft(list -> list.get(list.size() / 2))
                ),
                unloaded -> CompletableFuture.completedFuture(Either.right(unloaded))
            ),
            this.mainThreadExecutor
        );
    }
}
