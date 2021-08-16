package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

public class LightUtil {
    /**
     * Replacement for {@link VoxelShapes#unionCoversFullCube(VoxelShape, VoxelShape)}. This implementation early-exits
     * in some common situations to avoid unnecessary computation.
     *
     * @author JellySquid
     */
    public static boolean unionCoversFullCube(VoxelShape a, VoxelShape b) {
        // At least one shape is a full cube and will match
        if (a == VoxelShapes.block() || b == VoxelShapes.block()) {
            return true;
        }

        boolean ae = a.isEmpty();
        boolean be = b.isEmpty();

        // If both shapes are empty, they can never overlap a full cube
        if (ae && be) {
            return false;
        }

        // If both shapes are the same, it is pointless to merge them
        if (a == b) {
            return coversFullCube(a);
        }

        // If one of the shapes is empty, we can skip merging as any shape merged with an empty shape is the same shape
        if (ae || be) {
            return coversFullCube(ae ? b : a);
        }

        // No special optimizations can be performed, so we need to merge both shapes and test them
        return coversFullCube(VoxelShapes.joinUnoptimized(a, b, IBooleanFunction.OR));
    }

    private static boolean coversFullCube(VoxelShape shape) {
        return !VoxelShapes.joinIsNotEmpty(VoxelShapes.block(), shape, IBooleanFunction.ONLY_FIRST);
    }
}
