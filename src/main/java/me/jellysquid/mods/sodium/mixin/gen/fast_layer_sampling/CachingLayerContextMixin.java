package me.jellysquid.mods.sodium.mixin.gen.fast_layer_sampling;

import me.jellysquid.mods.lithium.common.world.layer.CachingLayerContextExtended;
import net.minecraft.util.FastRandom;
import net.minecraft.world.gen.LazyAreaLayerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LazyAreaLayerContext.class)
public class CachingLayerContextMixin implements CachingLayerContextExtended {
    @Shadow
    private long rval;

    @Shadow
    @Final
    private long seed;

    @Override
    public void skipInt() {
        this.rval = FastRandom.next(this.rval, this.seed);
    }
}