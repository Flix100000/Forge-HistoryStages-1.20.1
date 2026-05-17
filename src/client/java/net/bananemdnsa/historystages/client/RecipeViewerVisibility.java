package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.world.item.ItemStack;

public final class RecipeViewerVisibility {
    private RecipeViewerVisibility() {
    }

    public static void invalidateCache() {
    }

    public static boolean isOutputOfLockedRecipe(ItemStack stack) {
        return isItemRecipeActionLocked(stack);
    }

    public static boolean isItemRecipeActionLocked(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && StageLockHelper.isActionLockedForClient(stack, "recipe");
    }

    public static boolean isRecipeIdLocked(String recipeId) {
        return recipeId != null && StageManager.isRecipeIdLocked(recipeId, true);
    }
}
