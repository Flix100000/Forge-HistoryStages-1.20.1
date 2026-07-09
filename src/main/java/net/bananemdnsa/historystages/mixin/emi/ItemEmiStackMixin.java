package net.bananemdnsa.historystages.mixin.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws lock-icon overlays on locked items in EMI's ingredient panel, recipe slots,
 * and favorites by hooking EMI's item-stack render. This is the EMI-panel counterpart
 * to the vanilla-inventory {@link net.bananemdnsa.historystages.client.LockDecorator}
 * and shares its icon logic via {@link LockIconRenderer}.
 *
 * <p>{@code dev.emi.emi.api.stack.ItemEmiStack} is an EMI API class, but EMI may be
 * absent at runtime, so this uses @Pseudo + require=0 to be silently skipped when EMI
 * is not installed. {@code EmiIngredient.RENDER_ICON} is a compile-time constant, so
 * the reference inlines to a literal and adds no runtime dependency.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.stack.ItemEmiStack", remap = false)
public abstract class ItemEmiStackMixin {

    @Shadow
    public abstract ItemStack getItemStack();

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void historystages$drawLockIcon(GuiGraphics draw, int x, int y, float delta, int flags, CallbackInfo ci) {
        // Only overlay when the item icon itself is being drawn (not amount-only passes).
        if ((flags & EmiIngredient.RENDER_ICON) == 0) return;
        try {
            ResourceLocation icon = LockIconRenderer.iconFor(getItemStack());
            if (icon != null) LockIconRenderer.draw(draw, icon, x, y);
        } catch (Throwable ignored) {
        }
    }
}
