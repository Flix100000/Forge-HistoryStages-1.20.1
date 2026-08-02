package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.graph.GraphNode;
import net.bananemdnsa.historystages.client.editor.graph.GraphViewFilter;
import net.bananemdnsa.historystages.client.editor.graph.NodeShapes;
import net.bananemdnsa.historystages.client.editor.graph.NodeTextures;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Read-only visualization of all stages, their properties, lock status, and
 * dependencies. Left third: collapsible stage list. Right two-thirds:
 * focus/neighborhood graph. Opened from {@link StageOverviewScreen}.
 */
public class DependencyGraphScreen extends Screen {

    private final Screen parent;
    /** Decides which stages and node categories this view may show. */
    private final GraphViewFilter filter;
    /** True for the editor/admin view (shows a back button); false for the player view. */
    private final boolean editorView;

    // --- Left panel state ---
    private static final int ROW_H = 20;               // card row slot (card height + gap)
    private static final int CARD_H = 16;              // visible card height within the slot
    private static final int PROP_ROW_H = 11;          // vertical step for a property line
    private static final int DEP_ROW_H = 10;           // vertical step for a dependency line
    private static final int PROPS_ALL_ROWS = 4;       // Mode, Research time, Pedestal tier, Lock entries
    private int panelWidth;                 // 1/3 of screen
    private double panelScroll = 0;
    private int panelContentHeight = 0;
    private boolean draggingPanelScrollbar = false;
    /** Stage id currently expanded in the panel (properties shown), or null. */
    private String expandedStageId = null;
    /** Whether the dependency sub-block of the expanded stage is open. */
    private boolean depsExpanded = false;
    /** Stage id currently focused in the graph, or null. */
    private String focusStageId = null;

    // --- Graph state ---
    private net.bananemdnsa.historystages.client.editor.graph.GraphModel graphModel = null;
    private float panX = 0f;
    private float panY = 0f;
    private float zoom = 1.0f;
    private boolean panning = false;
    // --- Animation state ---
    private long graphBuildTime = 0L;   // start of the focus-change pop-in
    private float introScale = 1f;      // node scale during pop-in (eased, may overshoot)
    private float introAlpha = 1f;      // edge fade-in during pop-in (0..1)
    /** Eased graph-node hover-lift progress (0..1). */
    private final Anim hoverNodeAnim = new Anim();
    private float legendAnim = 0f;      // legend body slide progress (0=closed, 1=open)
    private static final int POP_MS = 260;
    private boolean legendExpanded = false;
    private static final int LEGEND_W = 158;        // expanded panel width
    private static final int LEGEND_HEADER_H = 15;  // clickable header strip height
    private static final int LEGEND_ROW = 13;       // height of one legend entry row
    private static final int LEGEND_SUBH = 12;      // height of a section sub-header
    private static final int LEGEND_PAD = 5;        // inner top/bottom padding of the body
    private static final float NODE_R = 15f;      // stage node radius (graph-space)
    private static final float DETAIL_R = 11f;    // detail/trigger node radius (graph-space)
    private static final float LABEL_HIDE_ZOOM = 0.55f; // below this zoom, node labels are hidden

    /** Admin view — opened from the editor. Sees everything. */
    public DependencyGraphScreen(Screen parent) {
        this(parent, GraphViewFilter.passThrough(), "editor.historystages.depgraph.title", true);
    }

    /** Player view — opened from the pause screen. Applies the server's {@code [graph]} config. */
    public static DependencyGraphScreen forPlayer(Screen parent) {
        return new DependencyGraphScreen(parent, GraphViewFilter.fromConfig(),
                "graph.historystages.title", false);
    }

    private DependencyGraphScreen(Screen parent, GraphViewFilter filter, String titleKey, boolean editorView) {
        super(Component.translatable(titleKey));
        this.parent = parent;
        this.filter = filter;
        this.editorView = editorView;
    }

