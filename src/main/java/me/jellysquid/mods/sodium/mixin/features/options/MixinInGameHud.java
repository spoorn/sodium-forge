package me.jellysquid.mods.sodium.mixin.features.options;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.ForgeIngameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// forge patches ingamegui, any injections or anything to the base class are worthless
@Mixin(ForgeIngameGui.class)
public class MixinInGameHud {
    @Redirect(method = "renderIngameGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isFancyGraphicsEnabled()Z"))
    private boolean redirectFancyGraphicsVignette() {
        return SodiumClientMod.options().quality.enableVignette;
    }

    @Inject(at = @At("TAIL"), method = "renderIngameGui")
    public void render(MatrixStack matrixStack, float partialTicks, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();

        // dont show if we have f3 on
        if (!client.gameSettings.showDebugInfo && SodiumClientMod.options().fpsCounter) {

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
            client.fontRenderer.drawStringWithShadow(matrixStack, displayString, posx, posy, textColor);
        }
    }
}
