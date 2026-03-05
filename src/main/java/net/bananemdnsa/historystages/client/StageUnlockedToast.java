package net.bananemdnsa.historystages.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class StageUnlockedToast implements Toast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");
    private static final ResourceLocation ICON = new ResourceLocation("historystages", "textures/item/research_scroll.png");

    private final Component title;
    private final Component stageName;
    private long firstRender = -1;
    private static final long DISPLAY_TIME = 5000L;

    public StageUnlockedToast(String stageName) {
        this.title = Component.translatable("toast.historystages.stage_unlocked");
        this.stageName = Component.literal(stageName);
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        if (this.firstRender == -1) {
            this.firstRender = timeSinceLastVisible;
        }

        // Draw the vanilla toast background (challenge frame at UV 0,32 for purple tint)
        guiGraphics.blit(TEXTURE, 0, 0, 0, 32, this.width(), this.height());

        // Draw title text (line 1)
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 30, 7, 0xFF800080, false);

        // Draw stage name (line 2)
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.stageName, 30, 18, 0xFFFFFFFF, false);

        // Draw the research scroll icon (16x16 at position 8,8)
        guiGraphics.blit(ICON, 8, 8, 0, 0, 16, 16, 16, 16);

        return (timeSinceLastVisible - this.firstRender) >= DISPLAY_TIME
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
