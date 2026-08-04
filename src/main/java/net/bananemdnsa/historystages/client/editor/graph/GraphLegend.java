package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Collapsible legend for {@link StageGraphScreen}: explains the three node states, that
 * individual stages are drawn with a different shape than global ones, and the two dependency
 * edge kinds. Pinned to the bottom-right corner of whatever area it is given ({@link #setBounds}),
 * with its body growing upward from a header that never moves.
 *
 * <p>Behaviour (fixed header, upward-growing body, click-swallowing while open) is salvaged from
 * the old {@code DependencyGraphScreen}'s legend. Every swatch is drawn through
 * {@link StageGraphConfig#styleFor} so it always matches what {@link GraphCanvas} actually
 * renders — a legend that disagrees with the canvas would be worse than no legend at all.
 */
public final class GraphLegend {

    private static final int HEADER_H = 15;
    private static final int ROW_H = 13;
    private static final int SUB_H = 12;
    private static final int PAD = 5;
    /** Gap kept between the legend and the edges of its allowed area. */
    private static final int MARGIN = 4;

    private static final int SWATCH_R = 5;
    private static final int COL1_DX = 13;   // global-shape swatch centre, relative to lx
    private static final int COL2_DX = 27;   // individual-shape swatch centre
    private static final int LABEL_DX = 40;  // node-row label text start
    private static final int EDGE_SAMPLE_DX = 13; // edge-row line sample centre
    private static final int EDGE_LABEL_DX = 26;  // edge-row label text start

    private static final int BORDER_COLOR = 0xFF3D3D3D;
    private static final int BODY_BG = 0xFF1A1A1A;
    private static final int HEADER_BG = 0xFF222222;
    private static final int HEADER_BG_HOVER = 0xFF2E2E2E;
    private static final int ACCENT_COLOR = 0xFFFFCC00;
    private static final int SECTION_TEXT_COLOR = 0xFF888888;
    private static final int ROW_TEXT_COLOR = 0xFFCCCCCC;
    private static final int SEPARATOR_COLOR = 0x30FFFFFF;
    private static final int EDGE_FALLBACK_COLOR = 0x999999;

    /**
     * Stage id used only to resolve the "no per-stage override" base style — not a real stage,
     * so {@code GraphStageData} never has an entry for it and {@link StageGraphConfig#styleFor}
     * always returns the plain {@code graph.toml} configuration.
     */
    private static final String PLACEHOLDER_STAGE_ID = "";

    private boolean expanded;
    /** Eased 0..1 body-reveal progress; only advanced when {@code animations} is on. */
    private final Anim reveal = new Anim();

    // Allowed area (usually the canvas viewport, deliberately excluding the detail panel strip
    // so the legend never fights it for space) the legend anchors itself into, bottom-right.
    private int boundsX, boundsY, boundsWidth, boundsHeight;

    // Cached from the last render(), so mouseClicked can hit-test without needing a Font of its
    // own — mirrors how a screen always renders a frame before it can receive the next click.
    private int lastLx, lastHy, lastCurW, lastBodyH;

    public GraphLegend() {
        this.expanded = GraphConfig.GRAPH.legendOpen.get();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.boundsX = x;
        this.boundsY = y;
        this.boundsWidth = width;
        this.boundsHeight = height;
    }

    // --- Rendering --------------------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (boundsWidth <= 0 || boundsHeight <= 0) return;

        boolean animate = GraphConfig.GRAPH.animations.get();
        float revealT = animate
                ? Ease.outCubic(reveal.ramp(expanded, Timing.REVEAL_MS, Timing.REVEAL_MS))
                : (expanded ? 1f : 0f);

        boolean wide = expanded || revealT > 0.02f;
        int curW = wide ? bodyWidth(font) : headerWidth(font);
        int lx = boundsX + boundsWidth - curW - MARGIN;
        int hy = boundsY + boundsHeight - MARGIN - HEADER_H;
        int bodyH = bodyHeight();

        // Drawn above canvas/sidebar/panel content, outside the canvas's own scissor, so it
        // stays fixed on screen regardless of pan or zoom.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int animH = Math.round(bodyH * revealT);
        if (animH > 1) {
            int fullTop = hy - bodyH;
            int revealTop = hy - animH;
            g.enableScissor(lx - 1, revealTop - 1, lx + curW + 1, hy);
            g.fill(lx - 1, fullTop - 1, lx + curW + 1, hy + HEADER_H + 1, BORDER_COLOR);
            g.fill(lx, fullTop, lx + curW, hy, BODY_BG);
            renderBody(g, font, lx, fullTop, curW);
            g.disableScissor();
        } else {
            g.fill(lx - 1, hy - 1, lx + curW + 1, hy + HEADER_H + 1, BORDER_COLOR);
        }

        boolean hoverHeader = mouseX >= lx && mouseX <= lx + curW && mouseY >= hy && mouseY <= hy + HEADER_H;
        g.fill(lx, hy, lx + curW, hy + HEADER_H, hoverHeader ? HEADER_BG_HOVER : HEADER_BG);
        g.fill(lx, hy + HEADER_H - 1, lx + curW, hy + HEADER_H, ACCENT_COLOR);
        g.drawString(font, (expanded ? "▼ " : "▶ ") + tr("title"), lx + 5, hy + 4, ACCENT_COLOR, false);

        g.pose().popPose();

        lastLx = lx;
        lastHy = hy;
        lastCurW = curW;
        lastBodyH = bodyH;
    }

    private void renderBody(GuiGraphics g, Font font, int lx, int by, int curW) {
        int sx1 = lx + COL1_DX;
        int sx2 = lx + COL2_DX;
        int tx = lx + LABEL_DX;
        int y = by + PAD;

        g.drawString(font, tr("nodes"), lx + 6, y, SECTION_TEXT_COLOR, false);
        y += SUB_H;
        y = stateRow(g, font, sx1, sx2, tx, y, NodeState.UNLOCKED);
        y = stateRow(g, font, sx1, sx2, tx, y, NodeState.REACHABLE);
        y = stateRow(g, font, sx1, sx2, tx, y, NodeState.LOCKED);

        g.fill(lx + 6, y + 1, lx + curW - 6, y + 2, SEPARATOR_COLOR);
        y += 4;
        g.drawString(font, tr("connections"), lx + 6, y, SECTION_TEXT_COLOR, false);
        y += SUB_H;
        int esx = lx + EDGE_SAMPLE_DX;
        int etx = lx + EDGE_LABEL_DX;
        y = edgeRow(g, font, esx, etx, y, false);
        edgeRow(g, font, esx, etx, y, true);
    }

    /**
     * One state row: the global (outer column) and individual (inner column) swatches side by
     * side, both drawn from the same configured colours but different shapes — the visual proof
     * that individual stages are told apart by shape, not colour.
     */
    private int stateRow(GuiGraphics g, Font font, int sx1, int sx2, int tx, int y, NodeState state) {
        int cy = y + 5;
        ResolvedStyle global = StageGraphConfig.styleFor(PLACEHOLDER_STAGE_ID, false, state);
        ResolvedStyle individual = StageGraphConfig.styleFor(PLACEHOLDER_STAGE_ID, true, state);
        drawSwatch(g, sx1, cy, global);
        drawSwatch(g, sx2, cy, individual);
        g.drawString(font, stateLabel(state), tx, y + 1, ROW_TEXT_COLOR, false);
        return y + ROW_H;
    }

    private void drawSwatch(GuiGraphics g, int cx, int cy, ResolvedStyle style) {
        drawShape(g, style.shape(), cx, cy, SWATCH_R, fillColor(style), style.border(),
                Math.max(1, style.borderWidth()));
    }

    /**
     * Same shape switch as {@code GraphCanvas.drawShape} — duplicated rather than shared because
     * that method is private and this task must not modify {@code GraphCanvas}.
     */
    private static void drawShape(GuiGraphics g, String shape, int cx, int cy, int r,
                                   int fill, int border, int bw) {
        switch (shape) {
            case "CIRCLE" -> NodeShapes.circle(g, cx, cy, r, fill, border, bw);
            case "DIAMOND" -> NodeShapes.diamond(g, cx, cy, r, fill, border, bw);
            case "HEXAGON" -> NodeShapes.hexagon(g, cx, cy, r, fill, border, bw);
            case "ROUNDED" -> NodeShapes.rounded(g, cx, cy, r, fill, border, bw);
            default -> NodeShapes.rect(g, cx, cy, r, fill, border, bw); // RECT, or anything unknown
        }
    }

    /** Mirrors {@code GraphCanvas.fillColor}: opacity applied, never fully transparent (see there). */
    private static int fillColor(ResolvedStyle style) {
        double opacity = Math.max(0.0, Math.min(1.0, style.fillOpacity()));
        int a = Math.max(1, Math.round(0xFF * (float) opacity));
        return (a << 24) | style.fill();
    }

    /**
     * One edge-kind row. Style and colour both come from {@code graph.toml}'s edge settings —
     * the "met" look is used for both rows since an edge kind has no state of its own; only
     * whether it belongs to an OR group changes which style value applies (see
     * {@code GraphCanvas.drawEdge}).
     */
    private int edgeRow(GuiGraphics g, Font font, int sx, int tx, int y, boolean orGroup) {
        GraphConfig.Graph cfg = GraphConfig.GRAPH;
        GraphConfig.EdgeStyle styleEnum = orGroup ? cfg.orGroupStyle.get() : cfg.edgeStyleMet.get();
        int color = 0xFF000000 | ResolvedStyle.parseColor(cfg.edgeColorMet.get(), EDGE_FALLBACK_COLOR);

        if (styleEnum == GraphConfig.EdgeStyle.DASHED) {
            for (int dx = -6; dx < 7; dx += 4) {
                g.fill(sx + dx, y + 5, sx + dx + 2, y + 7, color);
            }
        } else {
            g.fill(sx - 6, y + 5, sx + 7, y + 7, color);
        }
        g.drawString(font, tr(orGroup ? "or" : "and"), tx, y + 1, ROW_TEXT_COLOR, false);
        return y + ROW_H;
    }

    // --- Geometry -----------------------------------------------------------------------------

    private int headerWidth(Font font) {
        return font.width("▶ " + tr("title")) + 12;
    }

    /**
     * Widest of the header, the node-row content and the edge-row content — computed from the
     * actual translated strings so a longer language (German runs noticeably longer than
     * English here) never gets clipped.
     */
    private int bodyWidth(Font font) {
        int nodeLabelW = Math.max(font.width(stateLabel(NodeState.UNLOCKED)),
                Math.max(font.width(stateLabel(NodeState.REACHABLE)), font.width(stateLabel(NodeState.LOCKED))));
        int edgeLabelW = Math.max(font.width(tr("and")), font.width(tr("or")));
        int nodeRowW = LABEL_DX + nodeLabelW + PAD;
        int edgeRowW = EDGE_LABEL_DX + edgeLabelW + PAD;
        return Math.max(headerWidth(font), Math.max(nodeRowW, edgeRowW));
    }

    private int bodyHeight() {
        // 3 node rows + 2 edge rows, two section headers, a separator gap, padding top/bottom.
        return PAD * 2 + SUB_H * 2 + ROW_H * 5 + 4;
    }

    // --- Input ----------------------------------------------------------------------------------

    /**
     * Toggles on a header click, and otherwise swallows any click landing on the open body so
     * nodes behind the legend are never selected through it.
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || boundsWidth <= 0 || boundsHeight <= 0) return false;

        boolean overHeader = mx >= lastLx && mx <= lastLx + lastCurW
                && my >= lastHy && my <= lastHy + HEADER_H;
        if (overHeader) {
            expanded = !expanded;
            return true;
        }

        if (mx < lastLx || mx > lastLx + lastCurW) return false;
        int top = expanded ? lastHy - lastBodyH : lastHy;
        return my >= top && my <= lastHy + HEADER_H;
    }

    // --- Text -----------------------------------------------------------------------------------

    private static String tr(String key) {
        return Component.translatable("editor.historystages.graph.legend." + key).getString();
    }

    private static String stateLabel(NodeState state) {
        String key = switch (state) {
            case UNLOCKED -> "editor.historystages.graph.state.unlocked";
            case REACHABLE -> "editor.historystages.graph.state.reachable";
            case LOCKED -> "editor.historystages.graph.state.locked";
        };
        return Component.translatable(key).getString();
    }
}
