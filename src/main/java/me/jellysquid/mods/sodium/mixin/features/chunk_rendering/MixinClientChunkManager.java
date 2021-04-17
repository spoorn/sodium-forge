package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ClientChunkProvider.class)
public class MixinClientChunkManager implements ChunkStatusListenerManager {
    private ChunkStatusListener listener;

    @Inject(method = "loadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;onChunkLoaded(II)V", shift = At.Shift.AFTER))
    private void afterLoadChunkFromPacket(int chunkX, int chunkZ, BiomeContainer biomeContainerIn, PacketBuffer packetIn, CompoundNBT nbtTagIn, int sizeIn, boolean fullChunk, CallbackInfoReturnable<Chunk> cir) {
        if (this.listener != null) {
            this.listener.onChunkAdded(chunkX, chunkZ);
        }
    }

    @Inject(method = "unloadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkProvider$ChunkArray;unload(ILnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/chunk/Chunk;)Lnet/minecraft/world/chunk/Chunk;", shift = At.Shift.AFTER))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        if (this.listener != null) {
            this.listener.onChunkRemoved(x, z);
        }
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
        private int viewDistance;

        @Mutable
        @Shadow
        @Final
        private int sideLength;

        private int factor;

        @Inject(method = "<init>", at = @At("RETURN"))
        private void reinit(ClientChunkProvider outer, int loadDistance, CallbackInfo ci) {
            // This re-initialization is a bit expensive on memory, but it only happens when either the world is
            // switched or the render distance is changed;
            this.sideLength = loadDistance;

            // Make the diameter a power-of-two so we can exploit bit-wise math when computing indices
            this.viewDistance = MathHelper.smallestEncompassingPowerOfTwo(loadDistance * 2 + 1);

            // The factor is used as a bit mask to replace the modulo in getIndex
            this.factor = this.viewDistance - 1;

            this.chunks = new AtomicReferenceArray<>(this.viewDistance * this.viewDistance);
        }

        /**
         * @reason Avoid expensive modulo
         * @author JellySquid
         */
        @Overwrite
        private int getIndex(int chunkX, int chunkZ) {
            return (chunkZ & this.factor) * this.viewDistance + (chunkX & this.factor);
        }
    }
}
