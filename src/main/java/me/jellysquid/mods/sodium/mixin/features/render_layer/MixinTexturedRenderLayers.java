package me.jellysquid.mods.sodium.mixin.features.render_layer;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.Material;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(Sheets.class)
public class MixinTexturedRenderLayers {
    @Shadow
    @Final
    public static Map<WoodType, Material> SIGN_MATERIALS;

    // Instantiating a SpriteIdentifier every time a sign tries to grab a texture identifier causes a significant
    // performance impact as no RenderLayer will ever be cached for the sprite. Minecraft already maintains a
    // SignType -> SpriteIdentifier cache but for some reason doesn't use it.
    @Inject(method = "getSignMaterial", at = @At("HEAD"), cancellable = true, remap = false)
    private static void preGetSignTextureId(WoodType woodType, CallbackInfoReturnable<Material> cir) {
        if (SIGN_MATERIALS != null) {
            Material sprite = SIGN_MATERIALS.get(woodType);

            if (sprite != null) {
                cir.setReturnValue(sprite);
            }
        }
    }
}
