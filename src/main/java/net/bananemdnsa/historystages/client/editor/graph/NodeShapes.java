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

    private NodeShapes() {}

    public static void circle(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.circle(), cx, cy, r, fill, border, bw);
    }

    public static void diamond(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.diamond(), cx, cy, r, fill, border, bw);
    }

    public static void hexagon(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(g, NodeTextures.hexagon(), cx, cy, r, fill, border, bw);
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
