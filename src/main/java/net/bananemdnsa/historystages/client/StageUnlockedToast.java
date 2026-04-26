package net.bananemdnsa.historystages.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class StageUnlockedToast implements Toast {

    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final int DISPLAY_TIME = 5000;

    private final Component title;
    private final Component stageName;
    private final ItemStack icon;

    public StageUnlockedToast(String stageName, ItemStack icon) {
        this.title = Component.translatable("toast.historystages.stage_unlocked");
        this.stageName = Component.literal(stageName);
        this.icon = icon != null ? icon : ItemStack.EMPTY;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        // Draw the vanilla toast background using 1.21 sprite system
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());

        // Draw title text (line 1) - yellow like vanilla advancement toasts
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 30, 7, 0xFFFFFF00, false);

        // Draw stage name (line 2) - white
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.stageName, 30, 18, 0xFFFFFFFF, false);

        // Draw the stage icon
        if (!icon.isEmpty()) {
            guiGraphics.renderFakeItem(icon, 8, 8);
        }

        return (double) timeSinceLastVisible >= (double) DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
