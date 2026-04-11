package net.bananemdnsa.historystages.mixin.jei;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Blocks slot hover events on locked recipes in JEI so the overlay cannot be bypassed.
 * Uses @Pseudo so the mixin is silently skipped when JEI is not installed.
 *
 * Adapted for MC 1.21 / JEI 19.x — recipes are wrapped in RecipeHolder<?> and
 * the getItemStackUnderMouse method no longer exists on RecipeLayout.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class RecipeLayoutMixin {

    @Shadow
    public abstract Object getRecipe();

    @Inject(method = "getSlotUnderMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockSlotHoverIfLocked(double mouseX, double mouseY, CallbackInfoReturnable<Optional<?>> cir) {
        if (isCurrentRecipeLocked()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getRecipeSlotUnderMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockRecipeSlotHoverIfLocked(double mouseX, double mouseY, CallbackInfoReturnable<Optional<?>> cir) {
        if (isCurrentRecipeLocked()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    private boolean isCurrentRecipeLocked() {
        Object recipe = getRecipe();

        // 1. Check by recipe ID — in 1.21 recipes are wrapped in RecipeHolder<?>
        if (recipe instanceof RecipeHolder<?> holder) {
            ResourceLocation recipeId = holder.id();
            if (recipeId != null && StageManager.isRecipeIdLocked(recipeId.toString(), true)) {
                return true;
            }
        }

        // 2. Check by output items — use the same logic as the decorator
        try {
            var layout = (mezz.jei.api.gui.IRecipeLayoutDrawable<?>) this;
            var slotsView = layout.getRecipeSlotsView();
            var outputSlots = slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
            for (var slot : outputSlots) {
                Optional<ItemStack> displayed = slot.getDisplayedItemStack();
                if (displayed.isPresent() && !displayed.get().isEmpty()) {
                    if (StageManager.isItemLocked(displayed.get(), true)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
