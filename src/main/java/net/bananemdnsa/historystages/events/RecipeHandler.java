package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeHandler {
    public static boolean isOutputLocked(RecipeHolder<?> holder) {
        if (holder == null) return false;

        ItemStack result;
        try {
            result = holder.value().getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            return false;
        }

        if (result.isEmpty()) return false;

        return StageManager.isItemLockedForServer(result);
    }
}
