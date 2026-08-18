package net.bananemdnsa.historystages.client.editor.graph;

import com.mojang.math.Axis;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stage graph's drawing surface: background, edges, nodes and labels, plus pan/zoom and hit
 * testing. Owns no application state beyond the camera and the currently focused node — the
 * sidebar, the detail panel and everything they trigger belong to the screen that composes this
 * canvas, not to the canvas itself.
 *
 * <p>The grid-cell -> screen-pixel transform ({@link #screenX}/{@link #screenY}, and their
 * inverses {@link #gridX}/{@link #gridY}) is defined exactly once here and used by every drawing
 * and hit-testing method below. Nothing in this class re-derives it inline — that was the bug in
 * the screen this replaces, where the same formula was copied at four separate sites and drifted.
 */
public final class GraphCanvas {

    /** Resolved icon items, keyed by the raw id from the stage definition. */
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    /** See {@link #setClipRight}. Unbounded until the screen says otherwise. */
    private int clipRight = Integer.MAX_VALUE;

    /**
     * Largest {@code size} multiplier {@code graph.toml} accepts on a style block. Used only to
     * bound culling — if that range is ever widened, widen this with it or nodes at the very
     * edge of the window will start popping.
     */
    private static final float MAX_STYLE_SIZE = 4.0f;

    /** Extra room below a node for its label when deciding whether it is off screen. */
    private static final int LABEL_CULL_PAD = 24;

    /** Thinnest a line is ever drawn. Sub-pixel is fine now that the quad uses float corners. */
    private static final float MIN_LINE_H = 1.0f;

    /** Grid line colour — one pixel wide, faint on purpose. */
    private static final int GRID_COLOR = 0x1FFFFFFF;

    /**
     * Smallest gap between drawn grid lines, in screen pixels. Once the cell size falls below
     * this the grid draws every second cell instead, then every fourth, and so on.
     */
    private static final int MIN_GRID_SPACING = 26;

    /** Stage node radius in screen pixels at zoom 1.0, before the style's {@code size} multiplier. */
    private static final int BASE_NODE_RADIUS = 15;
    /** Below this zoom, node captions are hidden — matches the legacy graph screen's threshold. */
    private static final float LABEL_HIDE_ZOOM = 0.55f;
    /** Extra pixels added to a node's drawn radius when hit-testing, so an edge click still counts. */
    private static final int HIT_PADDING = 2;
    /** Scroll wheel notches per 1.0 of zoom. */
    private static final float ZOOM_STEP = 0.1f;
    /** Peak scale-up applied to the hovered node, eased in via {@link Ease#outCubic}. */
    private static final float HOVER_LIFT = 0.18f;
    /** Number of straight sub-segments a CURVED edge is sampled into. */
    private static final int BEZIER_SEGMENTS = 16;
    /** Accent used for the focused-node highlight ring, matching the editor's gold accent. */
    private static final int FOCUS_RING_COLOR = 0xFFFFCC00;

    /** Screen pixels a press must travel before it counts as a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 4;
    /** Width of the fixed "unplaced" strip at the canvas's own left edge. Editor mode only. */
    private static final int UNPLACED_W = 100;
    private static final int UNPLACED_HEADER_H = 16;
    private static final int UNPLACED_ROW_H = 18;
    private static final int UNPLACED_BG = 0xF0141416;
    private static final int UNPLACED_EDGE = 0x33FFFFFF;
    private static final int UNPLACED_HEADER_COLOR = 0xFF999999;
    private static final int UNPLACED_ROW_COLOR = 0xFFDDDDDD;
    private static final int UNPLACED_ROW_HOVER_COLOR = 0xFFFFCC00;
    private static final int DROP_TARGET_COLOR = 0x80FFCC00;
    private static final int GHOST_BG = 0xE0101010;
    private static final int GHOST_ACCENT = 0xFFFFCC00;

    private StageGraphModel model;
    /** Snapshot of {@code model.nodes()} in insertion (= draw) order, cached for hit testing. */
    private List<Map.Entry<String, StageGraphModel.Node>> drawOrder = List.of();

    // Viewport, in screen pixels — the area this canvas draws into and clips to.
    private int viewX, viewY, viewW, viewH;

    // Camera. panX/panY is the screen position of grid cell (0,0) at the current zoom.
    private float panX;
    private float panY;
    private float zoom;

    private boolean panning;
    /** Graph key of the node the highlight ring is drawn around, set by {@link #focusOn}. */
    private String focusedKey;

    /** Shared hover-lift progress (0..1), applied only to whichever node is under the cursor. */
    private final Anim hoverAnim = new Anim();

    /** Whether authoring (dragging, the unplaced strip) is available at all — editor mode only. */
    private final boolean editor;

    // Node/unplaced drag: armed on press, promoted to a real drag past DRAG_THRESHOLD, resolved
    // (applied or discarded) on release. Mirrors the arm-then-threshold pattern in
    // StageOverviewScreen (armDrag/mouseDragged/mouseReleased) rather than inventing a new one.
    private boolean dragArmed;
    private boolean dragStarted;
    private double pressX, pressY;
    /** Graph key of the node being dragged off the canvas proper, or null while dragging from the unplaced strip. */
    private String draggedKey;
    /** Index into {@code model.unplaced()} being dragged, or -1 when dragging an existing node. */
    private int draggedUnplacedIndex = -1;

    private DragHandler dragHandler;

    /**
     * Reports a completed drag-drop to the host screen, which alone decides whether the target
     * tree needs a freeze confirmation first. The move itself is not applied to the model until
     * {@code commit} runs — declining the confirmation leaves the canvas exactly as it was
     * before the drag, since nothing was ever mutated.
     */
    public interface DragHandler {
        void onDrop(boolean individual, boolean frozen, Map<String, GraphPos> resultingPositions, Runnable commit);
    }

    public void setDragHandler(DragHandler handler) {
        this.dragHandler = handler;
    }

    public GraphCanvas(StageGraphModel model, int x, int y, int width, int height, boolean editor) {
        this.viewX = x;
        this.viewY = y;
        this.viewW = width;
        this.viewH = height;
        this.zoom = (float) (double) GraphConfig.GRAPH.startZoom.get();
        this.panX = x + width / 2f;
        this.panY = y + height / 2f;
        this.editor = editor;
        setModel(model);
    }

    public void setModel(StageGraphModel model) {
        this.model = model;
        this.drawOrder = model == null ? List.of() : new ArrayList<>(model.nodes().entrySet());
        // A fresh model invalidates any in-flight drag reference (index/key from the old one).
        clearDrag();
    }

    private void clearDrag() {
        dragArmed = false;
        dragStarted = false;
        draggedKey = null;
        draggedUnplacedIndex = -1;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.viewX = x;
        this.viewY = y;
        this.viewW = width;
        this.viewH = height;
        this.clipRight = Integer.MAX_VALUE;
    }

    /**
     * Right edge the canvas is allowed to draw up to, independent of its bounds.
     *
     * <p>The detail panel is an overlay: the canvas keeps its full width so opening the panel
     * never shifts the camera. But item icons are rendered by Minecraft on its own, higher z
     * layer, so anything drawn underneath the panel punches straight through it. Clipping the
     * canvas at the panel's edge stops it being drawn there in the first place, which is both
     * correct and cheaper than fighting the z order.
     */
    public void setClipRight(int rightEdge) {
        this.clipRight = rightEdge;
    }

    /** The node currently highlighted (last target of {@link #focusOn}), or null. */
    public String focusedKey() {
        return focusedKey;
    }

    // --- Transform: the single place grid space and screen space meet ------------------------

    /** Grid cell -> screen pixel. */
    public int screenX(int gridX) {
        return (int) Math.floor(panX) + Math.round(gridX * cellSize() * zoom);
    }

    public int screenY(int gridY) {
        return (int) Math.floor(panY) + Math.round(gridY * cellSize() * zoom);
    }

    /** Screen pixel -> grid cell, rounded to the nearest cell. */
    public int gridX(double screenX) {
        return Math.round((float) ((screenX - panX) / (cellSize() * zoom)));
    }

    public int gridY(double screenY) {
        return Math.round((float) ((screenY - panY) / (cellSize() * zoom)));
    }

    private int cellSize() {
        return GraphConfig.GRAPH.gridSize.get();
    }

    // --- Rendering ----------------------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        if (viewW <= 0 || viewH <= 0) return;

        int right = Math.min(viewX + viewW, clipRight);
        if (right <= viewX) return;
        g.enableScissor(viewX, viewY, right, viewY + viewH);
        g.fill(viewX, viewY, right, viewY + viewH, CANVAS_BG);

        // Everything in graph space shares one integer pixel grid (see screenX/screenY) and then
        // gets the camera's sub-pixel remainder applied once, here. Letting each node round its
        // own position instead means they cross pixel boundaries at different moments while
        // panning, so they shift by a pixel against each other — the map appears to shiver
        // rather than slide. One grid plus one shared offset keeps them locked together and the
        // motion smooth.
        g.pose().pushPose();
        g.pose().translate(panX - (float) Math.floor(panX), panY - (float) Math.floor(panY), 0f);

        switch (GraphConfig.GRAPH.background.get()) {
            case GRID -> drawGrid(g);
            case TEXTURE -> drawBackgroundTexture(g);
            case SOLID -> { /* the flat fill drawn above is the whole background */ }
        }

        if (model != null) {
            String hoverKey = within(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
            float hover = animationsEnabled()
                    ? hoverAnim.ramp(hoverKey != null, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)
                    : (hoverKey != null ? 1f : 0f);

            // Edges first so their lines pass behind the nodes.
            for (StageGraphModel.Edge edge : model.edges()) {
                if (edgeOffScreen(edge)) continue;
                drawEdge(g, edge);
            }
            for (Map.Entry<String, StageGraphModel.Node> e : drawOrder) {
                if (nodeOffScreen(e.getValue())) continue;
                drawNodeShape(g, e.getValue(), e.getKey().equals(hoverKey) ? hover : 0f);
            }
            if (zoom >= LABEL_HIDE_ZOOM) {
                for (Map.Entry<String, StageGraphModel.Node> e : drawOrder) {
                    if (nodeOffScreen(e.getValue())) continue;
                    drawLabel(g, font, e.getValue());
                }
            }
            if (focusedKey != null) {
                StageGraphModel.Node focused = model.nodes().get(focusedKey);
                if (focused != null) drawFocusHighlight(g, focused);
            }
            if (dragStarted) {
                drawDropTarget(g, mouseX, mouseY);
            }
        }

        g.pose().popPose();
        g.disableScissor();

        // Outside the pan/zoom transform on purpose — a fixed strip, not part of the graph.
        if (editor && model != null && !model.unplaced().isEmpty()) {
            renderUnplacedColumn(g, font, mouseX, mouseY);
        }
        if (dragStarted) {
            drawDragGhost(g, font, mouseX, mouseY);
        }
    }

    private static boolean animationsEnabled() {
        return GraphConfig.GRAPH.animations.get();
    }

    /**
     * Canvas backdrop, drawn before the sub-pixel camera offset is applied — it fills the
     * viewport exactly and must not slide with the graph.
     *
     * <p>SOLID is this fill on its own. TEXTURE falls back to it as well: {@code graph.toml}
     * names the mode but has no resource to point at yet, and guessing a path would only produce
     * a missing-texture chequerboard.
     */
    private static final int CANVAS_BG = 0xFF17171A;

    /**
     * Tiles {@code canvas.backgroundTexture} across the viewport, panning and zooming with the
     * graph exactly as the grid does — a background that stayed still while the map moved would
     * read as a window rather than as ground.
     *
     * <p>One quad with a repeating UV range, not one blit per tile. {@code GuiGraphics.blit} is
     * not batched: every call is its own draw, so a screenful of tiles cost hundreds of draws a
     * frame and made the whole graph, sidebar included, feel sluggish. This is a single draw at
     * any zoom, with no loop to bound.
     *
     * <p>One tile covers one grid cell, so the texture lines up with the grid instead of drifting
     * against it. Anything unusable — blank, unparseable, or a texture the pack does not ship —
     * leaves the plain fill that is already on screen; a missing-texture chequerboard behind the
     * whole graph would be far worse than a colour.
     */
    private void drawBackgroundTexture(GuiGraphics g) {
        ResourceLocation tex = ResourceLocation.tryParse(
                GraphConfig.GRAPH.backgroundTexture.get().trim());
        if (tex == null) return;

        float tile = cellSize() * zoom;
        if (tile < 1.0f) return; // below a pixel per tile there is nothing left to see

        // Floored like screenX/screenY: the fractional part of the pan is supplied by the pose
        // this runs inside, and counting it twice would make the texture crawl under the grid.
        float originX = (int) Math.floor(panX);
        float originY = (int) Math.floor(panY);

        float u1 = (viewX - originX) / tile;
        float v1 = (viewY - originY) / tile;
        NodeTextures.repeatingQuad(g, tex, viewX, viewY, viewX + viewW, viewY + viewH,
                u1, v1, u1 + viewW / tile, v1 + viewH / tile);
    }

    private void drawGrid(GuiGraphics g) {
        double cellSpacing = (double) cellSize() * zoom;
        if (cellSpacing <= 0.0) return;

        // Coarsen instead of shrink. Every line is one pixel wide, so zooming out does not make
        // them thicker — it packs them closer, and below roughly a finger's width apart the grid
        // stops reading as a reference and starts reading as a flat texture over the whole
        // canvas. Doubling the step keeps the drawn spacing inside a comfortable band at every
        // zoom, so the grid stays a grid rather than fading out or turning into a mesh.
        int step = 1;
        while (cellSpacing * step < MIN_GRID_SPACING) step *= 2;
        double spacing = cellSpacing * step;

        int gridColor = GRID_COLOR;

        // Both loops are bounded by a line count computed up front, and step in double rather
        // than float. The earlier version was an unbounded for(;;) that exited only once a
        // float sum exceeded the viewport — and a float at the magnitude pan reaches after a
        // few zoom-toward-cursor steps has a spacing larger than the increment, so the sum
        // stops advancing and the loop never ends. That hung the render thread.
        int maxLines = (int) Math.ceil(Math.max(viewW, viewH) / spacing) + 3;

        // Lines are placed with screenX/screenY — the same transform the nodes use — so a grid
        // line always sits exactly on the cell a node occupies. Computing them independently
        // meant a second rounding of the same value, and the two drifted against each other by
        // up to a pixel while panning, which reads as the grid crawling under the graph.
        // Snapped down to a multiple of the step so the drawn lines stay on the same cells as the
        // zoom changes — otherwise which lines survive the coarsening shifts while panning.
        int firstCol = Math.floorDiv(gridX(viewX) - step, step) * step;
        for (int i = 0; i <= maxLines; i++) {
            int x = screenX(firstCol + i * step);
            if (x > viewX + viewW) break;
            if (x >= viewX) g.fill(x, viewY, x + 1, viewY + viewH, gridColor);
        }

        int firstRow = Math.floorDiv(gridY(viewY) - step, step) * step;
        for (int i = 0; i <= maxLines; i++) {
            int y = screenY(firstRow + i * step);
            if (y > viewY + viewH) break;
            if (y >= viewY) g.fill(viewX, y, viewX + viewW, y + 1, gridColor);
        }
    }

    // --- Nodes ----------------------------------------------------------------------------------

    private int nodeRadius(ResolvedStyle style) {
        return Math.max(3, Math.round(BASE_NODE_RADIUS * (float) style.size() * zoom));
    }

    private void drawNodeShape(GuiGraphics g, StageGraphModel.Node node, float hoverT) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        int cx = screenX(node.pos().x());
        int cy = screenY(node.pos().y());
        int r = nodeRadius(style);

        float scale = 1f + HOVER_LIFT * Ease.outCubic(hoverT);
        boolean scaled = Math.abs(scale - 1f) > 1.0e-3f;
        if (scaled) {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-cx, -cy, 0);
        }

        int bw = Math.max(0, Math.round(style.borderWidth() * zoom));
        NodeShapes.draw(g, style.shape(), cx, cy, r, style.fillArgb(), style.border(), bw);
        drawNodeIcon(g, node, cx, cy, r);
        if (style.checkmark()) {
            drawCheckmark(g, cx, cy, r, style.border());
        }

        if (scaled) g.pose().popPose();
    }

    /**
     * The stage's {@code icon} item, drawn inside the node.
     *
     * <p>Sized to 1.25× the node radius, which still fits inside a diamond — its inscribed square
     * has a side of about 1.41× the radius, and the diamond is the tightest of the five shapes.
     * Below a few pixels the sprite is a smudge rather than a symbol, so it is dropped instead.
     */
    private void drawNodeIcon(GuiGraphics g, StageGraphModel.Node node, int cx, int cy, int r) {
        if (node.icon() == null || node.icon().isEmpty()) return;
        ItemStack stack = iconStack(node.icon());
        if (stack.isEmpty()) return;

        float side = r * 1.25f;
        if (side < 6f) return;

        g.pose().pushPose();
        g.pose().translate(cx - side / 2f, cy - side / 2f, 0);
        g.pose().scale(side / 16f, side / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Cached: this runs once per visible node per frame, and a registry lookup is not free. */
    private ItemStack iconStack(String id) {
        return iconCache.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            // An unknown id resolves to AIR rather than null, so both cases land here.
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    /** Small status-tick badge in the node's bottom-right corner. */
    private void drawCheckmark(GuiGraphics g, int cx, int cy, int r, int badgeColor) {
        NodeShapes.checkmark(g, cx, cy, r, badgeColor);
    }

    private void drawLabel(GuiGraphics g, Font font, StageGraphModel.Node node) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        if ("NONE".equals(style.label())) return;
        String text = "ID".equals(style.label()) ? node.stageId() : node.label();
        if (text == null || text.isEmpty()) return;

        int cx = screenX(node.pos().x());
        int topY = screenY(node.pos().y()) + nodeRadius(style) + 2;
        g.pose().pushPose();
        g.pose().translate(cx, topY, 0);
        g.pose().scale(zoom, zoom, 1f);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2, 0, style.labelColor(), false);
        g.pose().popPose();
    }

    private void drawFocusHighlight(GuiGraphics g, StageGraphModel.Node node) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        int cx = screenX(node.pos().x());
        int cy = screenY(node.pos().y());
        int r = nodeRadius(style);
        int ringW = Math.max(2, Math.round(3f * zoom));
        NodeShapes.draw(g, style.shape(), cx, cy, r + ringW, FOCUS_RING_COLOR, FOCUS_RING_COLOR, ringW);
    }

    // --- Drag authoring (editor mode only) ---------------------------------------------------

    /** Crosshair over the grid cell a drag would land on if released right now. */
    private void drawDropTarget(GuiGraphics g, int mouseX, int mouseY) {
        int cx = screenX(gridX(mouseX));
        int cy = screenY(gridY(mouseY));
        int r = Math.max(6, Math.round(BASE_NODE_RADIUS * zoom));
        g.fill(cx - r, cy - 1, cx + r, cy + 1, DROP_TARGET_COLOR);
        g.fill(cx - 1, cy - r, cx + 1, cy + r, DROP_TARGET_COLOR);
    }

    /** Small label following the cursor, naming whatever is currently being dragged. */
    private void drawDragGhost(GuiGraphics g, Font font, int mouseX, int mouseY) {
        String label = dragGhostLabel();
        if (label == null || label.isEmpty()) return;

        int w = font.width(label) + 8;
        int gx = mouseX + 10;
        int gy = mouseY + 10;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // above nodes/edges, below modal dialogs
        g.fill(gx, gy, gx + w, gy + 14, GHOST_BG);
        g.fill(gx, gy + 13, gx + w, gy + 14, GHOST_ACCENT);
        g.drawString(font, label, gx + 4, gy + 3, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    private String dragGhostLabel() {
        if (model == null) return null;
        if (draggedUnplacedIndex >= 0) {
            List<StageGraphModel.Node> unplaced = model.unplaced();
            if (draggedUnplacedIndex >= unplaced.size()) return null;
            return labelFor(unplaced.get(draggedUnplacedIndex));
        }
        if (draggedKey != null) {
            StageGraphModel.Node node = model.nodes().get(draggedKey);
            return node == null ? null : labelFor(node);
        }
        return null;
    }

    private static String labelFor(StageGraphModel.Node node) {
        return node.label() == null || node.label().isEmpty() ? node.stageId() : node.label();
    }

    /**
     * Fixed strip at the canvas's own left edge listing {@code model.unplaced()} — stages a
     * frozen tree has no stored position for. Only ever non-empty for a frozen tree in editor
     * view (see {@link StageGraphModel#unplaced()}), so an unfrozen tree draws nothing here.
     */
    private void renderUnplacedColumn(GuiGraphics g, Font font, int mouseX, int mouseY) {
        List<StageGraphModel.Node> unplaced = model.unplaced();
        int stripH = Math.min(viewH, UNPLACED_HEADER_H + unplaced.size() * UNPLACED_ROW_H + 4);

        g.enableScissor(viewX, viewY, viewX + UNPLACED_W, viewY + viewH);
        g.fill(viewX, viewY, viewX + UNPLACED_W, viewY + stripH, UNPLACED_BG);
        g.fill(viewX, viewY, viewX + UNPLACED_W, viewY + 1, UNPLACED_EDGE);
        g.fill(viewX + UNPLACED_W - 1, viewY, viewX + UNPLACED_W, viewY + stripH, UNPLACED_EDGE);
        g.fill(viewX, viewY + stripH - 1, viewX + UNPLACED_W, viewY + stripH, UNPLACED_EDGE);

        g.drawString(font, Component.translatable("editor.historystages.graph.unplaced").getString(),
                viewX + 4, viewY + 4, UNPLACED_HEADER_COLOR, false);

        for (int i = 0; i < unplaced.size(); i++) {
            // The row being dragged away is drawn as a ghost near the cursor instead.
            if (dragStarted && draggedUnplacedIndex == i) continue;

            int rowY = viewY + UNPLACED_HEADER_H + i * UNPLACED_ROW_H;
            boolean hovered = mouseX >= viewX && mouseX < viewX + UNPLACED_W
                    && mouseY >= rowY && mouseY < rowY + UNPLACED_ROW_H;
            String label = font.plainSubstrByWidth(labelFor(unplaced.get(i)), UNPLACED_W - 8);
            g.drawString(font, label, viewX + 4, rowY + 4,
                    hovered ? UNPLACED_ROW_HOVER_COLOR : UNPLACED_ROW_COLOR, false);
        }
        g.disableScissor();
    }

    /** Index into {@code model.unplaced()} at the given point, or -1 outside the strip. */
    private int unplacedIndexAt(double mx, double my) {
        if (model == null || model.unplaced().isEmpty()) return -1;
        if (mx < viewX || mx >= viewX + UNPLACED_W || my < viewY || my >= viewY + viewH) return -1;

        int idx = (int) ((my - viewY - UNPLACED_HEADER_H) / UNPLACED_ROW_H);
        if (idx < 0 || idx >= model.unplaced().size()) return -1;
        int rowY = viewY + UNPLACED_HEADER_H + idx * UNPLACED_ROW_H;
        return (my >= rowY && my < rowY + UNPLACED_ROW_H) ? idx : -1;
    }

    // --- Edges ----------------------------------------------------------------------------------

    /** A drawable path in screen space: {@code xs[i], ys[i]} for i in [0, xs.length). */
    private record Path(float[] xs, float[] ys) {}

    private record Point(float x, float y) {}

    private void drawEdge(GuiGraphics g, StageGraphModel.Edge edge) {
        StageGraphModel.Node from = model.nodes().get(edge.fromKey());
        StageGraphModel.Node to = model.nodes().get(edge.toKey());
        if (from == null || to == null) return; // StageGraphModel guarantees both exist; defensive only

        ResolvedStyle fromStyle = StageGraphConfig.styleFor(from.stageId(), from.individual(), from.state());
        ResolvedStyle toStyle = StageGraphConfig.styleFor(to.stageId(), to.individual(), to.state());

        float fx = screenX(from.pos().x());
        float fy = screenY(from.pos().y());
        float tx = screenX(to.pos().x());
        float ty = screenY(to.pos().y());

        GraphConfig.Graph cfg = GraphConfig.GRAPH;
        Path path = buildPath(fx, fy, tx, ty, cfg.edgeRouting.get());
        float[] xs = path.xs();
        float[] ys = path.ys();
        int last = xs.length - 1;

        // Inset the endpoints to the node boundary so the line (and its arrowhead) don't start
        // or end underneath the node shape.
        Point p0 = insetPoint(xs[0], ys[0], xs[1], ys[1], nodeRadius(fromStyle));
        Point pn = insetPoint(xs[last], ys[last], xs[last - 1], ys[last - 1], nodeRadius(toStyle));
        xs[0] = p0.x();
        ys[0] = p0.y();
        xs[last] = pn.x();
        ys[last] = pn.y();

        // An OR-group edge always uses orGroupStyle, regardless of state, to read as "one of
        // several alternatives" — the met/open colour still communicates its actual state.
        GraphConfig.EdgeStyle styleEnum = edge.orGroup() ? cfg.orGroupStyle.get()
                : (edge.satisfied() ? cfg.edgeStyleMet.get() : cfg.edgeStyleOpen.get());
        int color = 0xFF000000 | ResolvedStyle.parseColor(
                edge.satisfied() ? cfg.edgeColorMet.get() : cfg.edgeColorOpen.get(), 0x999999);
        // Floor of one pixel, not 1.5 — anything higher stops the line shrinking with the rest of
        // the canvas well before the minimum zoom is reached.
        float thickness = Math.max(1.0f, cfg.edgeWidth.get() * zoom);

        // Two independent axes: the line style says AND vs OR, the colour says satisfied vs open.
        // With the stock config that is unambiguous — AND is solid either way, so dashed means OR.
        //
        // A pack author can still set styleOpen to DASHED, at which point an open AND edge and an
        // OR edge would look the same. Rather than let them collide, OR falls back to a distinctly
        // finer dotted pattern in that case only; at default settings it gets the ordinary dash,
        // which is far easier to read than dots.
        boolean openAndAlsoDashed = cfg.edgeStyleOpen.get() == GraphConfig.EdgeStyle.DASHED;
        boolean needsFinerPattern = edge.orGroup() && openAndAlsoDashed;

        float dashLen = 0f;
        float gapLen = 0f;
        if (styleEnum == GraphConfig.EdgeStyle.DASHED) {
            if (needsFinerPattern) {
                dashLen = Math.max(2f, 2.5f * zoom);
                gapLen = Math.max(2f, 3.5f * zoom);
            } else {
                dashLen = Math.max(3f, 7f * zoom);
                gapLen = Math.max(2f, 5f * zoom);
            }
        }

        drawPath(g, xs, ys, color, thickness, dashLen, gapLen);
        if (cfg.edgeArrowheads.get()) {
            drawArrowHead(g, xs[last], ys[last], xs[last - 1], ys[last - 1], thickness, color);
        }
    }

    private Path buildPath(float fx, float fy, float tx, float ty, GraphConfig.EdgeRouting routing) {
        return switch (routing) {
            // Two horizontal legs joined by one vertical leg at the x-midpoint — the standard
            // "elbow" connector look, and what lets the path reach an arbitrary (tx,ty) while
            // only ever running horizontal or vertical.
            case ORTHOGONAL -> {
                float midX = (fx + tx) / 2f;
                yield new Path(new float[]{fx, midX, midX, tx}, new float[]{fy, fy, ty, ty});
            }
            case CURVED -> buildBezier(fx, fy, tx, ty);
            default -> new Path(new float[]{fx, tx}, new float[]{fy, ty});
        };
    }

    /** Cubic Bezier with control points offset horizontally by half the source-target span. */
    private Path buildBezier(float fx, float fy, float tx, float ty) {
        float span = (tx - fx) / 2f;
        float c1x = fx + span, c1y = fy;
        float c2x = tx - span, c2y = ty;
        float[] xs = new float[BEZIER_SEGMENTS + 1];
        float[] ys = new float[BEZIER_SEGMENTS + 1];
        for (int i = 0; i <= BEZIER_SEGMENTS; i++) {
            float t = (float) i / BEZIER_SEGMENTS;
            xs[i] = cubic(fx, c1x, c2x, tx, t);
            ys[i] = cubic(fy, c1y, c2y, ty, t);
        }
        return new Path(xs, ys);
    }

    private static float cubic(float p0, float p1, float p2, float p3, float t) {
        float u = 1f - t;
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3;
    }

    private static Point insetPoint(float fromX, float fromY, float towardX, float towardY, float radius) {
        float dx = towardX - fromX, dy = towardY - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return new Point(fromX, fromY);
        return new Point(fromX + dx / len * radius, fromY + dy / len * radius);
    }

    /**
     * Draws every segment of a multi-point path, solid or dashed.
     *
     * <p>Works in line <em>height</em> rather than radius, so a single-pixel hairline is
     * expressible. Going through a radius forced a minimum height of two pixels, and since
     * everything else on the canvas keeps shrinking with the zoom, that floor is what made the
     * lines look heavier the further out you went — they were the only thing that stopped
     * getting smaller.
     */
    private void drawPath(GuiGraphics g, float[] xs, float[] ys, int color, float thickness,
                          float dashLen, float gapLen) {
        // Thickness stays a float all the way to the vertex buffer. Rounding it to whole pixels is
        // what forced a two-pixel floor before: an integer-sized rotated rectangle either snaps up
        // to two pixels or, at one, misses pixel centres along a diagonal and breaks into specks.
        // A quad with sub-pixel corners and a filtered texture just gets fainter as it thins, the
        // way FTB Quests' dependency lines do.
        float h = Math.max(MIN_LINE_H, thickness);
        if (dashLen > 0f) {
            drawDashedPolyline(g, xs, ys, color, h, dashLen, gapLen);
            return;
        }
        // Joins only once the line is thick enough for the notch at a bend to be worth patching.
        // A curve is sampled into many short segments, so each bend turns by a couple of degrees
        // and the notch is well under a pixel on a thin line — while the dot covering it is a
        // circle texture squeezed into a few pixels, more conspicuous than the seam.
        int joinRadius = h >= 3f ? Math.round(h / 2f) : 0;
        for (int i = 0; i < xs.length - 1; i++) {
            drawSegment(g, xs[i], ys[i], xs[i + 1], ys[i + 1], h, color);
            if (i > 0 && joinRadius > 0) blitDot(g, xs[i], ys[i], joinRadius, color);
        }
        if (joinRadius > 0) {
            blitDot(g, xs[0], ys[0], joinRadius, color);
            blitDot(g, xs[xs.length - 1], ys[xs.length - 1], joinRadius, color);
        }
    }

    /**
     * Walks the whole path with a dash phase that carries across segments, so a CURVED edge's
     * many small sub-segments read as one continuous dashed line rather than restarting the
     * pattern at every sample point.
     */
    private void drawDashedPolyline(GuiGraphics g, float[] xs, float[] ys, int color, float h,
                                    float dash, float gap) {
        float cycle = dash + gap;
        float carried = 0f;

        for (int i = 0; i < xs.length - 1; i++) {
            float x1 = xs[i], y1 = ys[i], x2 = xs[i + 1], y2 = ys[i + 1];
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.5f) continue;
            float angle = (float) Math.atan2(dy, dx);

            g.pose().pushPose();
            g.pose().translate(x1, y1, 0);
            g.pose().mulPose(Axis.ZP.rotation(angle));

            // Stepped by dash index, not by an accumulating cursor. The previous version advanced
            // a float `pos` by a computed remainder; that remainder can be arbitrarily small, and
            // once the segment is long enough that the remainder falls below the float spacing at
            // `pos`, `pos` stops advancing and the loop never terminates. Segment length scales
            // directly with zoom, so this was reachable simply by zooming in — and it hit dashed
            // edges, which is every unsatisfied dependency and every OR group.
            float phase = carried % cycle;
            float firstStart = phase < dash ? -phase : cycle - phase;
            int maxDashes = (int) Math.ceil((len + cycle) / cycle) + 1;

            for (int k = 0; k < maxDashes; k++) {
                float start = firstStart + k * cycle;
                if (start > len) break;
                float from = Math.max(0f, start);
                float to = Math.min(len, start + dash);
                if (to > from) blitLine(g, from, to, h, color);
            }

            g.pose().popPose();
            carried += len;
        }
    }

    /** Draws one straight segment as a rotated quad; returns its length. */
    private float drawSegment(GuiGraphics g, float x1, float y1, float x2, float y2,
                              float h, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return 0f;
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        blitLine(g, 0f, len, h, color);
        g.pose().popPose();
        return len;
    }

    /**
     * Draws a shaft from local x = {@code s} to x = {@code e}, {@code h} pixels tall and centred
     * on the local x axis. Called inside a rotated pose, so this is a rotated quad.
     *
     * <p>Everything here is a float, right through to the vertex buffer. Both {@code fill} and
     * {@code blit} take integers, which rounds the thickness to whole pixels <em>before</em> the
     * pose rotates it — a 1.4 pixel line at an angle simply cannot be expressed, so it either
     * snaps to two pixels or falls apart. The line texture carries the soft border rows that make
     * the edge fade instead of stair-stepping, and it is filtered linearly for the same reason.
     */
    private void blitLine(GuiGraphics g, float s, float e, float h, int color) {
        if (e <= s || h <= 0f) return;
        NodeTextures.quad(g, NodeTextures.line(), s, -h / 2f, e, h / 2f, color);
    }

    private void blitDot(GuiGraphics g, float x, float y, int r, int color) {
        NodeTextures.blit(g, NodeTextures.circle(), Math.round(x) - r, Math.round(y) - r, r * 2, r * 2,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
    }

    /** Draws the arrowhead at (tx,ty), pointing from (fromX,fromY) toward it. */
    private void drawArrowHead(GuiGraphics g, float tx, float ty, float fromX, float fromY,
                               float thickness, int color) {
        float angle = (float) Math.atan2(ty - fromY, tx - fromX);
        // Tied to the line it terminates. The old floor of four pixels left an eight-pixel head on
        // a one-pixel line at low zoom, next to nodes barely ten pixels across.
        int ah = Math.max(2, Math.round(2.2f * thickness));
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        NodeTextures.blit(g, NodeTextures.arrow(), -2 * ah, -ah, 2 * ah, 2 * ah,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
        g.pose().popPose();
    }

    // --- Hit testing ------------------------------------------------------------------------

    /**
     * Returns the graph key of the topmost node under (mx,my), or null. Iterates in reverse
     * draw order so a node placed on top of another (hand-placement makes this routine once
     * nodes are dragged) wins the hit rather than whichever happened to be inserted first.
     */
    public String nodeAt(double mx, double my) {
        if (model == null || !within(mx, my)) return null;
        for (int i = drawOrder.size() - 1; i >= 0; i--) {
            Map.Entry<String, StageGraphModel.Node> e = drawOrder.get(i);
            StageGraphModel.Node node = e.getValue();
            ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
            int cx = screenX(node.pos().x());
            int cy = screenY(node.pos().y());
            int r = nodeRadius(style) + HIT_PADDING;
            if (Math.abs(mx - cx) <= r && Math.abs(my - cy) <= r) {
                return e.getKey();
            }
        }
        return null;
    }

    private boolean within(double mx, double my) {
        return mx >= viewX && mx < viewX + viewW && my >= viewY && my < viewY + viewH;
    }

    // --- Culling ----------------------------------------------------------------------------
    //
    // Zooming in puts most of a large pack outside the window, and everything outside it was
    // still being shaped, blitted and text-measured before the scissor threw it away. These two
    // checks are deliberately arithmetic only — no style lookup, no allocation — because a cull
    // test that costs as much as the draw it skips is not worth having.

    /**
     * Half-extent generous enough to cover the largest a node can possibly draw: the biggest
     * {@code size} the config permits, at full hover lift, plus room for the label underneath.
     * Erring large only means drawing something just off screen; erring small pops nodes out of
     * existence at the edge, which is far more noticeable.
     */
    private float cullMargin() {
        return BASE_NODE_RADIUS * MAX_STYLE_SIZE * zoom * (1f + HOVER_LIFT) + LABEL_CULL_PAD;
    }

    private boolean nodeOffScreen(StageGraphModel.Node node) {
        float m = cullMargin();
        int x = screenX(node.pos().x());
        int y = screenY(node.pos().y());
        return x + m < viewX || x - m > viewX + viewW
                || y + m < viewY || y - m > viewY + viewH;
    }

    private boolean edgeOffScreen(StageGraphModel.Edge edge) {
        StageGraphModel.Node a = model.nodes().get(edge.fromKey());
        StageGraphModel.Node b = model.nodes().get(edge.toKey());
        if (a == null || b == null) return true;

        int x1 = screenX(a.pos().x()), y1 = screenY(a.pos().y());
        int x2 = screenX(b.pos().x()), y2 = screenY(b.pos().y());
        // CURVED routing bows out from the straight line by up to half the horizontal span, so
        // the box cannot simply hug the endpoints or long curves would vanish while still visible.
        float bow = Math.abs(x2 - x1) * 0.5f + cullMargin();

        return Math.max(x1, x2) + bow < viewX || Math.min(x1, x2) - bow > viewX + viewW
                || Math.max(y1, y2) + bow < viewY || Math.min(y1, y2) - bow > viewY + viewH;
    }

    /**
     * Keeps the map within reach of the viewport.
     *
     * <p>Two reasons. The obvious one is that scrolling the graph off into nowhere and having no
     * way back is a bad time. The less obvious one is that it bounds how large {@code panX} and
     * {@code panY} can grow: {@link #drawGrid} steps along the viewport in units of the grid
     * spacing, and once the camera offset reaches a magnitude where that step disappears into
     * rounding, the walk stops advancing. The loop there is bounded now regardless, but keeping
     * the camera near the content removes the condition rather than only surviving it.
     */
    private void clampPan() {
        if (model == null || model.nodes().isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (StageGraphModel.Node node : model.nodes().values()) {
            minX = Math.min(minX, node.pos().x());
            maxX = Math.max(maxX, node.pos().x());
            minY = Math.min(minY, node.pos().y());
            maxY = Math.max(maxY, node.pos().y());
        }

        double cell = (double) cellSize() * zoom;
        // At least this much of the content must stay inside the viewport.
        double margin = Math.max(48.0, Math.min(viewW, viewH) * 0.4);

        panX = (float) clampBetween(panX,
                viewX + margin - maxX * cell, viewX + viewW - margin - minX * cell);
        panY = (float) clampBetween(panY,
                viewY + margin - maxY * cell, viewY + viewH - margin - minY * cell);
    }

    /**
     * Drops the camera's sub-pixel remainder once the movement that produced it has finished.
     *
     * <p>That remainder is what keeps panning smooth, but it is applied as a pose translation, and
     * text rendered at a fractional offset is slightly soft. Nobody is reading labels mid-drag;
     * they read them once the map is standing still. So the fraction exists exactly as long as
     * something is moving, and the view settles on whole pixels.
     */
    private void snapCameraToPixel() {
        panX = Math.round(panX);
        panY = Math.round(panY);
    }

    /** Clamps without assuming which bound is the lower one — at high zoom they can swap. */
    private static double clampBetween(double value, double a, double b) {
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        return value < lo ? lo : Math.min(value, hi);
    }

    // --- Camera control ---------------------------------------------------------------------

    /**
     * Highlights {@code graphKey} without moving the camera.
     *
     * <p>Selecting a node by clicking it must not recentre the view: the node is already under
     * the cursor, and yanking the map out from under a click makes the canvas feel like it is
     * fighting you. Recentring is what {@link #focusOn} is for, and that is the sidebar's job —
     * there the target genuinely may be off screen.
     */
    public void highlight(String graphKey) {
        focusedKey = graphKey;
    }

    /** Centres the viewport on {@code graphKey} at the current zoom, and highlights it. */
    public void focusOn(String graphKey) {
        if (model == null) return;
        StageGraphModel.Node node = model.nodes().get(graphKey);
        if (node == null) return;
        focusedKey = graphKey;
        panX = viewX + viewW / 2f - node.pos().x() * cellSize() * zoom;
        panY = viewY + viewH / 2f - node.pos().y() * cellSize() * zoom;
    }

    /** Fits the viewport to the bounding box of every currently visible node. */
    public void fitToContent() {
        double minZoomCfg = GraphConfig.GRAPH.minZoom.get();
        double maxZoomCfg = GraphConfig.GRAPH.maxZoom.get();

        if (model == null || model.nodes().isEmpty()) {
            zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, GraphConfig.GRAPH.startZoom.get()));
            panX = viewX + viewW / 2f;
            panY = viewY + viewH / 2f;
            return;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (StageGraphModel.Node node : model.nodes().values()) {
            minX = Math.min(minX, node.pos().x());
            maxX = Math.max(maxX, node.pos().x());
            minY = Math.min(minY, node.pos().y());
            maxY = Math.max(maxY, node.pos().y());
        }

        int cell = cellSize();
        // +1 cell of padding on top of the column/row span so edge nodes and their captions
        // are not flush against the viewport border.
        float contentW = (maxX - minX) * cell + cell;
        float contentH = (maxY - minY) * cell + cell;

        float fitZoom = Math.min(viewW / contentW, viewH / contentH);
        zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, fitZoom));

        float centerGridX = (minX + maxX) / 2f;
        float centerGridY = (minY + maxY) / 2f;
        panX = viewX + viewW / 2f - centerGridX * cell * zoom;
        panY = viewY + viewH / 2f - centerGridY * cell * zoom;
    }

    // --- Input --------------------------------------------------------------------------------

    public boolean mouseClicked(double mx, double my, int button) {
        if (!within(mx, my)) return false;

        // Middle mouse always pans, even with a node under the cursor. Left-drag on empty space
        // pans too, but that is unusable in the editor once the map is dense — there is barely
        // any empty space to grab, and every miss opens the detail panel over what you wanted
        // to look at.
        if (button == 2) {
            panning = true;
            return true;
        }
        if (button != 0) return false;

        // A press on a node (or, in editor mode, on an unplaced row) arms a drag instead of
        // panning; a press on empty space arms panning as before. The two can never both fire
        // from the same press, so dragging a node never also moves the camera underneath it.
        if (editor) {
            int unplacedIdx = unplacedIndexAt(mx, my);
            if (unplacedIdx >= 0) {
                armDrag(null, unplacedIdx, mx, my);
                return true;
            }
        }

        String hit = nodeAt(mx, my);
        if (editor && hit != null) {
            armDrag(hit, -1, mx, my);
        } else if (hit == null) {
            panning = true;
        }
        return true;
    }

    /** Records a press without acting on it yet — see {@link #mouseDragged}/{@link #mouseReleased}. */
    private void armDrag(String key, int unplacedIndex, double mx, double my) {
        dragArmed = true;
        dragStarted = false;
        pressX = mx;
        pressY = my;
        draggedKey = key;
        draggedUnplacedIndex = unplacedIndex;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            panX += (float) dx;
            panY += (float) dy;
            clampPan();
            return true;
        }
        if (dragArmed && button == 0) {
            if (!dragStarted) {
                double ddx = mx - pressX;
                double ddy = my - pressY;
                if (ddx * ddx + ddy * ddy > (double) DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    dragStarted = true;
                }
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if ((button == 0 || button == 2) && panning) {
            panning = false;
            snapCameraToPixel();
            return true;
        }
        if (button == 0 && dragArmed) {
            boolean started = dragStarted;
            String key = draggedKey;
            int unplacedIdx = draggedUnplacedIndex;
            clearDrag();
            if (started) finishDrag(key, unplacedIdx, mx, my);
            return true;
        }
        return false;
    }

    /**
     * Resolves a completed drag into a hypothetical complete position map and hands it to the
     * host via {@link #dragHandler} — the host decides whether to show the freeze confirmation
     * and, if so, only calls the supplied {@code commit} once the author agrees.
     */
    private void finishDrag(String key, int unplacedIdx, double mx, double my) {
        if (model == null || dragHandler == null || !within(mx, my)) return;

        if (unplacedIdx >= 0) {
            List<StageGraphModel.Node> unplaced = model.unplaced();
            if (unplacedIdx >= unplaced.size() || mx < viewX + UNPLACED_W) return; // dropped back on the strip
            StageGraphModel.Node node = unplaced.get(unplacedIdx);
            GraphPos newPos = GraphPos.of(gridX(mx), gridY(my));
            boolean frozen = GraphLayoutData.get().isFrozen(node.individual());
            Map<String, GraphPos> preview = previewPositions(node.individual(), node.stageId(), newPos);
            dragHandler.onDrop(node.individual(), frozen, preview, () -> commitUnplacedDrop(node, newPos));
        } else if (key != null) {
            StageGraphModel.Node node = model.nodes().get(key);
            if (node == null) return;
            GraphPos newPos = GraphPos.of(gridX(mx), gridY(my));
            if (newPos.equals(node.pos())) return; // released back where it started; nothing to report
            boolean frozen = GraphLayoutData.get().isFrozen(node.individual());
            Map<String, GraphPos> preview = previewPositions(node.individual(), node.stageId(), newPos);
            dragHandler.onDrop(node.individual(), frozen, preview, () -> commitNodeMove(key, node, newPos));
        }
    }

    /** The complete position map {@code individual}'s tree would have if the drop is committed. */
    private Map<String, GraphPos> previewPositions(boolean individual, String overrideId, GraphPos overridePos) {
        Map<String, GraphPos> out = new LinkedHashMap<>();
        for (StageGraphModel.Node node : model.nodes().values()) {
            if (node.individual() == individual) out.put(node.stageId(), node.pos());
        }
        out.put(overrideId, overridePos);
        return out;
    }

    private void commitNodeMove(String key, StageGraphModel.Node original, GraphPos newPos) {
        StageGraphModel.Node moved = new StageGraphModel.Node(original.stageId(), original.individual(), newPos,
                original.state(), original.anonymous(), original.label(), original.icon());
        model.nodes().put(key, moved);
        refreshDrawOrder();
    }

    private void commitUnplacedDrop(StageGraphModel.Node original, GraphPos newPos) {
        model.unplaced().remove(original);
        StageGraphModel.Node placed = new StageGraphModel.Node(original.stageId(), original.individual(), newPos,
                original.state(), original.anonymous(), original.label(), original.icon());
        model.nodes().put(StageManager.graphKey(original.stageId(), original.individual()), placed);
        refreshDrawOrder();
    }

    private void refreshDrawOrder() {
        this.drawOrder = new ArrayList<>(model.nodes().entrySet());
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!within(mx, my)) return false;
        float old = zoom;
        double minZoomCfg = GraphConfig.GRAPH.minZoom.get();
        double maxZoomCfg = GraphConfig.GRAPH.maxZoom.get();
        zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, zoom + scrollY * ZOOM_STEP));
        if (zoom == old) return true;   // already at a limit, leave the camera alone
        // Zoom toward the cursor: keep the grid point currently under it fixed on screen.
        panX = (float) (mx - ((mx - panX) / old) * zoom);
        panY = (float) (my - ((my - panY) / old) * zoom);
        clampPan();
        snapCameraToPixel();
        return true;
    }
}
