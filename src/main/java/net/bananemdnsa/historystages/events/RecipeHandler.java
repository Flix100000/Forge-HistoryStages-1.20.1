package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public class RecipeHandler {
    /**
     * Checks if a recipe's output is locked based on history stages.
     * Uses ClientStageCache on the client side, SERVER_CACHE on the server side.
     */
    public static boolean isOutputLocked(Recipe<?> recipe, boolean isClientSide) {
        if (recipe == null) return false;

        ItemStack result;
        try {
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            return false;
        }

        if (result.isEmpty()) return false;

        return StageManager.isItemLocked(result, isClientSide);
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(Recipe<?> recipe) {
        return isOutputLocked(recipe, false);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;
        return StageManager.isRecipeIdLocked(recipeId.toString(), isClientSide);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }
}