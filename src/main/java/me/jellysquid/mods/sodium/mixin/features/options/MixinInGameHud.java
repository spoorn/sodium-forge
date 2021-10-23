package me.jellysquid.mods.sodium.mixin.features.options;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// forge patches ingamegui, any injections or anything to the base class are worthless
@Mixin(ForgeIngameGui.class)
public class MixinInGameHud {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/gui/OverlayRegistry$OverlayEntry;isEnabled()Z"), remap = false)
    private boolean redirectFancyGraphicsVignette(OverlayRegistry.OverlayEntry overlayEntry) {
        if (overlayEntry.getOverlay() == ForgeIngameGui.VIGNETTE_ELEMENT) {
            return SodiumClientMod.options().quality.enableVignette;
        } else {
            return overlayEntry.isEnabled();
        }
    }*/

    @Inject(at = @At("TAIL"), method = "render", remap = false)
    public void render(PoseStack matrixStack, float partialTicks, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();

        // dont show if we have f3 on
        if (!client.options.renderDebug && SodiumClientMod.options().fpsCounter) {

            String displayString = ((MixinMinecraftAccessor)client).getFPSCounter() + " fps";
            float posx = 4;
            float posy = 4;

            // account for scale?
            //double guiScale = client.getMainWindow().getGuiScaleFactor();
            //if (guiScale > 0) {
            //    posx /= guiScale;
            //    posy /= guiScale;
            //}
            int alpha = 220;
            int textColor = ((alpha & 0xFF) << 24) | 0xEEEEEE;
            client.font.drawShadow(matrixStack, displayString, posx, posy, textColor);
        }
    }
}
