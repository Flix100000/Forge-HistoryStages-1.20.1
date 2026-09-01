package net.bananemdnsa.historystages.mixin.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The fluid counterpart of {@link ItemEmiStackMixin}: draws the lock overlay on gated fluids in
 * EMI's ingredient panel, recipe slots and favourites.
 *
 * <p>Same reason it is needed as on the JEI side — the overlay everything else gets comes from a
 * NeoForge item decorator, and a fluid is not an item.
 *
 * <p>{@code getKey()} on a {@code FluidEmiStack} hands back the {@code Fluid} itself. Asked
 * through the shadowed API method rather than the private field, so this keeps working if EMI
 * renames the field.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.stack.FluidEmiStack", remap = false)
public abstract class FluidEmiStackMixin {

    @Shadow
    public abstract Object getKey();

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void historystages$drawFluidLockIcon(GuiGraphics draw, int x, int y, float delta,
                                                 int flags, CallbackInfo ci) {
        // Only overlay when the icon itself is being drawn, not on amount-only passes.
        if ((flags & EmiIngredient.RENDER_ICON) == 0) return;
        try {
            if (!(getKey() instanceof Fluid fluid)) return;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id == null) return;

            ResourceLocation icon = LockIconRenderer.iconForFluid(id.toString());
            if (icon != null) LockIconRenderer.draw(draw, icon, x, y);
        } catch (Throwable ignored) {
        }
    }
}
