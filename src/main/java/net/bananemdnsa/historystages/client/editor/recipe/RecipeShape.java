package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Turns a recipe into something drawable: a layout, and the stacks that fill its slots.
 *
 * <p>The distinction that matters is shaped versus everything else. A shaped recipe carries a
 * width and a height, and its holes are the whole reason a card beats a row of icons — two
 * recipes with identical ingredients in a different arrangement look identical until you draw the
 * pattern. Everything else has only a flat list.
 *
 * <p>An ingredient can match several items; the first is shown, the same choice the picker and
 * the preview popup already made.
 */
public final class RecipeShape {

    private final RecipeCardLayout layout;
    private final List<ItemStack> slots;

    private RecipeShape(RecipeCardLayout layout, List<ItemStack> slots) {
        this.layout = layout;
        this.slots = slots;
    }

    public RecipeCardLayout layout() {
        return layout;
    }

    /** One entry per slot in the layout; empty stacks are the holes in a shaped pattern. */
    public List<ItemStack> slots() {
        return Collections.unmodifiableList(slots);
    }

    /** Reads a recipe. Never throws for a malformed one — it degrades to a flat sequence. */
    public static RecipeShape of(Recipe<?> recipe) {
        return of(recipe, 0);
    }

    /**
     * As {@link #of(Recipe)}, plus room for {@code fluidCount} fluid slots under the input grid.
     *
     * <p>The count comes from the caller because a recipe's shape and the fluids it mentions come
     * from two different places: the shape from the recipe itself, the fluids from an index that
     * reads the recipe's serialised form. Nothing in the recipe answers for both.
     */
    public static RecipeShape of(Recipe<?> recipe, int fluidCount) {
        RecipeShape read = read(recipe);
        return new RecipeShape(read.layout.withFluids(fluidCount), read.slots);
    }

    private static RecipeShape read(Recipe<?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();

        if (recipe instanceof ShapedRecipe shaped) {
            int w = shaped.getWidth();
            int h = shaped.getHeight();
            RecipeCardLayout layout = RecipeCardLayout.shaped(w, h);
            List<ItemStack> slots = new ArrayList<>(layout.slotCount());
            // ShapedRecipePattern.unpack() fills its ingredient list with
            // nonnulllist.set(col + width * row, ingredient), i.e. row-major over width*height —
            // exactly the order RecipeCardLayout.slotX/slotY expect, so the holes stay where the
            // recipe put them. Confirmed against ShapedRecipePattern's own matches() indexing
            // (ingredients.get(j + i * width)) and against StageDetailScreen.renderRecipePopup's
            // existing grid draw (idx = row * gridCols + col), which agree with each other.
            for (int i = 0; i < layout.slotCount(); i++) {
                slots.add(i < ingredients.size() ? firstStackOf(ingredients.get(i)) : ItemStack.EMPTY);
            }
            return new RecipeShape(layout, slots);
        }

        List<ItemStack> present = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack stack = firstStackOf(ingredient);
            if (!stack.isEmpty()) present.add(stack);
        }

        RecipeCardLayout layout;
        if (recipe.getType() == RecipeType.CRAFTING) {
            layout = RecipeCardLayout.shapeless(present.size());
        } else if (present.size() <= 1) {
            layout = RecipeCardLayout.single();
        } else {
            layout = RecipeCardLayout.sequence(present.size());
        }

        List<ItemStack> slots = new ArrayList<>(layout.slotCount());
        for (int i = 0; i < layout.slotCount(); i++) {
            slots.add(i < present.size() ? present.get(i) : ItemStack.EMPTY);
        }
        return new RecipeShape(layout, slots);
    }

    private static ItemStack firstStackOf(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length > 0 ? items[0] : ItemStack.EMPTY;
    }
}
