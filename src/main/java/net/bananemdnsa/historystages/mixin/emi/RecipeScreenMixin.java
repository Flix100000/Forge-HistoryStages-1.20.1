package net.bananemdnsa.historystages.mixin.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import net.bananemdnsa.historystages.emi.EmiRecipeLookup;
import net.bananemdnsa.historystages.emi.LockedEmiRecipeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks hovered-stack and click resolution on stage-locked recipes in EMI's recipe view so the
 * dark "Locked" overlay drawn by {@link LockedEmiRecipeDecorator} cannot be bypassed by reading the
 * stack under the mouse or clicking through it. (Slot highlight + item tooltip are handled by
 * {@code SlotWidgetMixin}.)
 *
 * <p>EMI has no per-recipe widget like JEI's RecipeLayout; the recipe screen
 * ({@code dev.emi.emi.screen.RecipeScreen}) tracks a single {@code hoveredWidget}. Output/catalyst
 * slots carry their recipe via {@link SlotWidget#getRecipe()}; input slots do not, so we fall back
 * to {@link EmiRecipeLookup} to find the owning recipe.
 *
 * <p>{@code @Pseudo} + {@code require = 0} means the mixin is silently skipped when EMI is absent or
 * a targeted member moves. Mirrors mixin/jei/RecipeLayoutMixin.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.RecipeScreen", remap = false)
public abstract class RecipeScreenMixin {

    @Shadow
    private Widget hoveredWidget;

    private boolean historystages$hoveredLocked() {
        Widget hovered = this.hoveredWidget;
        if (hovered == null) return false;

        EmiRecipe recipe = null;
        if (hovered instanceof SlotWidget slot) {
            recipe = slot.getRecipe();
        }
        if (recipe == null) {
            recipe = EmiRecipeLookup.recipeOwning(hovered);
        }
        return recipe != null && LockedEmiRecipeDecorator.isRecipeLocked(recipe);
    }

    /**
     * Suppress the stack reported under the mouse (ingredient lookups, recipe trees, cheating items
     * in, etc.) when hovering a locked recipe.
     */
    @Inject(method = "getHoveredStack", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$blockHoveredStack(CallbackInfoReturnable<EmiIngredient> cir) {
        if (historystages$hoveredLocked()) {
            cir.setReturnValue(EmiStack.EMPTY);
        }
    }

    /**
     * Swallow clicks that land on a locked recipe so view-recipe / cheat / resolve actions cannot
     * fire through the overlay.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$blockClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (historystages$hoveredLocked()) {
            cir.setReturnValue(true);
        }
    }
}
