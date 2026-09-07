package net.bananemdnsa.historystages.data.lock;

import java.util.Collection;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * A way to ask the server's recipe manager for every recipe it loaded, past the gate.
 *
 * <p>{@link VisibleRecipes} takes the gated recipes out of {@code getRecipes()}, and on this
 * version that is the only method there is for reading the whole list — so the places that
 * genuinely need all of it lose their source. Later versions grew a second accessor for exactly
 * this, {@code getOrderedRecipes}; here we add our own.
 *
 * <p>Two things need it. The packet every client is sent on join and after a reload has to keep
 * carrying the locked recipes, because on the client the same list is what the player is allowed
 * to <em>see</em>: JEI and EMI draw locked recipes with a lock on them, and the editor's recipe
 * picker has to be able to find a recipe that is already locked, or nobody could ever unlock it
 * again. And the question "did the set of hidden recipes change" has to be asked of the full list,
 * or it would be comparing the answer with itself.
 *
 * <p>Mixed into {@code RecipeManager}; every instance implements it.
 */
public interface UngatedRecipes {

    /** Every loaded recipe, gate or no gate. */
    Collection<Recipe<?>> historystages$ungatedRecipes();

    /** Convenience for callers holding a plain {@link RecipeManager}. */
    static Collection<Recipe<?>> of(RecipeManager manager) {
        return ((UngatedRecipes) (Object) manager).historystages$ungatedRecipes();
    }
}
