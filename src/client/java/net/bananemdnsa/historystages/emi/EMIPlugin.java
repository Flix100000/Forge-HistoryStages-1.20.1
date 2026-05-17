package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.client.RecipeViewerVisibility;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeDecorator((recipe, widgets) -> {
            if (!isLockedRecipe(recipe)) {
                return;
            }

            int width = widgets.getWidth();
            int height = widgets.getHeight();
            widgets.addDrawable(0, 0, width, height, (draw, mouseX, mouseY, delta) -> {
                Font font = Minecraft.getInstance().font;
                String text = "\u00A7c\u00A7l\u2716 Locked";
                int textX = (width - font.width(text)) / 2;
                int textY = (height - 8) / 2;
                draw.pose().pushPose();
                draw.pose().translate(0, 0, 400);
                draw.fill(0, 0, width, height, 0xBB000000);
                draw.drawString(font, text, textX, textY, 0xFFFFFF, true);
                draw.pose().popPose();
            }).tooltipText(java.util.List.of(
                    Component.literal("\u00A7c\u00A7lStage Locked"),
                    Component.literal("\u00A77This recipe requires a stage that"),
                    Component.literal("\u00A77has not been unlocked yet.")
            ));
        });
    }

    private static boolean isLockedRecipe(EmiRecipe recipe) {
        if (recipe.getId() != null && StageManager.isRecipeIdLocked(recipe.getId().toString(), true)) {
            return true;
        }
        for (EmiStack output : recipe.getOutputs()) {
            ItemStack stack = output.getItemStack();
            if (!stack.isEmpty() && RecipeViewerVisibility.isItemRecipeActionLocked(stack)) {
                return true;
            }
        }
        return false;
    }
}
