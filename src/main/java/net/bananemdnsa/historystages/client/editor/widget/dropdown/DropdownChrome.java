package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import com.mojang.math.Axis;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared chrome for the editor's dropdowns, which all draw the same frame, the same caret and
 * the same row highlight but had each open-and-close instantly.
 *
 * <p>The popup is revealed by clipping it to a growing height rather than fading it, because a
 * dropdown is anchored to its button: growing out of the button says where it came from, while
 * a fade would just have it materialise over whatever is behind it.
 */
public final class DropdownChrome {

    /** Depth the popup is lifted to, clearing the widgets it overlaps. */
    private static final int POPUP_Z = 400;

    /**
     * Horizontal room a caret needs beside its label: the 5px glyph plus the clearance on either
     * side. Public so that everything drawing a collapsed dropdown by hand reserves the same
     * space — a control whose caret sits two pixels off is a control that looks like a different
     * control.
     */
    public static final int CARET_WIDTH = 8;

    private DropdownChrome() {
    }

    /**
     * Opens a clipped, depth-lifted layer and draws the popup frame into it. Callers draw their
     * rows afterwards and must call {@link #end} when the value returned is {@code true}.
     *
     * @param t       reveal progress, 0..1
     * @param flipped true when the popup sits above its button, so it must grow upwards
     * @return false when there is nothing to draw and {@link #end} must not be called
     */
    public static boolean begin(GuiGraphics g, int px, int py, int pw, int ph, float t, boolean flipped) {
        float eased = Ease.outCubic(t);
        if (eased < 0.02f) return false;

        int visibleH = Math.max(1, Math.round(ph * eased));
        int clipTop = flipped ? py + ph - visibleH : py;
        int clipBottom = flipped ? py + ph : py + visibleH;

        g.pose().pushPose();
        g.pose().translate(0, 0, POPUP_Z);
        g.enableScissor(px - 1, clipTop - 1, px + pw + 1, clipBottom + 1);

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A1A);
        return true;
    }

    /** Closes the layer opened by {@link #begin}. */
    public static void end(GuiGraphics g) {
        g.disableScissor();
        g.pose().popPose();
    }

    /**
     * The collapsed control: frame, background, current value and caret. Shared so a dropdown
     * and a config row that opens one cannot end up looking like two different controls.
     *
     * @param hover    hover progress, 0..1
     * @param expanded whether the popup is open, which turns the frame gold
     * @param flip     caret rotation, 0 = pointing down
     */
    public static void drawButton(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y,
                                  int w, int h, String label, float hover, boolean expanded,
                                  float flip) {
        drawButton(g, font, x, y, w, h, label, hover, expanded, flip, 0xFFEEEEEE);
    }

    /**
     * As above, with the label colour spelled out. The per-stage style editor draws an inherited
     * row's dropdown dimmed, which is the one case where the value shown is not the row's own.
     */
    public static void drawButton(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y,
                                  int w, int h, String label, float hover, boolean expanded,
                                  float flip, int textColor) {
        int border = expanded ? 0xFFFFCC00 : Fade.mix(0xFF4A4A4A, 0xFF888888, hover);
        int bg = Fade.mix(0xFF0D0D0D, 0xFF252525, hover);
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        // Clipped to the space left of the caret, so a long value cannot run into it.
        String text = font.plainSubstrByWidth(label, Math.max(0, w - 16));
        g.drawString(font, text, x + 5, y + (h - 8) / 2 + 1, textColor, false);

        drawCaret(g, x + w - 7, y + h / 2 - 1, Fade.mix(0xFF999999, 0xFFDDDDDD, hover), flip);
    }

    /**
     * Draws the collapsed-state caret, rotated by {@code flip} (0 = pointing down, 1 = fully
     * turned up). Turning it is what ties the button to the popup: the same control changed
     * state, rather than a separate panel having appeared.
     *
     * @param x left edge of the 5x3 caret glyph
     * @param y top edge of the glyph
     */
    public static void drawCaret(GuiGraphics g, int x, int y, int color, float flip) {
        float cx = x + 2.5f;
        float cy = y + 1.5f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(180.0f * Ease.outCubic(flip)));
        g.pose().translate(-cx, -cy, 0.0f);
        g.fill(x, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.pose().popPose();
    }

    /**
     * Row highlight shared by every dropdown: a translucent wash plus a gold edge that grows in
     * from the left, so the row under the cursor is identifiable without relying on a brightness
     * step that is easy to miss on a dark popup.
     */
    public static void drawRowHighlight(GuiGraphics g, int x, int y, int w, int h, float hover) {
        if (hover < 0.001f) return;
        g.fill(x, y, x + w, y + h, Fade.rgba(0xFFFFFF, 0.15f * hover));
        g.fill(x, y, x + 1, y + h, Fade.rgba(0xFFCC00, hover));
    }
}
