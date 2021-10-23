package me.jellysquid.mods.sodium.mixin.features.options;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftScreen {

    @Shadow
    public abstract void setScreen(@Nullable Screen p_91153_);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, remap = false)
    public void setScreenToSodium(Screen p_91153_, CallbackInfo ci) {
        if (p_91153_ instanceof VideoSettingsScreen) {
            this.setScreen(new SodiumOptionsGUI(((VideoSettingsScreen) p_91153_).lastScreen));
            ci.cancel();
        }
    }
}
