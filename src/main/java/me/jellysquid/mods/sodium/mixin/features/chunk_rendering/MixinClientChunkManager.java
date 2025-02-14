package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;

@Mixin(ClientChunkProvider.class)
public abstract class MixinClientChunkManager implements ChunkStatusListenerManager {
    @Shadow
    @Nullable
    public abstract Chunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);

    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private boolean needsTrackingUpdate = false;

    private ChunkStatusListener listener;

    @Inject(method = "replaceWithPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;onChunkLoaded(II)V", shift = At.Shift.AFTER))
    private void afterLoadChunkFromPacket(int chunkX, int chunkZ, BiomeContainer biomeContainerIn, PacketBuffer packetIn, CompoundNBT nbtTagIn, int sizeIn, boolean fullChunk, CallbackInfoReturnable<Chunk> cir) {
        if (this.listener != null && this.loadedChunks.add(ChunkPos.asLong(chunkX, chunkZ))) {
            this.listener.onChunkAdded(chunkX, chunkZ);
        }
    }

    @Inject(method = "drop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkProvider$ChunkArray;replace(ILnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/chunk/Chunk;)Lnet/minecraft/world/chunk/Chunk;", shift = At.Shift.AFTER))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        if (this.listener != null && this.loadedChunks.remove(ChunkPos.asLong(x, z))) {
            this.listener.onChunkRemoved(x, z);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (!this.needsTrackingUpdate) {
            return;
        }

        LongIterator it = this.loadedChunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);

            if (this.getChunk(x, z, ChunkStatus.FULL, false) == null) {
                it.remove();

                if (this.listener != null) {
                    this.listener.onChunkRemoved(x, z);
                }
            }
        }

        this.needsTrackingUpdate = false;
    }

    @Inject(method = "updateViewCenter(II)V", at = @At("RETURN"))
    private void afterChunkMapCenterChanged(int x, int z, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Inject(method = "updateViewRadius",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientChunkProvider$ChunkArray;replace(ILnet/minecraft/world/chunk/Chunk;)V",
                    shift = At.Shift.AFTER))
    private void afterLoadDistanceChanged(int loadDistance, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Override
    public void setListener(ChunkStatusListener listener) {
        this.listener = listener;
    }

    @Mixin(targets = "net/minecraft/client/multiplayer/ClientChunkProvider$ChunkArray")
    public static class MixinClientChunkMap {
        @Mutable
        @Shadow
        @Final
        private AtomicReferenceArray<Chunk> chunks;

        @Mutable
        @Shadow
        @Final
        private int chunkRadius;

        @Mutable
        @Shadow
        @Final
        private int viewRange;

        private int factor;

        @Dynamic("The constructor needs the outer class reference in the first parameter which is not in the source code.")
        @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientChunkProvider;I)V", at = @At("RETURN"))
        private void reinit(ClientChunkProvider outer, int viewDistanceIn, CallbackInfo ci) {
            // This re-initialization is a bit expensive on memory, but it only happens when either the world is
            // switched or the render distance is changed;
            this.chunkRadius = viewDistanceIn;

            // Make the diameter a power-of-two so we can exploit bit-wise math when computing indices
            this.viewRange = MathHelper.smallestEncompassingPowerOfTwo(viewDistanceIn * 2 + 1);

            // The factor is used as a bit mask to replace the modulo in getIndex
            this.factor = this.viewRange - 1;

            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        /**
         * @reason Avoid expensive modulo
         * @author JellySquid
         */
        @Overwrite
        private int getIndex(int chunkX, int chunkZ) {
            return (chunkZ & this.factor) * this.viewRange + (chunkX & this.factor);
        }
    }
}