    @Override
    protected void init() {
        panelWidth = this.width / 3;
        // Back button — editor view only. The player view relies on ESC (its parent is the
        // pause screen) and must not offer an editor-style navigation control.
        if (editorView) {
            this.addRenderableWidget(net.bananemdnsa.historystages.client.editor.widget.StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> this.minecraft.setScreen(parent),
                    6, 2, 60, 18));
        }
    }

    /** (Re)builds {@link #graphModel} for the focused stage and restarts the pop-in animation. */
    private void rebuildGraph() {
        graphModel = focusStageId != null
                ? net.bananemdnsa.historystages.client.editor.graph.GraphModel.build(focusStageId, filter)
                : null;
        if (graphModel != null) {
            net.bananemdnsa.historystages.client.editor.graph.GraphLayout.apply(graphModel);
        }
        graphBuildTime = System.currentTimeMillis();
        introScale = 0f;
        introAlpha = 0f;
        hoverNodeAnim.set(0f);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — avoid 1.21's menu blur shader.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        renderPanel(g, mouseX, mouseY);
        renderGraphArea(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        // Node tooltip (drawn on top of everything) — but not for nodes hidden under the legend.
        if (graphModel != null && mouseX >= panelWidth && !isOverLegend(mouseX, mouseY)) {
            net.bananemdnsa.historystages.client.editor.graph.GraphNode hov = nodeAt(mouseX, mouseY);
            if (hov != null && !hov.tooltip.isEmpty()) {
                drawGraphTooltip(g, hov.tooltip, mouseX, mouseY);
            }
        }
    }

    /**
     * Renders a node tooltip in the editor's own style (dark bg, gold top accent, subtle border)
     * instead of the vanilla {@code renderComponentTooltip} look — matching the tooltip boxes
     * used elsewhere in the editor. Lines may carry legacy {@code §} formatting codes.
     */
    private void drawGraphTooltip(GuiGraphics g, List<String> lines, int mx, int my) {
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, this.font.width(l));

        int pad = 5;
        int lineH = this.font.lineHeight + 1;
        int boxW = maxW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 1;

        int bx = mx + 10;
        int by = my - 4;
        if (bx + boxW > this.width - 4) bx = this.width - 4 - boxW;
        if (by + boxH > this.height - 4) by = this.height - 4 - boxH;
        if (by < 4) by = 4;

        // z above the spinning entity previews (rendered at effective depth ~550, see
        // EntityPreviewRenderer) so their models don't poke through the tooltip box.
        g.pose().pushPose();
        g.pose().translate(0, 0, 700);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, 0xFF555555); // border
        g.fill(bx, by, bx + boxW, by + boxH, 0xFF1A1A1A);                 // background
        g.fill(bx, by, bx + boxW, by + 1, 0xFFFFCC00);                    // gold top accent
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, lines.get(i), bx + pad, by + pad + i * lineH, 0xFFDDDDDD, false);
        }
        g.pose().popPose();
    }

    // --- Left panel -------------------------------------------------------

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        int top = 24;
        int bottom = this.height - 8;
        int left = 6;
        int right = panelWidth - 4;

        g.fill(left, top, right, bottom, 0x40000000);
        g.fill(right, top, right + 1, bottom, 0xFF555555); // divider

        g.enableScissor(left, top, right, bottom);
        int y = top + 4 - (int) panelScroll;

        Map<String, StageEntry> global = StageManager.getStages();
        Map<String, StageEntry> individual = StageManager.getIndividualStages();

        y = renderSection(g, tr("panel.global"), visibleOrder(false), global, false, left, right, top, bottom, mouseX, mouseY, y);
        y = renderSection(g, tr("panel.individual"), visibleOrder(true), individual, true, left, right, top, bottom, mouseX, mouseY, y);

        panelContentHeight = (y + (int) panelScroll) - (top + 4);
        g.disableScissor();

        renderPanelScrollbar(g, top, bottom, right, mouseX, mouseY);
    }

    /**
     * The panel's stage order for one section, with everything the filter hides removed.
     * Rendering and click hit-testing both go through this so an invisible row can never
     * be clicked.
     */
    private List<String> visibleOrder(boolean individual) {
        List<String> order = individual ? StageManager.getIndividualStageOrder() : StageManager.getStageOrder();
        return order.stream().filter(id -> filter.showsStage(id, individual)).toList();
    }

    /** The label a panel row shows for a stage — the placeholder when it is anonymised. */
    private String rowLabel(String id, StageEntry entry) {
        return filter.anonymizes(id, entry)
                ? Component.translatable("editor.historystages.depgraph.hidden_stage").getString()
                : entry.getDisplayName();
    }

    /** Draws the panel scrollbar (track + thumb) at the panel's right edge when content overflows. */
    private void renderPanelScrollbar(GuiGraphics g, int top, int bottom, int right, int mouseX, int mouseY) {
        int trackH = bottom - top;
        int maxScroll = Math.max(0, panelContentHeight - trackH);
        if (maxScroll <= 0) return;
        int thumbH = Math.min(trackH, Math.max(20, (int) ((long) trackH * trackH / panelContentHeight)));
        int thumbY = top + (int) Math.round((trackH - thumbH) * (panelScroll / maxScroll));
        int sx = right - 4;
        boolean hot = draggingPanelScrollbar
                || (mouseX >= sx - 1 && mouseX <= right && mouseY >= top && mouseY <= bottom);
        g.fill(sx, top, right, bottom, 0x40000000);
        g.fill(sx, thumbY, right, thumbY + thumbH, hot ? 0xFFBBBBBB : 0xFF666666);
    }

    /** True if (mx,my) is on the panel scrollbar (only meaningful when the content overflows). */
    private boolean isOverPanelScrollbar(double mx, double my) {
        int top = 24, bottom = this.height - 8, right = panelWidth - 4;
        int maxScroll = Math.max(0, panelContentHeight - (bottom - top));
        return maxScroll > 0 && mx >= right - 5 && mx <= right && my >= top && my <= bottom;
    }

    /** Moves panelScroll so the thumb centre tracks the mouse Y. */
    private void panelScrollbarDragTo(double my) {
        int top = 24, bottom = this.height - 8;
        int trackH = bottom - top;
        int maxScroll = Math.max(0, panelContentHeight - trackH);
        if (maxScroll <= 0) return;
        int thumbH = Math.min(trackH, Math.max(20, (int) ((long) trackH * trackH / panelContentHeight)));
        double rel = (my - top - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        panelScroll = Math.max(0, Math.min(1, rel)) * maxScroll;
    }

    private int renderSection(GuiGraphics g, String header, List<String> order, Map<String, StageEntry> map,
                              boolean individual, int left, int right, int top, int bottom,
                              int mouseX, int mouseY, int y) {
        if (order.isEmpty()) return y;
        // Section header: muted bold label + thin divider.
        g.drawString(this.font, "§l§8" + header.toUpperCase(java.util.Locale.ROOT) + " §r§8(" + order.size() + ")",
                left + 4, y + 3, 0x888888, false);
        g.fill(left + 4, y + ROW_H - 3, right - 2, y + ROW_H - 2, 0x40FFFFFF);
        y += ROW_H;

        for (String id : order) {
            StageEntry entry = map.get(id);
            if (entry == null) continue;

            boolean rowVisible = y + CARD_H > top && y < bottom;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + CARD_H;
            if (rowVisible) {
                drawStageCard(g, id, entry, individual, hovered, left, right, y);
            }
            y += ROW_H;

            if (id.equals(expandedStageId)) {
                // Nested detail panel behind the properties/dependencies, with a gold guide.
                int detailH = propsRowCount() * PROP_ROW_H + ROW_H
                        + (depsExpanded ? dependencyBlockHeight(entry) : 0);
                g.fill(left + 8, y, right, y + detailH, 0xFF161616);
                g.fill(left + 8, y, left + 9, y + detailH, 0x66FFCC00);
                y = renderProperties(g, entry, individual, left, y);
                if (depsExpanded) {
                    y = renderDependencies(g, entry, left, y);
                }
            }
        }
        return y;
    }

    /** Draws a single stage as a card: status accent + item icon + name + lock badge. */
    private void drawStageCard(GuiGraphics g, String id, StageEntry entry, boolean individual,
                               boolean hovered, int left, int right, int y) {
        boolean focused = id.equals(focusStageId);
        boolean expanded = id.equals(expandedStageId);
        boolean unlocked = !individual && ClientStageCache.isStageUnlocked(id);
        int cardBot = y + CARD_H;

        int accent = individual ? 0xFF6688BB : (unlocked ? 0xFF4CAF50 : 0xFF777777);
        int bg = focused ? 0xFF26210F : (hovered ? 0xFF262626 : 0xFF1C1C1C);
        int borderC = focused ? 0xFFFFCC00 : 0xFF333333;

        g.fill(left, y, right, cardBot, bg);
        g.fill(left, y, right, y + 1, borderC);
        g.fill(left, cardBot - 1, right, cardBot, borderC);
        g.fill(left, y, left + 1, cardBot, borderC);
        g.fill(right - 1, y, right, cardBot, borderC);
        g.fill(left, y, left + 3, cardBot, accent);

        g.drawString(this.font, expanded ? "▼" : "▶", left + 7, y + 4, 0xFFAAAAAA, false);

        ItemStack icon = panelIconStack(entry, filter.anonymizes(id, entry));
        if (icon != null && !icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(left + 16, y + 2, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }

        int nameRight = right - 6;
        if (!individual) {
            String badge = unlocked ? "§a✔" : "§7🔒";
            int bw = this.font.width(badge);
            g.drawString(this.font, badge, right - bw - 6, y + 4, 0xFFFFFF, false);
            nameRight = right - bw - 10;
        }

        int nameX = left + 30;
        String name = rowLabel(id, entry);
        int avail = nameRight - nameX;
        if (this.font.width(name) > avail) {
            name = this.font.plainSubstrByWidth(name, avail - 6) + "..";
        }
        g.drawString(this.font, name, nameX, y + 4, individual ? 0xFFBBBBBB : 0xFFEEEEEE, false);
    }

    /**
     * Resolves a stage's icon (custom, else the configured default) to an ItemStack, or null.
     * An anonymised stage always falls back to the default icon — a custom icon typically
     * shows the stage's reward item and would give it away.
     */
    private ItemStack panelIconStack(StageEntry entry, boolean anon) {
        String iconId = (anon || entry.getIcon().isEmpty())
                ? net.bananemdnsa.historystages.Config.COMMON.defaultStageIcon.get()
                : entry.getIcon();
        return resolveItem(iconId);
    }
    /**
     * Whether the panel may name a stage's mode. Tied to {@code showTriggers}: the graph hides a
     * stage's auto-unlock conditions when that is off, and "Mode: AUTO" would still give away
     * that it unlocks on its own.
     *
     * <p>Note this hides the line for <i>every</i> stage, not just AUTO ones. Hiding it only for
     * AUTO stages would leak the same fact through the gap — a missing mode line would itself
     * read as "this one is automatic".</p>
     */
    private boolean showsModeLine() {
        return filter.showsTriggers();
    }

    /**
     * Whether the panel's dependency block may list the non-stage requirements (items, XP,
     * kills, ...). Tied to {@code showStageElements}, which promises the stage skeleton without
     * its contents — a promise the panel would otherwise break by counting them out.
     */
    private boolean showsElementLines() {
        return filter.showsDetails();
    }

    /** Property lines actually drawn — the geometry below derives from this, never from a constant. */
    private int propsRowCount() {
        return showsModeLine() ? PROPS_ALL_ROWS : PROPS_ALL_ROWS - 1;
    }

    private int renderProperties(GuiGraphics g, StageEntry entry, boolean individual, int left, int y) {
        int x = left + 20;
        if (showsModeLine()) {
            g.drawString(this.font, "§7" + tr("prop.mode") + ": §f" + entry.getMode().serialize(), x, y + 2, 0xAAAAAA, false);
            y += PROP_ROW_H;
        }
        g.drawString(this.font, "§7" + tr("prop.research_time") + ": §f" + entry.getResearchTime(), x, y + 2, 0xAAAAAA, false);
        y += PROP_ROW_H;
        g.drawString(this.font, "§7" + tr("prop.pedestal_tier") + ": §f" + entry.getMinPedestalTier(), x, y + 2, 0xAAAAAA, false);
        y += PROP_ROW_H;
        int lockCount = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                + entry.getRecipes().size() + entry.getDimensions().size() + entry.getStructures().size()
                + entry.getBiomes().size();
        g.drawString(this.font, "§7" + tr("prop.lock_entries") + ": §f" + lockCount, x, y + 2, 0xAAAAAA, false);
        y += PROP_ROW_H;
        // Dependencies sub-toggle
        String depArrow = depsExpanded ? "\u25BC " : "\u25B6 ";
        g.drawString(this.font, "§6" + depArrow + tr("prop.dependencies") + " (" + entry.getDependencies().size() + ")", x, y + 2, 0xFFAA55, false);
        y += ROW_H;
        return y;
    }

    private int renderDependencies(GuiGraphics g, StageEntry entry, int left, int y) {
        int x = left + 28;
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups.isEmpty()) {
            g.drawString(this.font, "§8" + tr("dep.none"), x, y + 2, 0x777777, false);
            return y + PROP_ROW_H;
        }
        for (int gi = 0; gi < groups.size(); gi++) {
            DependencyGroup grp = groups.get(gi);
            if (grp.isEmpty()) continue;
            g.drawString(this.font, "§6" + tr("dep.group") + " " + (gi + 1) + " §8[" + grp.getLogic() + "]", x, y + 2, 0x999999, false);
            y += DEP_ROW_H;
            for (String s : grp.getStages()) { g.drawString(this.font, "§7- " + tr("dep.stage") + ": " + s, x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            for (IndividualStageDep d : grp.getIndividualStages()) { g.drawString(this.font, "§7- " + tr("dep.ind_stage") + ": " + d.getStageId(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (!showsElementLines()) continue;
            if (!grp.getItems().isEmpty()) { g.drawString(this.font, "§7- " + tr("dep.items") + ": " + grp.getItems().size(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (grp.getXpLevel() != null) { g.drawString(this.font, "§7- " + tr("dep.xp_level") + ": " + grp.getXpLevel().getLevel(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (!grp.getAdvancements().isEmpty()) { g.drawString(this.font, "§7- " + tr("dep.advancements") + ": " + grp.getAdvancements().size(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (!grp.getEntityKills().isEmpty()) { g.drawString(this.font, "§7- " + tr("dep.kills") + ": " + grp.getEntityKills().size(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (!grp.getStats().isEmpty()) { g.drawString(this.font, "§7- " + tr("dep.stats") + ": " + grp.getStats().size(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
            if (!grp.getScoreboard().isEmpty()) { g.drawString(this.font, "§7- " + tr("dep.scoreboard") + ": " + grp.getScoreboard().size(), x + 6, y + 2, 0xAAAAAA, false); y += DEP_ROW_H; }
        }
        return y + 2;
    }

    // --- Graph area -------------------------------------------------------

    private void renderGraphArea(GuiGraphics g, int mouseX, int mouseY) {
        int left = panelWidth + 4;
        int top = 24;
        int right = this.width;
        int bottom = this.height;

        if (graphModel == null) {
            g.drawCenteredString(this.font, Component.translatable("editor.historystages.depgraph.hint"),
                    left + (right - left) / 2, this.height / 2, 0x888888);
            return;
        }

        g.enableScissor(left, top, right, bottom);

        // --- Per-frame animation progress ---
        long since = System.currentTimeMillis() - graphBuildTime;
        float introT = Math.max(0f, Math.min(1f, since / (float) POP_MS));
        introScale = easeOutBack(introT);   // nodes scale up (with a slight overshoot)
        introAlpha = introT;                // edges fade in
        boolean intro = introT < 1f;
        // Node under the cursor for the hover lift — only once the intro has settled.
        var hov = (!intro && mouseX >= panelWidth && mouseY >= top) ? nodeAt(mouseX, mouseY) : null;
        hoverNodeAnim.ramp(hov != null, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS);

        // Edges first (under nodes). Endpoints are inset to each node's boundary so the
        // arrowhead at the target is not hidden under the node.
        for (var edge : graphModel.edges) {
            float fx = panX + edge.from.x * zoom;
            float fy = panY + edge.from.y * zoom;
            float tx = panX + edge.to.x * zoom;
            float ty = panY + edge.to.y * zoom;
            float ex = tx - fx, ey = ty - fy;
            float len = (float) Math.hypot(ex, ey);
            if (len > 1f) {
                float ux = ex / len, uy = ey / len;
                fx += ux * nodeRadius(edge.from);
                fy += uy * nodeRadius(edge.from);
                tx -= ux * nodeRadius(edge.to);
                ty -= uy * nodeRadius(edge.to);
            }
            drawEdge(g, (int) fx, (int) fy, (int) tx, (int) ty, edge.dashed);
        }

        // Nodes: one node fully drawn at a time (shape, then its icon/entity/label,
        // then caption) — the same proven pattern used by AutoTriggerEditorScreen and
        // StageDetailScreen, which mix item sprites and spinning 3D entities in a single
        // frame without trouble. The entity renderer flips global render state and calls
        // endBatch() on the shared buffer source, but drawEntityIcon's own enableScissor
        // flushes any pending shape/sprite geometry first (with the correct model-view),
        // so a later item sprite is never corrupted by an earlier entity model.
        for (var node : graphModel.nodes) {
            int cx = (int) (panX + node.x * zoom);
            int cy = (int) (panY + node.y * zoom);
            // Visual scale: the pop-in while animating in, otherwise a hover lift.
            float vs = intro ? introScale : (node == hov ? 1f + 0.18f * Ease.outCubic(hoverNodeAnim.value()) : 1f);
            boolean scaled = Math.abs(vs - 1f) > 0.001f;
            if (scaled) {
                g.pose().pushPose();
                g.pose().translate(cx, cy, 0);
                g.pose().scale(vs, vs, 1f);
                g.pose().translate(-cx, -cy, 0);
            }
            drawNodeShape(g, node, cx, cy);
            if (node.entityId == null) {
                drawNodeIcon(g, node, cx, cy);
            }
            if (scaled) g.pose().popPose();
            // 3D entities use their own render path (pose scale doesn't apply), so scale
            // them via their radius and only draw once the pop-in has finished.
            if (node.entityId != null && !intro) {
                int er = Math.max(3, (int) (nodeRadius(node) * (node == hov ? 1f + 0.18f * Ease.outCubic(hoverNodeAnim.value()) : 1f)));
                drawEntityIcon(g, node.entityId, cx, cy, er);
            }
        }

        g.disableScissor();

        // Legend is drawn OUTSIDE the graph scissor so it stays at a fixed screen
        // position (bottom-right) regardless of pan/zoom.
        renderLegend(g, mouseX, mouseY);
    }

    /** Ease-out with a slight overshoot, for the node pop-in. */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private void drawEdge(GuiGraphics g, int x1, int y1, int x2, int y2, boolean dashed) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float angle = (float) Math.atan2(dy, dx);
        int alpha = Math.max(0, Math.min(255, Math.round(0xFF * introAlpha)));
        int color = (alpha << 24) | 0x999999;
        float thickness = Math.max(1.5f, 2.2f * zoom);
        int r = Math.max(1, Math.round(thickness / 2f));

        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));

        if (dashed) {
            float dash = 7f * Math.max(1f, zoom), gap = 5f * Math.max(1f, zoom);
            for (float s = 0; s < len; s += dash + gap) {
                float e = Math.min(len, s + dash);
                blitLine(g, (int) s, (int) e, r, color);
            }
        } else {
            blitLine(g, 0, (int) len, r, color);
            // Round caps on a continuous line keep the ends from looking cut off.
            NodeTextures.blit(g, NodeTextures.circle(), -r, -r, r * 2, r * 2,
                    NodeTextures.SIZE, NodeTextures.SIZE, color);
            NodeTextures.blit(g, NodeTextures.circle(), (int) len - r, -r, r * 2, r * 2,
                    NodeTextures.SIZE, NodeTextures.SIZE, color);
        }
        g.pose().popPose();

        drawArrowHead(g, x2, y2, angle, thickness, color);
    }

    /** Blits the smooth line texture as a shaft from local x={@code s} to x={@code e}. */
    private void blitLine(GuiGraphics g, int s, int e, int r, int color) {
        if (e <= s) return;
        NodeTextures.blit(g, NodeTextures.line(), s, -r, e - s, r * 2,
                NodeTextures.SIZE, NodeTextures.LINE_H, color);
    }

    /** Draws the arrowhead at (tx,ty), pointing along {@code angle} toward the target node. */
    private void drawArrowHead(GuiGraphics g, int tx, int ty, float angle, float thickness, int color) {
        int ah = Math.max(4, Math.round(2.4f * thickness)); // arrow half-size
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        // Arrow texture's tip is at +x; place the tip at the target point (local x = 0).
        NodeTextures.blit(g, NodeTextures.arrow(), -2 * ah, -ah, 2 * ah, 2 * ah,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
        g.pose().popPose();
    }

    private int nodeRadius(GraphNode node) {
        return Math.max(3, (int) ((node.isStage() ? NODE_R : DETAIL_R) * zoom));
    }

    /** Draws the node's outline-ring shape (dark interior + category-coloured ring) + focus ring. */
    private void drawNodeShape(GuiGraphics g, GraphNode node, int cx, int cy) {
        int ri = nodeRadius(node);
        int ringW = Math.max(2, Math.round(3f * zoom)); // bold ring for the outline look
        int interior = 0xFF17171A;                      // dark interior for every node
        int ring;
        if (node.type == GraphNode.Type.STAGE_GLOBAL) {
            ring = node.unlocked ? 0xFF4CAF50 : 0xFF777777; // green / grey
        } else if (node.type == GraphNode.Type.STAGE_INDIVIDUAL) {
            ring = 0xFF6688BB;                              // blue
        } else if (node.category == GraphNode.Category.TRIGGER) {
            ring = 0xFFB08A2A;                              // amber
        } else {
            ring = 0xFF5A6A8A;                              // bluish detail
        }

        // Focus highlight: a gold halo ring just outside the node.
        if (node.isStage() && node.id.equals(focusStageId)) {
            drawShape(g, node.category, cx, cy, ri + ringW, 0xFFFFCC00, 0xFFFFCC00, ringW);
        }
        drawShape(g, node.category, cx, cy, ri, interior, ring, ringW);
    }

    /** Dispatches to the right {@link NodeShapes} shape for the node's category. */
    private void drawShape(GuiGraphics g, GraphNode.Category cat, int cx, int cy, int r,
                           int fill, int border, int bw) {
        switch (cat) {
            case STAGE -> NodeShapes.circle(g, cx, cy, r, fill, border, bw);
            case DETAIL -> NodeShapes.diamond(g, cx, cy, r, fill, border, bw);
            case TRIGGER -> NodeShapes.hexagon(g, cx, cy, r, fill, border, bw);
        }
    }

    /**
     * Draws the item sprite or text label inside a (non-entity) node plus the stage
     * caption below it. Entity nodes are handled by {@link #drawEntityIcon} instead.
     */
    private void drawNodeIcon(GuiGraphics g, GraphNode node, int cx, int cy) {
        boolean stage = node.isStage();
        int ri = nodeRadius(node);

        ItemStack stack = resolveItem(node.itemIcon);
        if (stack != null && !stack.isEmpty()) {
            drawItemSprite(g, stack, cx, cy, ri);
        } else if (stage) {
            // Stage without an icon: show its initial (full name goes in the caption below).
            String initial = node.label.isEmpty() ? "?" : node.label.substring(0, 1).toUpperCase();
            drawCenteredScaledText(g, initial, cx, cy, 0xFFEEEEEE);
        } else if (!node.label.isEmpty()) {
            drawCenteredScaledText(g, node.label, cx, cy, 0xFFFFFFFF);
        }

        // Stage name caption below the node (zoom-scaled, hidden when far out).
        if (stage && zoom >= LABEL_HIDE_ZOOM && !node.label.isEmpty()) {
            drawCaptionBelow(g, node.label, cx, cy + ri + 2);
        }
    }

    /** Cache of resolved item-icon stacks, keyed by id (avoids per-frame allocation). */
    private final java.util.Map<String, ItemStack> itemStackCache = new java.util.HashMap<>();

    /** Resolves an item/block id to an ItemStack (cached), or null if unknown/air. */
    private ItemStack resolveItem(String id) {
        if (id == null) return null;
        ItemStack cached = itemStackCache.get(id);
        if (cached != null) return cached.isEmpty() ? null : cached;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        itemStackCache.put(id, stack);
        return stack.isEmpty() ? null : stack;
    }

    /**
     * Draws a 16x16 item sprite centered at (cx, cy), scaled with the current zoom so it
     * grows/shrinks together with the node (at zoom 1.0 it matches the plain 16x16
     * sprite). Only X/Y are scaled — the sprite's Z stays at renderItem's default (150)
     * to avoid pushing it out of the GUI depth range.
     */
    private void drawItemSprite(GuiGraphics g, ItemStack stack, int cx, int cy, int ri) {
        if (zoom == 1.0f) {
            g.renderItem(stack, cx - 8, cy - 8);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(zoom, zoom, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    /** Draws the spinning 3D entity model clipped to the node box. */
    private void drawEntityIcon(GuiGraphics g, String entityId, int cx, int cy, int ri) {
        LivingEntity living = EntityPreviewRenderer.getOrCreate(entityId);
        if (living == null) return;
        float angle = (System.currentTimeMillis() % 3600) / 10.0f;
        int scale = (int) Math.max(3, (ri * 1.4f) / Math.max(living.getBbWidth(), living.getBbHeight()));
        g.enableScissor(cx - ri, cy - ri, cx + ri, cy + ri);
        try {
            EntityPreviewRenderer.renderSpinning(g, cx, cy + ri - 2, scale, angle, living);
        } catch (Exception ignored) {
        } finally {
            g.disableScissor();
        }
    }

    /** Draws short text centered on the node, scaled with zoom. */
    private void drawCenteredScaledText(GuiGraphics g, String text, int cx, int cy, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 50);
        g.pose().scale(zoom, zoom, 1f);
        int tw = this.font.width(text);
        g.drawString(this.font, text, -tw / 2, -4, color, false);
        g.pose().popPose();
    }

    /** Draws a caption centered horizontally below the node, scaled with zoom. */
    private void drawCaptionBelow(GuiGraphics g, String text, int cx, int topY) {
        g.pose().pushPose();
        g.pose().translate(cx, topY, 50);
        g.pose().scale(zoom, zoom, 1f);
        int tw = this.font.width(text);
        g.drawString(this.font, text, -tw / 2, 0, 0xDDDDDD, false);
        g.pose().popPose();
    }

    /** Localized string for a {@code editor.historystages.depgraph.<key>} entry. */
    private String tr(String key) {
        return Component.translatable("editor.historystages.depgraph." + key).getString();
    }

    /** Localized legend string for the given sub-key. */
    private String legendText(String key) {
        return tr("legend." + key);
    }

    /** Width of the legend box as currently shown (full while open or animating, else slim). */
    private int legendCurrentW() {
        boolean wide = legendExpanded || legendAnim > 0.02f;
        return wide ? LEGEND_W : (this.font.width("▶ " + legendText("title")) + 12);
    }
    /** Height of the expanded body (two sections: 5 node rows + 2 connection rows). */
    private int legendBodyHeight() {
        return LEGEND_PAD * 2 + LEGEND_SUBH * 2 + 7 * LEGEND_ROW + 4;
    }
    private int legendX() { return this.width - legendCurrentW() - 4; }
    /** Y of the always-visible header strip — fixed at the bottom-right corner. */
    private int legendHeaderTop() { return this.height - 4 - LEGEND_HEADER_H; }
    /** Y where the expanded body starts; it grows upward, above the fixed header. */
    private int legendBodyTop() { return legendHeaderTop() - legendBodyHeight(); }

    private void renderLegend(GuiGraphics g, int mouseX, int mouseY) {
        // Ease the body slide toward the open/closed target.
        float target = legendExpanded ? 1f : 0f;
        legendAnim += (target - legendAnim) * 0.30f;
        if (Math.abs(target - legendAnim) < 0.01f) legendAnim = target;

        int curW = legendCurrentW();
        int lx = legendX();
        int hy = legendHeaderTop();
        boolean hoverHeader = legendHeaderClicked(mouseX, mouseY);

        // Drawn above the graph content (item sprites at z=150, captions at z=50, entity
        // models) so the legend is a true opaque overlay and nothing shows through it.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        // Body slides up from behind the header: draw it at its final position, clipped to
        // the revealed height so it animates open/closed.
        int bodyH = legendBodyHeight();
        int animH = Math.round(bodyH * legendAnim);
        if (animH > 1) {
            int fullTop = hy - bodyH;
            int revealTop = hy - animH;
            g.enableScissor(lx - 1, revealTop - 1, lx + curW + 1, hy);
            g.fill(lx - 1, fullTop - 1, lx + curW + 1, hy + LEGEND_HEADER_H + 1, 0xFF3D3D3D);
            g.fill(lx, fullTop, lx + curW, hy, 0xFF1A1A1A);
            renderLegendBody(g, lx, fullTop, curW);
            g.disableScissor();
        } else {
            g.fill(lx - 1, hy - 1, lx + curW + 1, hy + LEGEND_HEADER_H + 1, 0xFF3D3D3D);
        }

        // Header strip with gold accent underline — fixed at the bottom.
        g.fill(lx, hy, lx + curW, hy + LEGEND_HEADER_H, hoverHeader ? 0xFF2E2E2E : 0xFF222222);
        g.fill(lx, hy + LEGEND_HEADER_H - 1, lx + curW, hy + LEGEND_HEADER_H, 0xFFFFCC00);
        g.drawString(this.font, (legendExpanded ? "▼ " : "▶ ") + legendText("title"),
                lx + 5, hy + 4, 0xFFFFCC00, false);

        g.pose().popPose();
    }

    /** Renders the expanded legend's content (node + connection sections) starting at {@code by}. */
    private void renderLegendBody(GuiGraphics g, int lx, int by, int curW) {
        int sx = lx + 13;   // shape sample center x
        int tx = lx + 26;   // label text x
        int y = by + LEGEND_PAD;

        // --- Section: Nodes ---
        g.drawString(this.font, "§8" + legendText("nodes"), lx + 6, y, 0x888888, false);
        y += LEGEND_SUBH;
        y = legendShapeRow(g, sx, tx, y, GraphNode.Category.STAGE, 0xFF17171A, 0xFF4CAF50, "stage_unlocked");
        y = legendShapeRow(g, sx, tx, y, GraphNode.Category.STAGE, 0xFF17171A, 0xFF777777, "stage_locked");
        y = legendShapeRow(g, sx, tx, y, GraphNode.Category.STAGE, 0xFF17171A, 0xFF6688BB, "stage_individual");
        y = legendShapeRow(g, sx, tx, y, GraphNode.Category.DETAIL, 0xFF17171A, 0xFF5A6A8A, "detail");
        y = legendShapeRow(g, sx, tx, y, GraphNode.Category.TRIGGER, 0xFF17171A, 0xFFB08A2A, "trigger");

        // --- Section: Connections ---
        g.fill(lx + 6, y + 1, lx + curW - 6, y + 2, 0x30FFFFFF); // separator
        y += 4;
        g.drawString(this.font, "§8" + legendText("connections"), lx + 6, y, 0x888888, false);
        y += LEGEND_SUBH;
        // Solid line = AND (required together).
        g.fill(sx - 6, y + 5, sx + 7, y + 7, 0xFF999999);
        g.drawString(this.font, legendText("and"), tx, y + 1, 0xCCCCCC, false);
        y += LEGEND_ROW;
        // Dashed line = OR (alternative).
        for (int dxp = -6; dxp < 7; dxp += 4) {
            g.fill(sx + dxp, y + 5, sx + dxp + 2, y + 7, 0xFF999999);
        }
        g.drawString(this.font, legendText("or"), tx, y + 1, 0xCCCCCC, false);
    }

    /** Draws one node-sample row (shape + label) and returns the next row's y. */
    private int legendShapeRow(GuiGraphics g, int sx, int tx, int y, GraphNode.Category cat,
                               int fill, int border, String key) {
        int cy = y + 5;
        switch (cat) {
            case STAGE -> NodeShapes.circle(g, sx, cy, 5, fill, border, 2);
            case DETAIL -> NodeShapes.diamond(g, sx, cy, 5, fill, border, 2);
            case TRIGGER -> NodeShapes.hexagon(g, sx, cy, 5, fill, border, 2);
        }
        g.drawString(this.font, legendText(key), tx, y + 1, 0xCCCCCC, false);
        return y + LEGEND_ROW;
    }

    /** Returns true if (mx,my) is on the legend header (toggles expand/collapse). */
    private boolean legendHeaderClicked(double mx, double my) {
        int lx = legendX();
        int hy = legendHeaderTop();
        return mx >= lx && mx <= lx + legendCurrentW() && my >= hy && my <= hy + LEGEND_HEADER_H;
    }

    /** Returns true if (mx,my) is anywhere over the legend (header, plus the body when open). */
    private boolean isOverLegend(double mx, double my) {
        int lx = legendX();
        if (mx < lx || mx > lx + legendCurrentW()) return false;
        int top = legendExpanded ? legendBodyTop() : legendHeaderTop();
        return my >= top && my <= legendHeaderTop() + LEGEND_HEADER_H;
    }

    // --- Input ------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < panelWidth) {
            int viewport = (this.height - 8) - 24;
            int maxScroll = Math.max(0, panelContentHeight - viewport);
            panelScroll = Math.max(0, Math.min(maxScroll, panelScroll - scrollY * 12));
            return true;
        }
        if (graphModel != null) {
            float old = zoom;
            zoom = (float) Math.max(0.3, Math.min(2.5, zoom + scrollY * 0.1));
            // Zoom toward cursor: keep the graph point under the mouse fixed.
            panX = (float) mouseX - (((float) mouseX - panX) / old) * zoom;
            panY = (float) mouseY - (((float) mouseY - panY) / old) * zoom;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverPanelScrollbar(mouseX, mouseY)) {
            draggingPanelScrollbar = true;
            panelScrollbarDragTo(mouseY);
            return true;
        }
        if (button == 0 && mouseX <= panelWidth - 4 && mouseY >= 24) {
            handlePanelClick(mouseX, mouseY);
            return true;
        }
        if (button == 0 && legendHeaderClicked(mouseX, mouseY)) {
            legendExpanded = !legendExpanded;
            return true;
        }
        if (button == 0 && isOverLegend(mouseX, mouseY)) {
            return true; // swallow clicks on the expanded legend body so nodes behind it stay put
        }
        if (button == 0 && mouseX >= panelWidth && graphModel != null) {
            String clicked = stageNodeAt(mouseX, mouseY);
            if (clicked != null) {
                // Same rule as the panel rows: an anonymised stage must not expand.
                StageEntry entry = lookupStage(clicked);
                expandedStageId = filter.anonymizes(clicked, entry) ? null : clicked;
                depsExpanded = false;
                focusStageId = clicked;
                onFocusChanged();
            } else {
                panning = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Re-walks the panel layout to find which row was clicked (mirrors renderPanel). */
    private void handlePanelClick(double mouseX, double mouseY) {
        int top = 24;
        int left = 6;
        int y = top + 4 - (int) panelScroll;

        Map<String, StageEntry> global = StageManager.getStages();
        Map<String, StageEntry> individual = StageManager.getIndividualStages();

        int[] yBox = { y };
        if (hitSection(visibleOrder(false), global, false, left, mouseX, mouseY, yBox)) return;
        hitSection(visibleOrder(true), individual, true, left, mouseX, mouseY, yBox);
    }

    private boolean hitSection(List<String> order, Map<String, StageEntry> map, boolean individual,
                               int left, double mouseX, double mouseY, int[] yBox) {
        if (order.isEmpty()) return false;
        yBox[0] += ROW_H; // header

        for (String id : order) {
            StageEntry entry = map.get(id);
            if (entry == null) continue;
            int rowY = yBox[0];
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                onStageRowClicked(id, entry);
                return true;
            }
            yBox[0] += ROW_H;

            if (id.equals(expandedStageId)) {
                // properties block = property lines + deps-toggle line
                int propsLines = propsRowCount();
                int propsH = propsLines * PROP_ROW_H + ROW_H;
                int depsToggleY = yBox[0] + propsLines * PROP_ROW_H;
                if (mouseY >= depsToggleY && mouseY < depsToggleY + ROW_H) {
                    depsExpanded = !depsExpanded;
                    return true;
                }
                yBox[0] += propsH;
                if (depsExpanded) {
                    yBox[0] += dependencyBlockHeight(entry);
                }
            }
        }
        return false;
    }

    /** Mirrors renderDependencies height so click hit-testing stays aligned. */
    private int dependencyBlockHeight(StageEntry entry) {
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups.isEmpty()) return PROP_ROW_H;
        int h = 0;
        for (DependencyGroup grp : groups) {
            if (grp.isEmpty()) continue;
            h += DEP_ROW_H; // group header
            h += grp.getStages().size() * DEP_ROW_H;
            h += grp.getIndividualStages().size() * DEP_ROW_H;
            if (!showsElementLines()) continue;
            if (!grp.getItems().isEmpty()) h += DEP_ROW_H;
            if (grp.getXpLevel() != null) h += DEP_ROW_H;
            if (!grp.getAdvancements().isEmpty()) h += DEP_ROW_H;
            if (!grp.getEntityKills().isEmpty()) h += DEP_ROW_H;
            if (!grp.getStats().isEmpty()) h += DEP_ROW_H;
            if (!grp.getScoreboard().isEmpty()) h += DEP_ROW_H;
        }
        return h + 2;
    }

    private void onStageRowClicked(String id, StageEntry entry) {
        // An anonymised row must not expand: the properties/dependencies block is a full
        // description of the stage and would undo the anonymisation. It may still focus
        // the graph, where the node is anonymised as well.
        boolean anon = filter.anonymizes(id, entry);
        if (anon || id.equals(expandedStageId)) {
            // Clicking the already-expanded stage collapses it.
            expandedStageId = null;
            depsExpanded = false;
        } else {
            expandedStageId = id;
            depsExpanded = false;
        }
        focusStageId = id;            // selecting a row also focuses the graph
        onFocusChanged();             // overridden in a later task (no-op stub for now)
    }

    /** Rebuilds the graph around the new focus and re-centres the view on it. */
    protected void onFocusChanged() {
        panning = false;
        rebuildGraph();
        // Center the graph in the graph area.
        int left = panelWidth + 4;
        panX = left + (this.width - left) / 2f;
        panY = this.height / 2f;
        zoom = 1.0f;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPanelScrollbar) {
            panelScrollbarDragTo(mouseY);
            return true;
        }
        if (panning) {
            panX += (float) dragX;
            panY += (float) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPanelScrollbar && button == 0) { draggingPanelScrollbar = false; return true; }
        if (panning && button == 0) { panning = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Looks a stage id up in both maps (global first), or null if unknown. */
    private StageEntry lookupStage(String id) {
        StageEntry e = StageManager.getStages().get(id);
        return e != null ? e : StageManager.getIndividualStages().get(id);
    }

    /** Returns any node (stage or detail) under the cursor, or null. */
    private net.bananemdnsa.historystages.client.editor.graph.GraphNode nodeAt(double mouseX, double mouseY) {
        if (graphModel == null) return null;
        for (var node : graphModel.nodes) {
            int cx = (int) (panX + node.x * zoom);
            int cy = (int) (panY + node.y * zoom);
            float r = (node.isStage() ? NODE_R : DETAIL_R) * zoom + 2;
            if (Math.abs(mouseX - cx) <= r && Math.abs(mouseY - cy) <= r) {
                return node;
            }
        }
        return null;
    }

    /** Returns the stage id of a stage node under the cursor, or null. */
    private String stageNodeAt(double mouseX, double mouseY) {
        if (graphModel == null) return null;
        for (var node : graphModel.nodes) {
            if (!node.isStage()) continue;
            int cx = (int) (panX + node.x * zoom);
            int cy = (int) (panY + node.y * zoom);
            float r = NODE_R * zoom + 2;
            if (Math.abs(mouseX - cx) <= r && Math.abs(mouseY - cy) <= r) {
                return node.id;
            }
        }
        return null;
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}
