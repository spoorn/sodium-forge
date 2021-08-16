package me.jellysquid.mods.sodium.mixin.chunk.count_oversized_blocks;

import me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.palette.PalettedContainer;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Keep track of how many oversized blocks are in this chunk section. If none are there, collision code can skip a few blocks.
 * Oversized blocks are fences, walls, extended piston heads and blocks with dynamic bounds (scaffolding, shulker box, moving blocks)
 *
 * @author 2No2Name
 */
@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin implements ChunkAwareBlockCollisionSweeper.OversizedBlocksCounter {
    @Shadow
    public abstract void recalcBlockCounts();

    @Unique
    private short oversizedBlockCount;

    @Redirect(method = "recalcBlockCounts", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/palette/PalettedContainer;count(Lnet/minecraft/util/palette/PalettedContainer$ICountConsumer;)V"))
    private void addToOversizedBlockCount(PalettedContainer<BlockState> palettedContainer, PalettedContainer.ICountConsumer<BlockState> consumer) {
        palettedContainer.count((state, count) -> {
            consumer.accept(state, count);
            if (state.hasLargeCollisionShape()) {
                this.oversizedBlockCount += count;
            }
        });
    }

    @Inject(method = "recalcBlockCounts", at = @At("HEAD"))
    private void resetOversizedBlockCount(CallbackInfo ci) {
        this.oversizedBlockCount = 0;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;", at = @At(ordinal = 0, value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isRandomlyTicking()Z", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void decrOversizedBlockCount(int x, int y, int z, BlockState state, boolean lock, CallbackInfoReturnable<BlockState> cir, BlockState blockState2, FluidState fluidState, FluidState fluidState2) {
        if (blockState2.hasLargeCollisionShape()) {
            --this.oversizedBlockCount;
        }
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;", at = @At(ordinal = 1, value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isRandomlyTicking()Z", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void incrOversizedBlockCount(int x, int y, int z, BlockState state, boolean lock, CallbackInfoReturnable<BlockState> cir) {
        if (state.hasLargeCollisionShape()) {
            ++this.oversizedBlockCount;
        }
    }

    @Override
    public boolean hasOversizedBlocks() {
        return this.oversizedBlockCount > 0;
    }

    /**
     * Initialize oversized block count in the client worlds.
     * This also initializes other values (randomtickable blocks counter), but they are unused in the client worlds.
     */
    @OnlyIn(Dist.CLIENT)
    @Inject(method = "read", at = @At("RETURN"))
    private void initCounts(PacketBuffer packetByteBuf, CallbackInfo ci) {
        this.recalcBlockCounts();
    }
}
