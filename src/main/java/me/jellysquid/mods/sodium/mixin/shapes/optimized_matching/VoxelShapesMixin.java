package me.jellysquid.mods.sodium.mixin.shapes.optimized_matching;

import me.jellysquid.mods.lithium.common.shapes.VoxelShapeMatchesAnywhere;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VoxelShapes.class)
public class VoxelShapesMixin {
    @Inject(method = "compare",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/util/math/shapes/VoxelShape;getValues(Lnet/minecraft/util/Direction$Axis;)Lit/unimi/dsi/fastutil/doubles/DoubleList;",
                    ordinal = 0),
            cancellable = true)
    private static void cuboidMatchesAnywhere(VoxelShape shapeA, VoxelShape shapeB, IBooleanFunction predicate, CallbackInfoReturnable<Boolean> cir) {
        VoxelShapeMatchesAnywhere.cuboidMatchesAnywhere(shapeA, shapeB, predicate, cir);
    }
}