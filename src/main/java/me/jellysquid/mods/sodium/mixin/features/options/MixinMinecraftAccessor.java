package me.jellysquid.mods.sodium.mixin.features.options;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MixinMinecraftAccessor {
    @Accessor("fps")
    abstract int getFPSCounter();
}
