package net.bananemdnsa.historystages.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class EditorBlurController {
    private static Integer savedBlurValue;
    private static int depth;

    private EditorBlurController() {
    }

    public static void enter(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (depth == 0) {
            savedBlurValue = minecraft.options.menuBackgroundBlurriness().get();
            minecraft.options.menuBackgroundBlurriness().set(0);
        }
        if (minecraft.gameRenderer != null) {
            minecraft.gameRenderer.processBlurEffect(0.0F);
        }
        depth++;
    }

    public static void exit(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (depth > 0) {
            depth--;
        }
        if (depth == 0 && savedBlurValue != null) {
            minecraft.options.menuBackgroundBlurriness().set(savedBlurValue);
            if (minecraft.gameRenderer != null) {
                minecraft.gameRenderer.processBlurEffect(savedBlurValue.floatValue());
            }
            savedBlurValue = null;
        }
    }

    public static boolean isManagedScreen(Screen screen) {
        return screen instanceof StageOverviewScreen
                || screen instanceof ConfigEditorScreen
                || screen instanceof StageDetailScreen
                || screen instanceof DependencyEditorScreen
                || screen instanceof NbtItemEditScreen;
    }

    public static void enforce(Minecraft minecraft) {
        if (minecraft == null || minecraft.screen == null || !isManagedScreen(minecraft.screen)) {
            return;
        }
        if (minecraft.options != null && minecraft.options.menuBackgroundBlurriness().get() != 0) {
            minecraft.options.menuBackgroundBlurriness().set(0);
        }
        if (minecraft.gameRenderer != null) {
            minecraft.gameRenderer.processBlurEffect(0.0F);
        }
    }
}
