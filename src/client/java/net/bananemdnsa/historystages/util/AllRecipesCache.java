package net.bananemdnsa.historystages.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.List;

public final class AllRecipesCache {
    private AllRecipesCache() {
    }

    public static Collection<RecipeHolder<?>> get() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        return minecraft.level.getRecipeManager().getRecipes();
    }
}
