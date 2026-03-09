package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class StageUnlockedToast implements Toast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");
    private static final ItemStack ICON = new ItemStack(ModItems.RESEARCH_SCROLL.get());

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

        // Draw the vanilla toast background (standard dark frame at UV 0,0)
        guiGraphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());

        // Draw title text (line 1)
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 30, 7, 0xFFFFFF00, false);

        // Draw stage name (line 2)
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.stageName, 30, 18, 0xFFFFFFFF, false);

        // Draw the research scroll as a rendered item (with its 3D model)
        guiGraphics.renderItem(ICON, 8, 8);

        return (timeSinceLastVisible - this.firstRender) >= DISPLAY_TIME
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
