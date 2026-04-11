package net.bananemdnsa.historystages.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Draws a "locked" overlay on recipes whose output items or recipe IDs are stage-locked.
 * Renders at a high z-level to appear above item icons.
 */
public class LockedRecipeDecorator<T> implements IRecipeCategoryDecorator<T> {

    @Override
    public void draw(T recipe, IRecipeCategory<T> category, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        if (!isRecipeLocked(recipe, recipeSlotsView)) return;

        int width = category.getWidth();
        int height = category.getHeight();

        // Push pose and translate to z=400 so overlay renders above item icons (items are ~200)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        // Semi-transparent dark overlay
        guiGraphics.fill(0, 0, width, height, 0xBB000000);

        // "Locked" text centered
        Font font = Minecraft.getInstance().font;
        String text = "\u00A7c\u00A7l\u2716 Locked";
        int textWidth = font.width(text);
        guiGraphics.drawString(font, text, (width - textWidth) / 2, height / 2 - 4, 0xFFFFFF, true);

        guiGraphics.pose().popPose();
    }

    @Override
    public List<Component> decorateExistingTooltips(List<Component> tooltips, T recipe,
                                                     IRecipeCategory<T> category,
                                                     IRecipeSlotsView recipeSlotsView,
                                                     double mouseX, double mouseY) {
        if (isRecipeLocked(recipe, recipeSlotsView)) {
            tooltips.add(Component.empty());
            tooltips.add(Component.literal("\u00A7c\u00A7lStage Locked"));
            tooltips.add(Component.literal("\u00A77This recipe requires a stage that"));
            tooltips.add(Component.literal("\u00A77has not been unlocked yet."));
        }
        return tooltips;
    }

    private boolean isRecipeLocked(T recipe, IRecipeSlotsView recipeSlotsView) {
        // 1. Check by recipe ID if it's a vanilla RecipeHolder
        if (recipe instanceof RecipeHolder<?> holder) {
            ResourceLocation recipeId = holder.id();
            if (recipeId != null && StageManager.isRecipeIdLocked(recipeId.toString(), true)) {
                return true;
            }
        }

        // 2. Check by output items (works for all recipe types including modded)
        List<IRecipeSlotView> outputSlots = recipeSlotsView.getSlotViews(RecipeIngredientRole.OUTPUT);
        for (IRecipeSlotView slot : outputSlots) {
            var displayed = slot.getDisplayedItemStack();
            if (displayed.isPresent()) {
                ItemStack stack = displayed.get();
                if (!stack.isEmpty() && StageManager.isItemLocked(stack, true)) {
                    return true;
                }
            }
        }

        return false;
    }
}
