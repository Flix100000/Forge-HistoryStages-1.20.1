package net.bananemdnsa.historystages.client.editor.toast;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Editor-styled notification toast. Matches the gold/dark design language
 * of {@code StyledButton} and the editor chrome: dark translucent background,
 * subtle white borders, level-colored bottom accent and title.
 */
public class EditorToast implements Toast {

    public enum Level {
        SUCCESS(0xFFFFCC00, 0xFFFFCC00),  // editor gold
        ERROR  (0xFFFF5555, 0xFFFF5555),  // soft red
        INFO   (0xFF55AAFF, 0xFF55AAFF);  // soft blue

        public final int titleColor;
        public final int accentColor;

        Level(int titleColor, int accentColor) {
            this.titleColor = titleColor;
            this.accentColor = accentColor;
        }
    }

    private static final int WIDTH = 150;
    private static final int BASE_HEIGHT = 28;
    private static final int DISPLAY_TIME = 2800;

    private final Component title;
    private final Component message;
    private final Level level;
    private List<FormattedCharSequence> wrappedMessage;
    private int computedHeight = BASE_HEIGHT;

    public EditorToast(Level level, Component title, Component message) {
        this.level = level;
        this.title = title;
        this.message = message;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return computedHeight;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toastComponent, long timeSinceLastVisible) {
        Font font = toastComponent.getMinecraft().font;

        if (wrappedMessage == null) {
            wrappedMessage = font.split(message, WIDTH - 12);
            int lines = Math.max(1, wrappedMessage.size());
            computedHeight = Math.max(BASE_HEIGHT, 8 + 10 + lines * 10 + 4);
        }

        int w = WIDTH;
        int h = computedHeight;

        // Background — translucent dark, matches editor chrome
        g.fill(0, 0, w, h, 0x99151515);

        // Top + side hairlines (StyledButton pattern)
        g.fill(0, 0, w, 1, 0x20FFFFFF);
        g.fill(0, 0, 1, h, 0x15FFFFFF);
        g.fill(w - 1, 0, w, h, 0x15FFFFFF);

        // Bottom accent — level-colored, 2px like StyledButton
        g.fill(0, h - 2, w, h, level.accentColor);

        // Title
        g.drawString(font, title, 8, 6, level.titleColor, false);

        // Message body (wrapped)
        int y = 18;
        for (FormattedCharSequence line : wrappedMessage) {
            g.drawString(font, line, 8, y, 0xFFDDDDDD, false);
            y += 10;
        }

        return (double) timeSinceLastVisible >= (double) DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
