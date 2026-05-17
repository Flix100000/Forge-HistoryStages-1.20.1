package net.bananemdnsa.historystages.mixin.client.jei;

import mezz.jei.api.ingredients.IIngredientType;
import net.bananemdnsa.historystages.client.RecipeViewerVisibility;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class RecipeLayoutMixin {

    @Shadow
    public abstract Object getRecipe();

    @Inject(method = "getSlotUnderMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$blockSlotHoverIfLocked(double mouseX, double mouseY, CallbackInfoReturnable<Optional<?>> cir) {
        if (historystages$isCurrentRecipeLocked()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getRecipeSlotUnderMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$blockRecipeSlotHoverIfLocked(double mouseX, double mouseY, CallbackInfoReturnable<Optional<?>> cir) {
        if (historystages$isCurrentRecipeLocked()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getIngredientUnderMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private void historystages$blockIngredientHoverIfLocked(int mouseX, int mouseY, IIngredientType<?> ingredientType, CallbackInfoReturnable<Optional<?>> cir) {
        if (historystages$isCurrentRecipeLocked()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean historystages$isCurrentRecipeLocked() {
        try {
            mezz.jei.api.gui.IRecipeLayoutDrawable layout = (mezz.jei.api.gui.IRecipeLayoutDrawable) this;
            ResourceLocation recipeId = layout.getRecipeCategory().getRegistryName(getRecipe());
            if (recipeId != null && RecipeViewerVisibility.isRecipeIdLocked(recipeId.toString())) {
                return true;
            }

            var slotsView = layout.getRecipeSlotsView();
            var outputSlots = slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
            for (var slot : outputSlots) {
                Optional<ItemStack> displayed = slot.getDisplayedItemStack();
                if (displayed.isPresent() && !displayed.get().isEmpty()
                        && StageLockHelper.isActionLockedForClient(displayed.get(), "recipe")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
