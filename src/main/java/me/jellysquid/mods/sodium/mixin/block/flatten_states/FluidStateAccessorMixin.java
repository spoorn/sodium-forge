package me.jellysquid.mods.sodium.mixin.block.flatten_states;

import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Fluid.class)
public interface FluidStateAccessorMixin {

    @Invoker("isEmpty")
    boolean isEmpty();
}
