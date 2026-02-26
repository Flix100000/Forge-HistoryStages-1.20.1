package net.felix.historystages.events;

import net.felix.historystages.data.StageManager;
import net.felix.historystages.util.StageData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

public class RecipeHandler {
    /**
     * Checks if a recipe's output is locked based on history stages.
     * * @param recipe The recipe to check
     * @return true if the output item is locked for the current server state
     */
    public static boolean isOutputLocked(Recipe<?> recipe) {
        if (recipe == null) return false;

        ItemStack result;
        try {
            // Attempt to get the result item safely
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (IllegalStateException e) {
            // Skip recipes that require registries not yet available (e.g., Smithing Trims during early load)
            return false;
        }

        if (result.isEmpty()) return false;

        ResourceLocation resLoc = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (resLoc == null) return false;

        String itemId = resLoc.toString();
        String modId = resLoc.getNamespace();

        // Check if the item or its mod/tags are associated with a stage
        String requiredStage = StageManager.getStageForItemOrMod(itemId, modId);

        if (requiredStage != null) {
            // If a stage is required, check if it's NOT in the server cache
            return !StageData.SERVER_CACHE.contains(requiredStage);
        }

        return false;
    }
}