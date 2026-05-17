package net.bananemdnsa.historystages.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class EditorBlurController {
    private EditorBlurController() {
    }

    public static void enter(Minecraft minecraft) {
        if (minecraft != null && minecraft.gameRenderer != null) {
            minecraft.gameRenderer.shutdownEffect();
        }
    }

    public static void exit(Minecraft minecraft) {
    }

    public static boolean isManagedScreen(Screen screen) {
        return screen != null
                && screen.getClass().getName().startsWith("net.bananemdnsa.historystages.client.editor.");
    }

    public static void enforce(Minecraft minecraft) {
    }
}
