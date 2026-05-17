package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.client.editor.EditorBlurController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class EditorScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void historystages$skipEditorVanillaBackground(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (EditorBlurController.isManagedScreen(minecraft.screen)) {
            ci.cancel();
        }
    }
}
