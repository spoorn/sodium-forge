package me.jellysquid.mods.lithium.common.world.layer;

import net.minecraft.world.gen.IExtendedNoiseRandom;
import net.minecraft.world.gen.area.IArea;

public interface CloneableContext<R extends IArea> {
    IExtendedNoiseRandom<R> cloneContext();
}
