package me.jellysquid.mods.sodium.mixin.features.options;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoSettingsScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen {
    protected MixinOptionsScreen(ITextComponent title) {
        super(title);
    }

    @Dynamic
    @Redirect(method = "*(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    //@Inject(method = "*(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At("HEAD"), cancellable = true)
    //@Inject(method = "lambda$init$5(Lnet/minecraft/client/gui/widget/button/Button;)V", at = @At("HEAD"), cancellable = true, remap = false)
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
