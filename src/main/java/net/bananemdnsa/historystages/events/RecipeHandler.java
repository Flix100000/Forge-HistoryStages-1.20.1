package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
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

        // Respects lock_actions["recipe"]: if "recipe" is explicitly allowed, the recipe stays visible/craftable
        if (isClientSide) {
            return StageLockHelper.isActionLockedForClient(result, "recipe");
        } else {
            return StageLockHelper.isActionLockedForServer(result, "recipe");
        }
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(Recipe<?> recipe) {
        return isOutputLocked(recipe, false);
    }

    /**
     * Whether this recipe is gated for everyone on the server, by either route: its own id on a
     * stage, or an item it produces whose lock covers {@code recipe}.
     *
     * <p>Deliberately blind to who is asking. This is the answer given to a station that asked for
     * the whole recipe list rather than for one recipe, and that answer is cached and handed to
     * every station after it — so a per-player verdict here would let whoever walked past first
     * decide for everybody. Individual stages keep to the paths where a player is actually named.
     */
    public static boolean isLockedForEveryone(Recipe<?> recipe) {
        if (recipe == null) return false;

        ItemStack result;
        try {
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            result = ItemStack.EMPTY;
        }
        if (!result.isEmpty() && StageLockHelper.isActionLockedForServer(result, "recipe")) return true;

        return StageManager.isRecipeIdLockedForServer(recipe.getId().toString());
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;
        return StageManager.isRecipeIdLocked(recipeId.toString(), isClientSide);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }
}