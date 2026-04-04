package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Draws a "locked" overlay on EMI recipes whose output items or recipe IDs are stage-locked.
 * Registered globally for all EMI recipe categories.
 */
public class LockedEmiRecipeDecorator implements EmiRecipeDecorator {

    @Override
    public void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        if (!isRecipeLocked(recipe)) return;

        int width = widgets.getWidth();
        int height = widgets.getHeight();

        // Semi-transparent dark overlay rendered above everything
        widgets.addDrawable(0, 0, width, height, (guiGraphics, mouseX, mouseY, delta) -> {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400);

            guiGraphics.fill(0, 0, width, height, 0xBB000000);

            Font font = Minecraft.getInstance().font;
            String text = "\u00A7c\u00A7l\u2716 Locked";
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (width - textWidth) / 2, height / 2 - 4, 0xFFFFFF, true);

            guiGraphics.pose().popPose();
        }).tooltipText(java.util.List.of(
                Component.literal("\u00A7c\u00A7lStage Locked"),
                Component.literal("\u00A77This recipe requires a stage that"),
                Component.literal("\u00A77has not been unlocked yet.")
        ));
    }

    private boolean isRecipeLocked(EmiRecipe recipe) {
        // 1. Check by recipe ID
        ResourceLocation recipeId = recipe.getId();
        if (recipeId != null && StageManager.isRecipeIdLocked(recipeId.toString(), true)) {
            return true;
        }

        // 2. Check via backing vanilla recipe
        Recipe<?> backing = recipe.getBackingRecipe();
        if (backing != null) {
            ResourceLocation backingId = backing.getId();
            if (backingId != null && StageManager.isRecipeIdLocked(backingId.toString(), true)) {
                return true;
            }
        }

        // 3. Check by output items
        for (EmiStack output : recipe.getOutputs()) {
            ItemStack stack = output.getItemStack();
            if (!stack.isEmpty() && StageManager.isItemLocked(stack, true)) {
                return true;
            }
        }

        return false;
    }
}
