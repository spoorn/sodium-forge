package me.jellysquid.mods.sodium.mixin.block;

import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfo;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.Cache.class)
public class MixinShapeCache implements BlockStateLightInfo {
    @Shadow
    @Final
    private VoxelShape[] occlusionShapes;

    @Shadow
    @Final
    private int lightBlock;

    @Override
    public VoxelShape[] getExtrudedFaces() {
        return this.occlusionShapes;
    }

    @Override
    public int getLightSubtracted() {
        return this.lightBlock;
    }

}
