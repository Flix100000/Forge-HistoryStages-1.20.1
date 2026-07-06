package net.bananemdnsa.historystages.mixin.toast;

import net.bananemdnsa.historystages.client.toast.ToastLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overrides the y translation of a rendering toast with the flush-stacked offset computed by
 * {@link ToastComponentMixin}, replacing the vanilla {@code index * 32} grid position.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceMixin {

    @Shadow @Final int index;

    @ModifyArg(
            method = "render(ILnet/minecraft/client/gui/GuiGraphics;)Z",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
            index = 1)
    private float historystages$tightStackY(float originalY) {
        return ToastLayout.offset(this.index);
    }
}
