package me.jellysquid.mods.sodium.mixin.block.leaves;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LeavesBlock.class)
public class MixinLeavesBlock extends Block {

    public MixinLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        if (SodiumClientMod.options().advanced.useLeavesCulling) {
            return adjacentBlockState.getBlock() instanceof LeavesBlock;
        } else {
            return super.skipRendering(state, adjacentBlockState, side);
        }
    }
}
