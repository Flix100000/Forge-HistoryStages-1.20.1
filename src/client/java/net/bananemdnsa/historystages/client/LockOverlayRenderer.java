package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.EditorBlurController;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class LockOverlayRenderer {
    private static final ResourceLocation LOCK_ICON = HistoryStages.id("textures/gui/lock_overlay.png");
    private static final ResourceLocation SILVER_LOCK_ICON = HistoryStages.id("textures/gui/lock_overlay_silver.png");
    private static int suppressionDepth = 0;

    private LockOverlayRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if (suppressionDepth > 0 || stack == null || stack.isEmpty() || !Config.CLIENT.showLockIcons) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (EditorBlurController.isManagedScreen(minecraft.screen) || isEditorRenderStack()) {
            return;
        }

        boolean globallyLocked = isGloballyLocked(stack);
        boolean individuallyLocked = !globallyLocked
                && Config.CLIENT.showSilverLockIcons
                && StageLockHelper.isActionLockedByIndividualStageClient(stack, "icon");

        if (!globallyLocked && !individuallyLocked) {
            return;
        }

        ResourceLocation icon = individuallyLocked ? SILVER_LOCK_ICON : LOCK_ICON;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 250);
        guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);
        guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        guiGraphics.pose().popPose();
    }

    private static boolean isGloballyLocked(ItemStack stack) {
        return StageLockHelper.isActionLockedForClient(stack, "icon");
    }

    private static boolean isRecipeViewerContext(Minecraft minecraft) {
        if (isRecipeViewerRenderStack()) {
            return true;
        }
        if (minecraft == null || minecraft.screen == null) {
            return false;
        }
        String className = minecraft.screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return className.contains("jei") || className.contains("emi");
    }

    private static boolean isEditorRenderStack() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("net.bananemdnsa.historystages.client.editor.")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRecipeViewerRenderStack() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName().toLowerCase(java.util.Locale.ROOT);
            if (className.startsWith("mezz.jei.") || className.startsWith("dev.emi.emi.")) {
                return true;
            }
        }
        return false;
    }

    public static void pushSuppressed() {
        suppressionDepth++;
    }

    public static void popSuppressed() {
        suppressionDepth = Math.max(0, suppressionDepth - 1);
    }
}
