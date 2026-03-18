package net.bananemdnsa.historystages.util;

import net.minecraft.world.item.crafting.Recipe;

import java.util.Collection;
import java.util.Collections;

/**
 * Holds a snapshot of all recipes before stage-based filtering is applied.
 * Populated by RecipeManagerMixin, used by the in-game editor.
 */
public class AllRecipesCache {
    private static Collection<Recipe<?>> allRecipes = Collections.emptyList();

    public static void set(Collection<Recipe<?>> recipes) {
        allRecipes = recipes;
    }

    public static Collection<Recipe<?>> get() {
        return allRecipes;
    }
}
