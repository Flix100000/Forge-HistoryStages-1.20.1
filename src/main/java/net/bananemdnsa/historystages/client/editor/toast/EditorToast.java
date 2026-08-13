package net.bananemdnsa.historystages.client.editor.toast;

import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.toast.DismissibleToast;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
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
public class EditorToast implements Toast, DismissibleToast {

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
    /** Quick fade-out when the toast is clicked away — short on purpose; you want it gone. */
    private static final long DISMISS_FADE_MS = (long) Timing.TOAST_DISMISS_MS;

    private final Component title;
    private final Component message;
    private final Level level;
    private List<FormattedCharSequence> wrappedMessage;
    private int computedHeight = BASE_HEIGHT;
    private long dismissAtMs = -1L;

    public EditorToast(Level level, Component title, Component message) {
        this.level = level;
        this.title = title;
        this.message = message;
    }

    @Override
    public void dismiss() {
        if (dismissAtMs < 0L) {
            dismissAtMs = Util.getMillis();
        }
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        ensureLayout();
        return computedHeight;
    }

    @Override
    public int slotCount() {
        // Occupy a single grid slot regardless of height so up to five toasts stay visible.
        // Overlap with the following toast is prevented by ToastComponentMixin/ToastInstanceMixin,
        // which stack toasts by their real pixel height instead of the 32px slot grid.
        return 1;
    }

    /**
     * Wraps the message and derives the toast height. Idempotent and independent of
     * {@link GuiGraphics}, so it can run before the first render via the shared font.
     */
    private void ensureLayout() {
        if (wrappedMessage != null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        wrappedMessage = font.split(message, WIDTH - 12);
        int lines = Math.max(1, wrappedMessage.size());
        computedHeight = Math.max(BASE_HEIGHT, 8 + 10 + lines * 10 + 4);
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toastComponent, long timeSinceLastVisible) {
        Font font = toastComponent.getMinecraft().font;

        ensureLayout();

        // Clicked away: fade out quickly, then report HIDE so vanilla removes it.
        float fade = 1.0F;
        if (dismissAtMs >= 0L) {
            long elapsed = Util.getMillis() - dismissAtMs;
            if (elapsed >= DISMISS_FADE_MS) {
                return Visibility.HIDE;
            }
            // Eased so the toast thins out quickly and then clears, rather than
            // dimming at a constant rate the eye reads as a stall.
            fade = 1.0F - Ease.outCubic((float) elapsed / (float) DISMISS_FADE_MS);
        }

        int w = WIDTH;
        int h = computedHeight;

        // Background — translucent dark, matches editor chrome
        g.fill(0, 0, w, h, Fade.alpha(0x99151515, fade));

        // Top + side hairlines (StyledButton pattern)
        g.fill(0, 0, w, 1, Fade.alpha(0x20FFFFFF, fade));
        g.fill(0, 0, 1, h, Fade.alpha(0x15FFFFFF, fade));
        g.fill(w - 1, 0, w, h, Fade.alpha(0x15FFFFFF, fade));

        // Bottom accent — level-colored, 2px like StyledButton
        g.fill(0, h - 2, w, h, Fade.alpha(level.accentColor, fade));

        // Title
        g.drawString(font, title, 8, 6, Fade.alpha(level.titleColor, fade), false);

        // Message body (wrapped)
        int y = 18;
        for (FormattedCharSequence line : wrappedMessage) {
            g.drawString(font, line, 8, y, Fade.alpha(0xFFDDDDDD, fade), false);
            y += 10;
        }

        if (dismissAtMs >= 0L) {
            return Visibility.SHOW;
        }
        return (double) timeSinceLastVisible >= (double) DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
