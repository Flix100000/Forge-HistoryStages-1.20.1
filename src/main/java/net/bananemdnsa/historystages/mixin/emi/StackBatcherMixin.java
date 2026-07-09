package net.bananemdnsa.historystages.mixin.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws lock-icon overlays on locked items in EMI's <b>ingredient/index panel</b>.
 *
 * <p>EMI batches the index grid's item models through {@code StackBatcher} rather than
 * {@code ItemEmiStack#render}, so {@link ItemEmiStackMixin} alone does not cover the
 * panel. The panel loop calls {@code StackBatcher#render(EmiIngredient, GuiGraphics,
 * int, int, float)} once per visible slot with the slot's screen position, which is the
 * clean per-slot hook used here. Icon logic is shared with the vanilla-inventory
 * decorator via {@link LockIconRenderer}.
 *
 * <p>{@code StackBatcher} is an EMI internal, so this uses @Pseudo + require=0 and is
 * silently skipped when EMI is absent or the method moves. When EMI's batching is
 * disabled this method delegates to {@code ItemEmiStack#render} (already decorated by
 * {@link ItemEmiStackMixin}); the resulting double draw is at the same position with the
 * same icon and is visually harmless.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.StackBatcher", remap = false)
public abstract class StackBatcherMixin {

    @Inject(
            method = "render(Ldev/emi/emi/api/stack/EmiIngredient;Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"), require = 0)
    private void historystages$drawLockIcon(EmiIngredient ingredient, GuiGraphics draw,
                                            int x, int y, float delta, CallbackInfo ci) {
        try {
            ResourceLocation icon = LockIconRenderer.iconFor(historystages$firstItem(ingredient));
            if (icon != null) LockIconRenderer.draw(draw, icon, x, y);
        } catch (Throwable ignored) {
        }
    }

    private static ItemStack historystages$firstItem(EmiIngredient ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        for (EmiStack es : ingredient.getEmiStacks()) {
            ItemStack is = es.getItemStack();
            if (is != null && !is.isEmpty()) return is;
        }
        return ItemStack.EMPTY;
    }
}
