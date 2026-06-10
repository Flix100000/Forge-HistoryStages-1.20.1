package net.bananemdnsa.historystages.client.editor.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class EditorToastHandler {

    private EditorToastHandler() {}

    public static void show(EditorToast.Level level, Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getToasts().addToast(new EditorToast(level, title, message)));
    }

    public static void copiedToClipboard(String value) {
        show(EditorToast.Level.INFO,
                Component.translatable("editor.historystages.toast.copied.title"),
                Component.translatable("editor.historystages.toast.copied.message", value));
    }
}
