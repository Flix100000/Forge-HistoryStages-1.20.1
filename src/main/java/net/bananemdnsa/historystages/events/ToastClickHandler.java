package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.toast.DismissibleToast;
import net.bananemdnsa.historystages.client.toast.ToastLayout;
import net.bananemdnsa.historystages.mixin.toast.ToastComponentAccessor;
import net.bananemdnsa.historystages.mixin.toast.ToastInstanceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Dismisses one of this mod's toasts when the player clicks it. Toasts are only clickable while a
 * screen is open (the cursor is grabbed during normal play), which is exactly when the editor's
 * toasts appear. The toast under the cursor is hidden and the click is consumed so it doesn't also
 * fall through to the screen below.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ToastClickHandler {

    private ToastClickHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        ToastComponent toasts = mc.getToasts();
        List<?> visible = ((ToastComponentAccessor) toasts).historystages$getVisible();
        if (visible.isEmpty()) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        for (Object instance : visible) {
            if (instance == null) {
                continue;
            }
            ToastInstanceAccessor accessor = (ToastInstanceAccessor) instance;
            Toast toast = accessor.historystages$getToast();
            if (!(toast instanceof DismissibleToast dismissible)) {
                continue;
            }

            int top = ToastLayout.offset(accessor.historystages$getIndex());
            int left = screenWidth - toast.width();
            if (mouseX >= left && mouseX <= screenWidth && mouseY >= top && mouseY <= top + toast.height()) {
                dismissible.dismiss();
                event.setCanceled(true);
                return;
            }
        }
    }
}
