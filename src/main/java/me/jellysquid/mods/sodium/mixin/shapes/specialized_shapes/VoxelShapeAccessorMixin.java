package me.jellysquid.mods.sodium.mixin.shapes.specialized_shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VoxelShape.class)
public interface VoxelShapeAccessorMixin {

    @Invoker("getValues")
    DoubleList igetValues(Direction.Axis axis);

}
