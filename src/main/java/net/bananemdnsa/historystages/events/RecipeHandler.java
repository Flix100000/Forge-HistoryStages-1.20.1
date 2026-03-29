package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeHandler {
    /**
     * Checks if a recipe's output is locked based on history stages.
     * Uses ClientStageCache on the client side, SERVER_CACHE on the server side.
     */
    public static boolean isOutputLocked(RecipeHolder<?> holder, boolean isClientSide) {
        if (holder == null) return false;

        ItemStack result;
        try {
            result = holder.value().getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            return false;
        }

        if (result.isEmpty()) return false;

        return StageManager.isItemLocked(result, isClientSide);
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(RecipeHolder<?> holder) {
        return isOutputLocked(holder, false);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;
        return StageManager.isRecipeIdLocked(recipeId.toString(), isClientSide);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }
}
