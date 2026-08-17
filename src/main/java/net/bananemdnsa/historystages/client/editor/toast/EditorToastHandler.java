package net.bananemdnsa.historystages.client.editor.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class EditorToastHandler {

    private EditorToastHandler() {}

    public static void show(EditorToast.Level level, Component title, Component message) {
        show(level, title, message, null);
    }

    /** {@code face} may be null; when set, that player's head is drawn on the toast. */
    public static void show(EditorToast.Level level, Component title, Component message, UUID face) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getToasts().addToast(new EditorToast(level, title, message, face)));
    }

    public static void copiedToClipboard(String value) {
        show(EditorToast.Level.INFO,
                Component.translatable("editor.historystages.toast.copied.title"),
                Component.translatable("editor.historystages.toast.copied.message", value));
    }
}
