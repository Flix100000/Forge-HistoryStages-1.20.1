package net.bananemdnsa.historystages.client.editor.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing helpers for the graph's outline-ring node shapes. Each shape is rendered from a
 * smooth runtime texture (see {@link NodeTextures}): the {@code border} colour fills the
 * full shape, then the {@code fill} colour is drawn slightly smaller, leaving a clean
 * coloured ring of width {@code bw} around a solid interior. Public signatures are kept so
 * call sites in the graph screen and legend are unchanged.
 */
public final class NodeShapes {

    /** Dark interior colour for the small checkmark badge's outer ring. */
    public static final int CHECKMARK_RING_COLOR = 0xFF17171A;

    private NodeShapes() {}

    /**
     * Draws whichever shape {@code shape} names, falling back to {@link #rect} for an unknown
     * one. The single place that maps a style's shape name onto a draw call, so the graph canvas
     * and the editor's style preview cannot end up drawing different things.
     */
    public static void draw(GuiGraphics g, String shape, int cx, int cy, int r,
                            int fill, int border, int bw) {
        switch (shape) {
            case "CIRCLE" -> circle(g, cx, cy, r, fill, border, bw);
            case "DIAMOND" -> diamond(g, cx, cy, r, fill, border, bw);
            case "HEXAGON" -> hexagon(g, cx, cy, r, fill, border, bw);
            case "ROUNDED" -> rounded(g, cx, cy, r, fill, border, bw);
            default -> rect(g, cx, cy, r, fill, border, bw); // RECT, or anything unknown
        }
    }

    /** Small status-tick badge in the node's bottom-right corner. */
    public static void checkmark(GuiGraphics g, int cx, int cy, int r, int badgeColor) {
        int br = Math.max(4, Math.round(r * 0.45f));
        int bx = cx + r - br / 2;
        int by = cy + r - br / 2;
        int ringW = Math.max(1, Math.round(br * 0.22f));
        circle(g, bx, by, br, badgeColor, CHECKMARK_RING_COLOR, ringW);

        // One generated glyph, like every other shape on this canvas. Stroking it at render time
        // meant either a bare corner where the arms meet, or a round cap patching that corner
        // that is wider than the stroke itself — the tick was the only thing here still being
        // drawn by hand instead of blitted, and it looked like it.
        int glyph = Math.max(3, Math.round(br * 1.6f));
        NodeTextures.blit(g, NodeTextures.check(), bx - glyph / 2, by - glyph / 2, glyph, glyph,
                NodeTextures.SIZE, NodeTextures.SIZE, 0xFFFFFFFF);
    }

    public static void circle(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.circle(), cx, cy, r, fill, border, bw);
    }

    public static void diamond(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.diamond(), cx, cy, r, fill, border, bw);
    }

    public static void hexagon(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.hexagon(), cx, cy, r, fill, border, bw);
    }

    public static void rect(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.rect(), cx, cy, r, fill, border, bw);
    }

    /**
     * The corner radius is baked into {@link NodeTextures#rounded()} at a fixed relative value
     * (0.18 of the texture's side) and cannot change per draw call — a texture generated once
     * cannot change shape at runtime. The config's {@code cornerRadius} therefore does NOT scale
     * the corner geometry; only {@code r} (the drawn size of the whole shape) is under caller
     * control here.
     */
    public static void rounded(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.rounded(), cx, cy, r, fill, border, bw);
    }

    private static void ring(GuiGraphics g, ResourceLocation tex, int cx, int cy, int r,
                             int fill, int border, int bw) {
        if (r <= 0) return;
        blit(g, tex, cx, cy, r, border);
        int inner = r - Math.max(1, bw);
        if (inner > 0) blit(g, tex, cx, cy, inner, fill);
    }

    /** Blits {@code tex} centred at (cx,cy) scaled to a {@code rad}-radius box, tinted {@code color}. */
    private static void blit(GuiGraphics g, ResourceLocation tex, int cx, int cy, int rad, int color) {
        int size = rad * 2;
        NodeTextures.blit(g, tex, cx - rad, cy - rad, size, size,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
    }
}
