package me.jellysquid.mods.sodium.mixin.features.options;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen {
    protected MixinOptionsScreen(Component title) {
        super(title);
    }

    @Dynamic
    @Redirect(method = "*(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    //@Inject(method = "*(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At("HEAD"), cancellable = true)
    //@Inject(method = "func_213059_g(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void open(Minecraft mc, Screen guiScreenIn) {
        if (guiScreenIn instanceof VideoSettingsScreen) {
            this.minecraft.setScreen(new SodiumOptionsGUI(this));
        }
        else {
            this.minecraft.setScreen(guiScreenIn);
        }
    }
//    private void open(Button widget, CallbackInfo ci) {
//        this.minecraft.displayGuiScreen(new SodiumOptionsGUI(this));
//
//        ci.cancel();
//    }
}
