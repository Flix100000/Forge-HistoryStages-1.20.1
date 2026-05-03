package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

public final class RecipeHandler {
    private RecipeHandler() {
    }

    public static boolean isOutputLocked(Recipe<?> recipe, boolean isClientSide) {
        if (recipe == null) {
            return false;
        }
        ItemStack result;
        try {
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (Exception ignored) {
            return false;
        }
        return !result.isEmpty() && StageManager.isItemLocked(result, isClientSide);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        return recipeId != null && StageManager.isRecipeIdLocked(recipeId.toString(), isClientSide);
    }
}
