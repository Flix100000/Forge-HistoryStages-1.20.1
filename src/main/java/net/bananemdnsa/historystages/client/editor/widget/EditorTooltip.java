package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor's own hover tooltip: dark panel, grey frame, wrapped text, and a delay before it
 * appears so that dragging the cursor across a list does not trail a box behind it.
 *
 * <p>An instance holds the delay state, which is per screen rather than per row — the timer has
 * to restart when the cursor moves from one element to the next, and that is only visible to
 * whoever is tracking both.
 *
 * <p>Deliberately not {@code GuiGraphics.renderTooltip}: the vanilla tooltip has its own border,
 * gradient and instant appearance, and next to the editor's panels it reads as a different
 * application.
 */
public final class EditorTooltip {

    private static final int MAX_WIDTH = 200;
    private static final int LINE_H = 10;
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    /**
     * Above everything else the editor draws. Higher than the dropdown popups at 400, because a
     * per-option tooltip is drawn while such a popup is open and must not fight it for depth.
     */
    private static final int TOOLTIP_Z = 500;

    private static final int FRAME = 0xFF3D3D3D;
    private static final int BACKGROUND = 0xFF0D0D0D;
    private static final int TEXT = 0xCCCCCC;

    /** Identity of what the cursor is on, so moving to a different element restarts the delay. */
    private String hoveredKey;
    private long hoverStart;

    /**
     * Draws the tooltip once the cursor has rested long enough on the same element.
     *
     * @param key  identity of the hovered element, or null when nothing is hovered
     * @param text the text to show; ignored when {@code key} is null
     */
    public void render(GuiGraphics g, Font font, String key, String text,
                       int mouseX, int mouseY, int screenW, int screenH) {
        if (key == null || text == null || text.isEmpty()) {
            hoveredKey = null;
            return;
        }
        if (!key.equals(hoveredKey)) {
            hoveredKey = key;
            hoverStart = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - hoverStart < Timing.TOOLTIP_DELAY_MS) return;

        draw(g, font, text, mouseX, mouseY, screenW, screenH);
    }

    private static void draw(GuiGraphics g, Font font, String text,
                             int mouseX, int mouseY, int screenW, int screenH) {
        g.pose().pushPose();
        g.pose().translate(0, 0, TOOLTIP_Z);

        List<String> lines = wrap(font, text);
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        w += PAD_X * 2;
        int h = lines.size() * LINE_H + PAD_Y * 2;

        int x = mouseX + 12;
        int y = mouseY - 4;
        // Flip to the other side of the cursor rather than let the box run off the screen.
        if (x + w + 2 > screenW - 4) x = mouseX - w - 4;
        if (y + h + 2 > screenH - 4) y = screenH - h - 6;
        if (x < 4) x = 4;
        if (y < 4) y = 4;

        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, FRAME);
        g.fill(x, y, x + w, y + h, BACKGROUND);

        int ty = y + PAD_Y;
        for (String line : lines) {
            g.drawString(font, line, x + PAD_X, ty, TEXT, false);
            ty += LINE_H;
        }

        g.pose().popPose();
    }

    /** Greedy word wrap at {@link #MAX_WIDTH}. */
    private static List<String> wrap(Font font, String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && font.width(line + " " + word) > MAX_WIDTH) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
