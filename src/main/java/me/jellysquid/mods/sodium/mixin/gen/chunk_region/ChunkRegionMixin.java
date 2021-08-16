package me.jellysquid.mods.sodium.mixin.gen.chunk_region;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldGenRegion.class)
public abstract class ChunkRegionMixin {
    @Shadow
    @Final
    private ChunkPos firstPos;

    @Shadow
    @Final
    private int size;

    // Array view of the chunks in the region to avoid an unnecessary de-reference
    private IChunk[] chunksArr;

    // The starting position of this region
    private int minChunkX, minChunkZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(ServerWorld world, List<IChunk> chunks, CallbackInfo ci) {
        this.minChunkX = this.firstPos.x;
        this.minChunkZ = this.firstPos.z;

        this.chunksArr = chunks.toArray(new IChunk[0]);
    }

    /**
     * @reason Avoid pointer de-referencing, make method easier to inline
     * @author JellySquid
     */
    @Overwrite
    public BlockState getBlockState(BlockPos pos) {
        int x = (pos.getX() >> 4) - this.minChunkX;
        int z = (pos.getZ() >> 4) - this.minChunkZ;
        int w = this.size;

        if (x >= 0 && z >= 0 && x < w && z < w) {
            return this.chunksArr[x + z * w].getBlockState(pos);
        } else {
            throw new NullPointerException("No chunk exists at " + new ChunkPos(pos));
        }
    }

    /**
     * @reason Use our block fetch function
     * @author JellySquid
     */
    @Overwrite
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }
}
