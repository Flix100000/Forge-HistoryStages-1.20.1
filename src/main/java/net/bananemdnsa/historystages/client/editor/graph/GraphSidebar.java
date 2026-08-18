package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.SearchBar;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Searchable stage list, grouped by folder, down the left side of the stage graph screen.
 *
 * <p>Folders exist here and nowhere else in this feature: they organise this list and nothing
 * more. They are never drawn on {@link GraphCanvas}, never tint a node, and picking one does not
 * move or hide anything — a click only calls {@link GraphCanvas#focusOn(String)}. The layout is
 * ordered by dependency depth, and a pack whose folders cut across that ordering cannot have
 * both; this was a deliberate trade made when the sidebar was designed.
 *
 * <p>Row geometry is computed once per rebuild into {@link #positioned}; both {@link #render} and
 * {@link #mouseClicked} read that same list, so a click can never land on a different row than
 * the one drawn under the cursor — the convention {@code StageOverviewScreen} uses for its own
 * list ({@code ListLayout} / {@code rowTop}).
 */
public final class GraphSidebar {

    private static final int STAGE_ROW_HEIGHT = 18;
    private static final int FOLDER_HEADER_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int SEARCH_GAP = 6;
    private static final int ROW_LEFT_PAD = 8;
    private static final String FOLDER_SEPARATOR = " / ";

    private static final int BG_COLOR = 0xFF17171A;
    private static final int EDGE_COLOR = 0x33FFFFFF;
    private static final int HEADER_TEXT_COLOR = 0xFF999999;
    private static final int ROW_TEXT_COLOR = 0xFFDDDDDD;
    private static final int ROW_TEXT_COLOR_HOVER = 0xFFFFFFFF;
    private static final int ACCENT_COLOR = 0xFFCC00;

    private final GraphCanvas canvas;
    private final SearchBar searchBar =
            new SearchBar(Component.translatable("editor.historystages.search").getString());

    private StageGraphModel model;
    private int x, y, width, height;
    private float scroll;
    private float maxScroll;

    /** One folder's worth of rows, built fresh whenever {@link #setModel} runs. */
    private record Group(String header, List<StageRow> rows) {}

    private record StageRow(String graphKey, String stageId, String label) {}

    /**
     * A single drawable/clickable row with its content-space top offset already resolved.
     * Rebuilt whenever the model or the search text changes ({@link #rebuildPositions}); render
     * and hit testing both walk this exact list so they can never disagree about where a row is.
     */
    private record PositionedRow(String headerText, StageRow stage, int top, int height) {
        static PositionedRow header(String text, int top) {
            return new PositionedRow(text, null, top, FOLDER_HEADER_HEIGHT);
        }

        static PositionedRow stage(StageRow s, int top) {
            return new PositionedRow(null, s, top, STAGE_ROW_HEIGHT);
        }

        boolean isHeader() {
            return headerText != null;
        }
    }

    private List<Group> groups = List.of();
    private List<PositionedRow> positioned = List.of();
    private final Map<String, Anim> hoverAnim = new HashMap<>();

    public GraphSidebar(GraphCanvas canvas) {
        this.canvas = canvas;
        searchBar.onChange(t -> rebuildPositions());
    }

    /** Rebuilds the folder grouping from scratch. Cheap enough to call on every model refresh. */
    public void setModel(StageGraphModel model) {
        this.model = model;
        rebuildGroups();
        rebuildPositions();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuildPositions(); // list height changed, so max scroll must be re-clamped
    }

    public boolean isSearchFocused() {
        return searchBar.isFocused();
    }

    // --- Grouping -------------------------------------------------------------------------

    private record GroupKey(boolean individual, String folder) {}

    private static final Comparator<GroupKey> GROUP_ORDER =
            Comparator.<GroupKey>comparingInt(k -> k.individual() ? 1 : 0)
                    .thenComparing(GroupKey::folder);

    private void rebuildGroups() {
        if (model == null) {
            groups = List.of();
            return;
        }
        Map<GroupKey, List<StageRow>> byGroup = new TreeMap<>(GROUP_ORDER);
        for (Map.Entry<String, StageGraphModel.Node> e : model.nodes().entrySet()) {
            StageGraphModel.Node node = e.getValue();
            GroupKey key = new GroupKey(node.individual(),
                    StageManager.getStageFolder(node.stageId(), node.individual()));
            byGroup.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new StageRow(e.getKey(), node.stageId(), node.label()));
        }

        List<Group> out = new ArrayList<>();
        for (Map.Entry<GroupKey, List<StageRow>> e : byGroup.entrySet()) {
            List<StageRow> rows = e.getValue();
            rows.sort(Comparator.comparing((StageRow r) -> r.label().toLowerCase(Locale.ROOT))
                    .thenComparing(StageRow::stageId));
            out.add(new Group(headerLabel(e.getKey()), rows));
        }
        groups = out;
    }

    private static String headerLabel(GroupKey key) {
        String treeLabel = Component.translatable(key.individual()
                ? "editor.historystages.folder.root_individual"
                : "editor.historystages.folder.root_global").getString();
        return key.folder().isEmpty() ? treeLabel : treeLabel + FOLDER_SEPARATOR + key.folder();
    }

    /** Applies the search filter and lays out the surviving rows top-to-bottom. */
    private void rebuildPositions() {
        String query = searchBar.getText().trim().toLowerCase(Locale.ROOT);
        List<PositionedRow> out = new ArrayList<>();
        int cursor = 0;
        for (Group group : groups) {
            List<StageRow> matches;
            if (query.isEmpty()) {
                matches = group.rows();
            } else {
                matches = new ArrayList<>();
                for (StageRow r : group.rows()) {
                    if (r.stageId().toLowerCase(Locale.ROOT).contains(query)
                            || r.label().toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(r);
                    }
                }
            }
            if (matches.isEmpty()) continue;

            out.add(PositionedRow.header(group.header(), cursor));
            cursor += FOLDER_HEADER_HEIGHT;
            for (StageRow r : matches) {
                out.add(PositionedRow.stage(r, cursor));
                cursor += STAGE_ROW_HEIGHT;
            }
        }
        positioned = out;
        maxScroll = Math.max(0, cursor - listAreaHeight());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    // --- Layout -----------------------------------------------------------------------------

    private int listTop() {
        return y + PADDING + SearchBar.HEIGHT + SEARCH_GAP;
    }

    private int listBottom() {
        return y + height - PADDING;
    }

    private int listAreaHeight() {
        return Math.max(0, listBottom() - listTop());
    }

    // --- Rendering --------------------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (width <= 0 || height <= 0) return;

        g.fill(x, y, x + width, y + height, BG_COLOR);
        g.fill(x + width - 1, y, x + width, y + height, EDGE_COLOR);

        searchBar.setPosition(x + PADDING, y + PADDING, width - PADDING * 2);
        searchBar.render(g, font, mouseX, mouseY);

        int top = listTop();
        int bottom = listBottom();
        if (bottom <= top) return;

        g.enableScissor(x, top, x + width, bottom);
        String focused = canvas.focusedKey();
        boolean animate = GraphConfig.GRAPH.animations.get();
        int scrollPx = Math.round(scroll);

        for (PositionedRow pr : positioned) {
            int screenTop = top - scrollPx + pr.top();
            int screenBottom = screenTop + pr.height();
            if (screenBottom < top || screenTop > bottom) continue;

            if (pr.isHeader()) {
                drawHeader(g, font, pr.headerText(), screenTop);
            } else {
                drawStageRow(g, font, pr.stage(), screenTop, mouseX, mouseY, top, bottom,
                        pr.stage().graphKey().equals(focused), animate);
            }
        }
        g.disableScissor();
    }

    private void drawHeader(GuiGraphics g, Font font, String text, int top) {
        g.drawString(font, text, x + ROW_LEFT_PAD, top + 4, HEADER_TEXT_COLOR, false);
    }

    private void drawStageRow(GuiGraphics g, Font font, StageRow row, int top, int mouseX, int mouseY,
                              int clipTop, int clipBottom, boolean selected, boolean animate) {
        int bottom = top + STAGE_ROW_HEIGHT;
        boolean hovered = mouseX >= x && mouseX < x + width
                && mouseY >= Math.max(top, clipTop) && mouseY < Math.min(bottom, clipBottom);

        Anim anim = hoverAnim.computeIfAbsent(row.graphKey(), k -> new Anim());
        float hover = animate
                ? Ease.outCubic(anim.ramp(hovered || selected, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS))
                : (hovered || selected ? 1f : 0f);

        if (hover > 0.01f) {
            g.fill(x, top, x + width, bottom, Fade.rgba(0x2A2A2E, 0.6f * hover));
            g.fill(x, top, x + 2, bottom, Fade.rgba(ACCENT_COLOR, hover));
        }

        int textColor = selected ? 0xFFFFCC00 : Fade.mix(ROW_TEXT_COLOR, ROW_TEXT_COLOR_HOVER, hover);
        String label = row.label() == null || row.label().isEmpty() ? row.stageId() : row.label();
        g.drawString(font, label, x + ROW_LEFT_PAD + 2, top + 5, textColor, false);
    }

    // --- Input --------------------------------------------------------------------------------

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !within(mx, my)) return false;
        if (searchBar.mouseClicked(mx, my)) return true;

        int top = listTop();
        int bottom = listBottom();
        if (my < top || my >= bottom) return false;

        double rel = my - top + scroll;
        for (PositionedRow pr : positioned) {
            if (pr.isHeader()) continue;
            if (rel >= pr.top() && rel < pr.top() + pr.height()) {
                canvas.focusOn(pr.stage().graphKey());
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!within(mx, my)) return false;
        scroll = (float) Math.max(0, Math.min(maxScroll, scroll - delta * STAGE_ROW_HEIGHT));
        return true;
    }

    public boolean charTyped(char c) {
        return searchBar.charTyped(c);
    }

    public boolean keyPressed(int keyCode) {
        return searchBar.keyPressed(keyCode);
    }

    private boolean within(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
