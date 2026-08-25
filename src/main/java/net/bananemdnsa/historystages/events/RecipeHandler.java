package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.util.lock.StageLockHelper;
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

        // Respects lock_actions["recipe"]: if "recipe" is explicitly allowed, the recipe stays visible/craftable
        if (isClientSide) {
            return StageLockHelper.isActionLockedForClient(result, "recipe");
        } else {
            return StageLockHelper.isActionLockedForServer(result, "recipe");
        }
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(RecipeHolder<?> holder) {
        return isOutputLocked(holder, false);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;
        // Global-only on both sides: this feeds RecipeManagerMixin's live recipe resolution
        // (crafting-grid output prediction, recipe book), which historically never consulted
        // individual stages. Routing the client branch through the both-scope check would
        // newly hide recipes gated only by an individual stage — see StageLockHelper for details.
        return isClientSide
                ? StageLockHelper.isRecipeLockedForClientGlobalOnly(recipeId.toString())
                : StageLockHelper.isRecipeLockedForServer(recipeId.toString());
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }
}
