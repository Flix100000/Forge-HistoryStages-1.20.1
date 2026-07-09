package net.bananemdnsa.historystages.mixin.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.bananemdnsa.historystages.emi.EmiRecipeLookup;
import net.bananemdnsa.historystages.emi.LockedEmiRecipeDecorator;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Makes slots of a stage-locked recipe non-interactive so the dark "Locked" overlay drawn by
 * {@link LockedEmiRecipeDecorator} reads cleanly: it suppresses the white slot hover-highlight
 * (which otherwise bleeds through the overlay) and replaces the slot's item tooltip with the
 * "Stage Locked" message.
 *
 * <p>Output/catalyst slots know their recipe via {@code getRecipe()}; input slots do not, so this
 * falls back to {@link EmiRecipeLookup} which finds the owning recipe via the active recipe screen.
 * {@code dev.emi.emi.api.widget.SlotWidget} is an EMI API class; @Pseudo + require=0 keep this a
 * no-op when EMI is absent. Index-panel slots have no recipe and are unaffected.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.widget.SlotWidget", remap = false)
public abstract class SlotWidgetMixin {

    @Shadow
    public abstract EmiRecipe getRecipe();

    private boolean historystages$locked() {
        try {
            EmiRecipe recipe = getRecipe();
            if (recipe == null) {
                // Input slots carry no recipe context — find the owning recipe via the screen.
                recipe = EmiRecipeLookup.recipeOwning(this);
            }
            return recipe != null && LockedEmiRecipeDecorator.isRecipeLocked(recipe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "shouldDrawSlotHighlight", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$noHighlightWhenLocked(int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (historystages$locked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$lockedTooltip(int mouseX, int mouseY, CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        if (historystages$locked()) {
            cir.setReturnValue(LockedEmiRecipeDecorator.lockedTooltipLines().stream()
                    .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                    .toList());
        }
    }
}
