package me.jellysquid.mods.sodium.mixin.features.options;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen {
    protected MixinOptionsScreen(Component title) {
        super(title);
    }

    /*@Dynamic
    //@Redirect(method = "*(Lnet/minecraft/client/gui/components/Button;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), remap = false)
    @Inject(method = "*(Lnet/minecraft/client/gui/components/Button;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void open(Button button, CallbackInfo ci) {
       // if (guiScreenIn instanceof VideoSettingsScreen) {
        this.minecraft.setScreen(new SodiumOptionsGUI(this));
        //}
        *//*else {
            this.minecraft.setScreen(guiScreenIn);
        }*//*
        ci.cancel();
    }*/
//    private void open(Button widget, CallbackInfo ci) {
//        this.minecraft.displayGuiScreen(new SodiumOptionsGUI(this));
//
//        ci.cancel();
//    }
}
