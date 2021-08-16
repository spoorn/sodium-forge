package me.jellysquid.mods.sodium.mixin.shapes.precompute_shape_arrays;

import net.minecraft.util.math.shapes.DoubleRangeList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(DoubleRangeList.class)
public class FractionalDoubleListMixin {
    @Shadow
    @Final
    private int parts;
    private double scale;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initScale(int sectionCount, CallbackInfo ci) {
        this.scale = 1.0D / this.parts;
    }

    /**
     * @author JellySquid
     * @reason replace division with multiplication
     */
    @Overwrite
    public double getDouble(int position) {
        return position * this.scale;
    }
}
