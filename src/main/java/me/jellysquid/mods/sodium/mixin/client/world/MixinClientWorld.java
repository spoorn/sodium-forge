package me.jellysquid.mods.sodium.mixin.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public abstract class MixinClientWorld
{
    @Redirect(
        method = "unload",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;enableLightSources(Lnet/minecraft/world/level/ChunkPos;Z)V"
        ), remap = false
    )
    private void cancelDisableLightUpdates(final LevelLightEngine levelLightEngine, final ChunkPos pos, final boolean enable) {
    }
}
