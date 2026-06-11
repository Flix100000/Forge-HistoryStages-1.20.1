package net.bananemdnsa.historystages.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * Draws lock-icon overlays on locked items in vanilla GUIs (player inventory,
 * containers). Icon selection/drawing is shared with the EMI ingredient-panel
 * mixin via {@link LockIconRenderer}. Runs regardless of whether EMI is installed —
 * EMI's own panel is covered separately by the ItemEmiStack mixin (it does not use
 * vanilla item decorations), so there is no double-draw.
 */
public class LockDecorator implements IItemDecorator {

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        ResourceLocation icon = LockIconRenderer.iconFor(stack);
        if (icon != null) {
            LockIconRenderer.draw(guiGraphics, icon, xOffset, yOffset);
        }
        return false;
    }
}
