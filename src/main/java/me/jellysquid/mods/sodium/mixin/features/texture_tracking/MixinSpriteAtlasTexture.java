package me.jellysquid.mods.sodium.mixin.features.texture_tracking;

import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureAtlas.class)
public class MixinSpriteAtlasTexture {
    @Inject(method = "getSprite", at = @At("RETURN"), remap = false)
    private void preReturnSprite(CallbackInfoReturnable<TextureAtlasSprite> cir) {
        TextureAtlasSprite TextureAtlasSprite = cir.getReturnValue();

        if (TextureAtlasSprite != null) {
            SpriteUtil.markSpriteActive(TextureAtlasSprite);
        }
    }
}
