package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
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
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (IllegalStateException e) {
            return false;
        }

        if (result.isEmpty()) return false;

        // Nutze direkt die wasserdichte "Alle-oder-Nichts" Logik aus dem StageManager
        return StageManager.isItemLockedForServer(result);
    }
}