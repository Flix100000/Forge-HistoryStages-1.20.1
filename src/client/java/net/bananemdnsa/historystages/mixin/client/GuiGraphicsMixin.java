package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.client.LockOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("TAIL"), require = 0)
    private void historystages$renderLockOverlay(ItemStack stack, int x, int y, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, Minecraft.getInstance().font, stack, x, y);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"), require = 0)
    private void historystages$renderLockOverlaySeeded(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, Minecraft.getInstance().font, stack, x, y);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("TAIL"), require = 0)
    private void historystages$renderLockOverlayLayered(ItemStack stack, int x, int y, int seed, int z, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, Minecraft.getInstance().font, stack, x, y);
    }

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("TAIL"), require = 0)
    private void historystages$renderFakeItemLockOverlay(ItemStack stack, int x, int y, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, Minecraft.getInstance().font, stack, x, y);
    }

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"), require = 0)
    private void historystages$renderFakeItemLockOverlayLayered(ItemStack stack, int x, int y, int z, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, Minecraft.getInstance().font, stack, x, y);
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("TAIL"), require = 0)
    private void historystages$renderDecoratedLockOverlay(net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, font, stack, x, y);
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"), require = 0)
    private void historystages$renderDecoratedLockOverlayWithText(net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        LockOverlayRenderer.render((GuiGraphics) (Object) this, font, stack, x, y);
    }
}
