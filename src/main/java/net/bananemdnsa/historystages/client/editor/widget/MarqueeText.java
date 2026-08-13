package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A single-line label that truncates when it does not fit and, once its row has been hovered
 * long enough, scrolls the full text back and forth inside a scissor rect.
 *
 * <p>One instance per list, not per row: only the row under the cursor can ever be scrolling,
 * so the hover timer is a single pair of fields keyed by whichever row claimed it last. Moving
 * the cursor to another row restarts the delay, which is what stops a sweep down the list from
 * setting every label in motion.
 */
public final class MarqueeText {

    private static final String ELLIPSIS = "...";

    /** Row key currently owning the hover timer, or null while nothing is hovered. */
    private String activeKey;
    private long hoverStartMs;

    /**
     * Draws {@code text} at {@code x, y}, clipped to {@code available} pixels.
     *
     * @param key       identifies the row across frames; changing it restarts the hover delay
     * @param clipTop   vertical scissor bounds of the row, already clamped to the list's own
     * @param clipBottom  visible area so a half-scrolled row cannot draw outside it
     * @param animate   false when the player has editor animations switched off: truncate only
     */
    public void draw(GuiGraphics g, Font font, String key, String text, int x, int y,
                     int available, int clipTop, int clipBottom, int color,
                     boolean hovered, boolean animate) {
        if (available <= 0) return;

        int textWidth = font.width(text);
        if (textWidth <= available) {
            g.drawString(font, text, x, y, color, false);
            return;
        }

        if (hovered && animate) {
            if (!key.equals(activeKey)) {
                activeKey = key;
                hoverStartMs = Util.getMillis();
            }
            long elapsed = Util.getMillis() - hoverStartMs;
            if (elapsed > Timing.MARQUEE_DELAY_MS) {
                int span = textWidth - available;
                float travelled = (elapsed - Timing.MARQUEE_DELAY_MS) / 1000.0f * Timing.MARQUEE_SPEED;
                // Ping-pong: one full cycle runs to the far end and back, so the label always
                // returns to its readable starting position instead of wrapping mid-word.
                float cycle = span * 2.0f;
                float pos = travelled % cycle;
                int offset = Math.round(pos <= span ? pos : cycle - pos);
                g.enableScissor(x, clipTop, x + available, clipBottom);
                g.drawString(font, text, x - offset, y, color, false);
                g.disableScissor();
                return;
            }
        } else if (key.equals(activeKey)) {
            activeKey = null;
        }

        g.drawString(font, truncate(font, text, available), x, y, color, false);
    }

    /** The truncated form on its own, for callers that only need the string. */
    public static String truncate(Font font, String text, int available) {
        if (font.width(text) <= available) return text;
        return font.plainSubstrByWidth(text, Math.max(0, available - font.width(ELLIPSIS))) + ELLIPSIS;
    }
}
