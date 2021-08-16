package me.jellysquid.mods.sodium.mixin.entity.gravity_check_block_below;

import net.minecraft.block.BlockState;
import net.minecraft.util.AxisRotation;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IWorldReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.stream.Stream;

@Mixin(VoxelShapes.class)
public class VoxelShapesMixin {
    /**
     * Check the block below the entity first, as it is the block that is most likely going to cancel the movement from
     * gravity.
     */
    @Inject(method = "collide(Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/world/IWorldReader;DLnet/minecraft/util/math/shapes/ISelectionContext;Lnet/minecraft/util/AxisRotation;Ljava/util/stream/Stream;)D",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/AxisRotation;inverse()Lnet/minecraft/util/AxisRotation;", ordinal = 0),
            cancellable = true, locals = LocalCapture.NO_CAPTURE)
    private static void checkBelowFeet(AxisAlignedBB box, IWorldReader world, double movement, ISelectionContext context, AxisRotation direction, Stream<VoxelShape> shapes, CallbackInfoReturnable<Double> cir) {
        //[VanillaCopy] calculate axis of movement like vanilla: direction.opposite().cycle(...)
        //necessary due to the method not simply explicitly receiving the axis of the movement
        if (movement >= 0 || direction.inverse().cycle(Direction.Axis.Z) != Direction.Axis.Y) {
            return;
        }
        //here the movement axis must be Axis.Y, and the movement is negative / downwards
        int x = MathHelper.floor((box.minX + box.maxX) / 2);
        int y = MathHelper.ceil(box.minY) - 1;
        int z = MathHelper.floor((box.minZ + box.maxZ) / 2);
        BlockPos pos = new BlockPos(x, y, z);
        //[VanillaCopy] collide with the block below the center of the box exactly like vanilla does during block iteration
        BlockState blockState = world.getBlockState(pos);
        movement = blockState.getCollisionShape(world, pos, context).collide(Direction.Axis.Y, box.move(-x, -y, -z), movement);
        if (Math.abs(movement) < 1.0E-7D) {
            cir.setReturnValue(0.0D);
        }
    }
}
