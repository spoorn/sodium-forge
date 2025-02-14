package me.jellysquid.mods.sodium.mixin.alloc.chunk_random;

import me.jellysquid.mods.lithium.common.world.ChunkRandomSource;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    private final BlockPos.Mutable randomPosInChunkCachedPos = new BlockPos.Mutable();

    /**
     * @reason Avoid allocating BlockPos every invocation through using our allocation-free variant
     */
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;getBlockRandomPos(IIII)Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos redirectgetBlockRandomPos(ServerWorld serverWorld, int x, int y, int z, int mask) {
        ((ChunkRandomSource) serverWorld).getRandomPosInChunk(x, y, z, mask, this.randomPosInChunkCachedPos);

        return this.randomPosInChunkCachedPos;
    }

    /**
     * @reason Ensure an immutable block position is passed on block tick
     */
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;randomTick(Lnet/minecraft/world/server/ServerWorld;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V"))
    private void redirectBlockStateTick(BlockState blockState, ServerWorld world, BlockPos pos, Random rand) {
        blockState.randomTick(world, pos.immutable(), rand);
    }

    /**
     * @reason Ensure an immutable block position is passed on fluid tick
     */
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/fluid/FluidState;randomTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V"))
    private void redirectFluidStateTick(FluidState fluidState, World world, BlockPos pos, Random rand) {
        fluidState.randomTick(world, pos.immutable(), rand);
    }
}
