package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class RecipeHandler {
    private RecipeHandler() {
    }

    public static boolean isOutputLocked(Recipe<?> recipe, boolean isClientSide) {
        return isOutputLocked(recipe, RegistryAccess.EMPTY, isClientSide);
    }

    public static boolean isOutputLocked(Recipe<?> recipe, RegistryAccess registryAccess, boolean isClientSide) {
        if (recipe == null) {
            return false;
        }
        ItemStack result;
        try {
            result = recipe.getResultItem(registryAccess);
        } catch (Exception ignored) {
            return false;
        }
        if (result.isEmpty()) {
            return false;
        }
        return isClientSide
                ? StageLockHelper.isActionLockedForClient(result, "recipe")
                : StageLockHelper.isActionLockedForServer(result, "recipe");
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        return recipeId != null && StageManager.isRecipeIdLocked(recipeId.toString(), isClientSide);
    }

    public static boolean isRecipeLocked(RecipeHolder<?> recipe, RegistryAccess registryAccess, boolean isClientSide) {
        return recipe != null
                && (isOutputLocked(recipe.value(), registryAccess, isClientSide)
                || isRecipeIdLocked(recipe.id(), isClientSide));
    }

    public static boolean isCraftingResultLocked(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return false;
        }
        if (level.isClientSide()
                ? StageLockHelper.isActionLockedForClient(stack, "recipe")
                : StageLockHelper.isActionLockedForServer(stack, "recipe")) {
            return true;
        }
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!isRecipeIdLocked(holder.id(), level.isClientSide())) {
                continue;
            }
            ItemStack result = getResult(holder.value(), level.registryAccess());
            if (!result.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack getResult(Recipe<?> recipe, RegistryAccess registryAccess) {
        try {
            return recipe.getResultItem(registryAccess);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
