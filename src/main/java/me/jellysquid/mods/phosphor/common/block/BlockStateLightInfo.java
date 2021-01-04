package me.jellysquid.mods.phosphor.common.block;

import net.minecraft.util.math.shapes.VoxelShape;

public interface BlockStateLightInfo {
    VoxelShape[] getExtrudedFaces();

    int getLightSubtracted();
}
