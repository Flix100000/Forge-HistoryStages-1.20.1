package net.bananemdnsa.historystages.recipe;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.init.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Turns an open scroll back into a sealed one for the same stage, without consuming the open scroll.
 *
 * <p>The open scroll works the way a smithing template does: it is the mould, not the material.
 * That makes sealed scrolls repeatable from one keepsake plus a sheet of paper, which is the
 * point — a pack that wants scarcity turns the whole recipe off.
 *
 * <p>Special rather than data-driven because the stage id has to be read off the input and written
 * into the result, and no vanilla recipe type can copy a component across like that.
 */
public class ResealScrollRecipe extends CustomRecipe {

    public ResealScrollRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // The config gate lives here rather than in recipe loading: a disabled recipe that simply
        // never matches needs no reload and cannot leave a stale entry in the recipe book.
        if (!Config.COMMON.enableScrollResealing.get()) return false;

        ItemStack scroll = ItemStack.EMPTY;
        int paper = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.RESEARCH_SCROLL_OPEN.get())) {
                if (!scroll.isEmpty()) return false;
                scroll = stack;
            } else if (stack.is(Items.PAPER)) {
                paper++;
            } else {
                return false;
            }
        }
        // An untagged open scroll belongs to no stage, so there is nothing to seal it back into.
        return paper == 1 && !scroll.isEmpty() && ScrollVariants.readStageResearch(scroll) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(ModItems.RESEARCH_SCROLL_OPEN.get())) continue;
            String stageId = ScrollVariants.readStageResearch(stack);
            if (stageId == null) break;
            return ScrollVariants.createScroll(stageId);
        }
        return ItemStack.EMPTY;
    }

    /** Hands the open scroll back to the grid; only the paper is spent. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.RESEARCH_SCROLL_OPEN.get())) {
                remaining.set(i, stack.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RESEAL_SCROLL.get();
    }
}
