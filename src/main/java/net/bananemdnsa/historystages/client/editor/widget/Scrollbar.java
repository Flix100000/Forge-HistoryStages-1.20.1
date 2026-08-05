package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The editor's scrollbar: a draggable thumb that appears only while the content overflows.
 *
 * <p>Every screen in the editor used to draw its own copy of this — same 5px width, same
 * {@code 0x80FFFFFF} thumb brightening to {@code 0xCCFFFFFF} while hovered or held, same
 * minimum thumb height, same mapping from cursor to offset. This is that shape, factored out
 * so the graph's sidebar and detail window do not invent a third look. Retrofitting the older
 * screens onto it is a reasonable follow-up; nothing here forces it.
 *
 * <p>Callers should still reserve {@link #WIDTH} on their right edge even while nothing is
 * drawn there: giving the space back when the bar disappears would reflow the text under it
 * every time the content crosses the threshold.
 *
 * <p>Geometry is cached from the last {@link #render} so the input methods can hit-test
 * without being handed the layout again — the convention {@code GraphLegend} uses, and safe
 * for the same reason: a screen always draws a frame before it can receive the next click.
 */
public final class Scrollbar {

    /** Width of the bar. Callers reserve this much space on their right edge. */
    public static final int WIDTH = 5;

    private static final int MIN_THUMB = 20;
    private static final int THUMB_COLOR = 0x80FFFFFF;
    private static final int THUMB_COLOR_ACTIVE = 0xCCFFFFFF;
    /** Extra pixels either side of the bar that still count as grabbing it. */
    private static final int GRAB_SLOP = 2;

    private boolean dragging;

    private int barX;
    private int trackTop;
    private int trackBottom;
    private float maxScroll;
    private boolean visible;

    /**
     * @param x          left edge of the bar; it occupies {@code x} to {@code x + WIDTH}
     * @param scroll     current offset, from 0 to {@code maxScroll}
     * @param maxScroll  0 or less draws nothing — content that fits needs no bar
     */
    public void render(GuiGraphics g, int x, int top, int bottom,
                       float scroll, float maxScroll, int mouseX, int mouseY) {
        this.barX = x;
        this.trackTop = top;
        this.trackBottom = bottom;
        this.maxScroll = maxScroll;
        this.visible = maxScroll > 0 && bottom > top;
        if (!visible) return;

        int trackH = bottom - top;
        int thumbH = thumbHeight(trackH, maxScroll);
        int thumbY = top + Math.round((trackH - thumbH) * (scroll / maxScroll));

        boolean hovered = mouseX >= x - GRAB_SLOP && mouseX <= x + WIDTH + GRAB_SLOP
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;

        // Thumb only, no track. A channel drawn down the full height reads as a scrollbar that
        // is always there, whether or not it says anything — StageOverviewScreen leaves it out
        // for the same reason. Nothing at all is drawn when the content fits (see above).
        g.fill(x, thumbY, x + WIDTH, thumbY + thumbH,
                dragging || hovered ? THUMB_COLOR_ACTIVE : THUMB_COLOR);
    }

    private static int thumbHeight(int trackH, float maxScroll) {
        return Math.max(MIN_THUMB, Math.round(trackH / (maxScroll + trackH) * trackH));
    }

    /**
     * Grabs the bar when the press lands anywhere on its track — jumping to the pressed spot,
     * which is what the editor's hand-rolled bars already did and what makes a long list
     * reachable in one gesture.
     *
     * @return true when the press was taken; the caller then reads {@link #scrollFor}
     */
    public boolean mouseClicked(double mx, double my) {
        if (!visible) return false;
        if (mx < barX - GRAB_SLOP || mx > barX + WIDTH + GRAB_SLOP) return false;
        if (my < trackTop || my > trackBottom) return false;
        dragging = true;
        return true;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void mouseReleased() {
        dragging = false;
    }

    /** The offset the thumb's centre landing under {@code my} corresponds to. */
    public float scrollFor(double my) {
        int trackH = trackBottom - trackTop;
        int thumbH = thumbHeight(trackH, maxScroll);
        float usable = trackH - thumbH;
        if (usable <= 0) return 0.0f;
        float ratio = (float) ((my - trackTop - thumbH / 2.0) / usable);
        return Math.max(0.0f, Math.min(1.0f, ratio)) * maxScroll;
    }
}
