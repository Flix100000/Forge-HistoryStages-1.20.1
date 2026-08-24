package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.MarqueeText;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Searchable stage tree down the left side of the stage graph screen, collapsible to a rail.
 *
 * <p>Folders exist here and nowhere else in this feature: they organise this tree and nothing
 * more. They are never drawn on {@link GraphCanvas}, never tint a node, and picking a stage does
 * not move or hide anything — a click only calls {@link GraphCanvas#focusOn(String)}. The layout
 * is ordered by dependency depth, and a pack whose folders cut across that ordering cannot have
 * both; this was a deliberate trade made when the sidebar was designed.
 *
 * <p>Row geometry is computed once per rebuild into {@link #positioned}; both {@link #render} and
 * {@link #mouseClicked} read that same list, so a click can never land on a different row than
 * the one drawn under the cursor — the convention {@code StageOverviewScreen} uses for its own
 * list ({@code ListLayout} / {@code rowTop}).
 *
 * <p>The collapse animation is the one piece of state the owning screen has to cooperate with:
 * the rendered width changes every frame while it runs, and the canvas viewport has to follow it.
 * See {@link #advanceWidth()}.
 */
public final class GraphSidebar {

    private static final int STAGE_ROW_HEIGHT = 16;
    private static final int FOLDER_ROW_HEIGHT = 16;
    private static final int SECTION_HEADER_HEIGHT = 14;
    private static final int PADDING = 6;
    private static final int SEARCH_GAP = 6;
    private static final int ROW_LEFT_PAD = 6;
    /** Horizontal step per tree level. */
    private static final int INDENT_W = 8;

    /** Width left standing when collapsed — just enough for the chevron on the handle. */
    private static final int COLLAPSED_W = 12;
    private static final int HANDLE_W = 8;
    private static final int HANDLE_H = 24;
    /** Fixed column on the right holding the per-stage style dot, so it never wanders. */
    private static final int DOT_COLUMN_W = 7;

    private static final int BG_COLOR = 0xFF17171A;
    private static final int EDGE_COLOR = 0x33FFFFFF;
    private static final int SECTION_TEXT_COLOR = 0xFF8A8A8F;
    private static final int FOLDER_TEXT_COLOR = 0xFFBBBBBB;
    private static final int COUNT_TEXT_COLOR = 0xFF666666;
    private static final int ROW_TEXT_COLOR = 0xFFDDDDDD;
    private static final int ROW_TEXT_COLOR_HOVER = 0xFFFFFFFF;
    private static final int EMPTY_TEXT_COLOR = 0xFF777777;
    private static final int SEPARATOR_COLOR = 0x22FFFFFF;
    private static final int GUIDE_COLOR = 0x1AFFFFFF;
    private static final int HANDLE_BG = 0xFF222226;
    private static final int HANDLE_BG_HOVER = 0xFF2E2E33;
    private static final int HANDLE_GLYPH_COLOR = 0xFF999999;
    private static final int ROW_FILL_COLOR = 0x2A2A2E;
    private static final int ACCENT_COLOR = 0xFFCC00;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFCC00;

    private final GraphCanvas canvas;
    private final SearchBar searchBar =
            new SearchBar(Component.translatable("editor.historystages.search").getString());
    private final MarqueeText marquee = new MarqueeText();
    private final Scrollbar scrollbar = new Scrollbar();

    /** Told about a stage picked in the list, so the screen can open the detail panel on it. */
    private Consumer<String> onSelect;

    private StageGraphModel model;
    /** Width the sidebar occupies when fully open; {@link #width} is the animated value. */
    private int fullWidth;
    private int x, y, width, height;
    private float scroll;
    private float maxScroll;

    private boolean open;
    private final Anim openAnim;

    /** One stage, flattened out of the tree once its folder is known. */
    private record StageRow(String graphKey, String stageId, String label, boolean individual) {}

    /**
     * A folder and everything below it. Children are sorted case-insensitively by name so the
     * tree reads the same regardless of how the files happened to land on disk.
     */
    private static final class FolderNode {
        private final String path;
        private final String name;
        private final Map<String, FolderNode> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final List<StageRow> stages = new ArrayList<>();
        /** Stages below this folder including every nested one — see {@link #computeTotals}. */
        private int total;

        private FolderNode(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    /** One of the two trees, with the header row shown above it. */
    private record Section(boolean individual, String title, FolderNode root) {}

    private enum Kind { SECTION, FOLDER, STAGE }

    /**
     * A single drawable/clickable row with its content-space top offset already resolved.
     * Rebuilt whenever the model or the search text changes ({@link #rebuildPositions}); render
     * and hit testing both walk this exact list so they can never disagree about where a row is.
     */
    private record PositionedRow(Kind kind, String text, String key, StageRow stage,
                                 int depth, int count, int top, int height) {}

    private List<Section> sections = List.of();
    private List<PositionedRow> positioned = List.of();
    private final Map<String, Anim> hoverAnim = new HashMap<>();

    /**
     * Keys of the sections and folders currently expanded. An instance field rather than a
     * static one: it survives a re-init of {@code StageGraphScreen} (a Re-arrange or freeze
     * confirmation swaps the screen and comes back to the same sidebar) but resets when the
     * screen is opened afresh, which is the point at which a stale tree state would confuse.
     */
    private final Set<String> expanded = new HashSet<>();
    /** Guards the one-time seeding of {@link #expanded} in {@link #rebuildTree}. */
    private boolean treeInitialised;
    /** True while the search box has text: the tree is force-expanded and {@link #expanded} rests. */
    private boolean searching;
    /** Running total while {@link #layoutFolder} walks the tree. */
    private int layoutCursor;

    public GraphSidebar(GraphCanvas canvas) {
        this.canvas = canvas;
        this.open = GraphConfig.GRAPH.sidebarOpen.get();
        this.openAnim = new Anim(open ? 1.0f : 0.0f);
        searchBar.onChange(t -> rebuildPositions());
    }

    /** Rebuilds the tree from scratch. Cheap enough to call on every model refresh. */
    public void setModel(StageGraphModel model) {
        this.model = model;
        rebuildTree();
        revealFocused();
        rebuildPositions();
    }

    /** Sets the position and the <em>open</em> width; the drawn width follows the animation. */
    public void setBounds(int x, int y, int fullWidth, int height) {
        this.x = x;
        this.y = y;
        this.fullWidth = fullWidth;
        this.height = height;
        this.width = renderWidth();
        rebuildPositions(); // list height changed, so max scroll must be re-clamped
    }

    /**
     * Advances the collapse animation by one frame and returns the width the sidebar will draw
     * itself at. The owning screen calls this before positioning the canvas, so the viewport
     * tracks the sidebar edge instead of jumping when the animation ends.
     *
     * <p>Safe to drive {@code GraphCanvas.setBounds} with: that method only moves the viewport
     * and leaves pan and zoom alone, so the map stays put on screen and simply reveals more
     * area on the left.
     */
    public int advanceWidth() {
        if (GraphConfig.GRAPH.animations.get()) {
            openAnim.ramp(open, Timing.REVEAL_MS, Timing.REVEAL_MS);
        } else {
            openAnim.set(open ? 1.0f : 0.0f);
        }
        this.width = renderWidth();
        return this.width;
    }

    public int currentWidth() {
        return renderWidth();
    }

    private int renderWidth() {
        if (fullWidth <= COLLAPSED_W) return fullWidth;
        float t = Ease.outCubic(openAnim.value());
        return Math.round(COLLAPSED_W + (fullWidth - COLLAPSED_W) * t);
    }

    /** Told about a stage picked in the list; see {@link #selectStage}. */
    public void setSelectHandler(Consumer<String> handler) {
        this.onSelect = handler;
    }

    /** True once the body is wide enough for the search bar and the list to be worth drawing. */
    private boolean bodyVisible() {
        return width > COLLAPSED_W + PADDING * 2;
    }

    public boolean isSearchFocused() {
        return bodyVisible() && searchBar.isFocused();
    }

    // --- Tree ---------------------------------------------------------------------------------

    private void rebuildTree() {
        if (model == null) {
            sections = List.of();
            return;
        }
        FolderNode global = new FolderNode("", "");
        FolderNode individual = new FolderNode("", "");

        for (Map.Entry<String, StageGraphModel.Node> e : model.nodes().entrySet()) {
            StageGraphModel.Node node = e.getValue();
            FolderNode root = node.individual() ? individual : global;
            String folder = StageManager.getStageFolder(node.stageId(), node.individual());
            resolve(root, folder).stages.add(
                    new StageRow(e.getKey(), node.stageId(), node.label(), node.individual()));
        }

        sortStages(global);
        sortStages(individual);
        computeTotals(global);
        computeTotals(individual);

        List<Section> out = new ArrayList<>();
        if (global.total > 0) {
            out.add(new Section(false, tr("editor.historystages.folder.root_global"), global));
        }
        if (individual.total > 0) {
            out.add(new Section(true, tr("editor.historystages.folder.root_individual"), individual));
        }
        sections = out;

        // Both trees start open; their folders start closed. Only on the very first build —
        // rebuildTree also runs whenever an unlock lands while the graph is open, and re-adding
        // the section keys there would tear a hand-collapsed section back open under the player.
        if (!treeInitialised) {
            treeInitialised = true;
            for (Section section : out) {
                expanded.add(sectionKey(section));
            }
        }
    }

    /** Walks (creating as it goes) to the folder {@code path} names, relative to {@code root}. */
    private static FolderNode resolve(FolderNode root, String path) {
        if (path == null || path.isEmpty()) return root;
        FolderNode current = root;
        StringBuilder built = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            if (built.length() > 0) built.append('/');
            built.append(segment);
            String full = built.toString();
            current = current.children.computeIfAbsent(segment, s -> new FolderNode(full, s));
        }
        return current;
    }

    private static void sortStages(FolderNode folder) {
        folder.stages.sort(Comparator.comparing((StageRow r) -> displayLabel(r).toLowerCase(Locale.ROOT))
                .thenComparing(StageRow::stageId));
        for (FolderNode child : folder.children.values()) sortStages(child);
    }

    /**
     * Counts stages below each folder, nested ones included. A folder holding nothing but
     * subfolders would otherwise show a zero and read as empty.
     */
    private static int computeTotals(FolderNode folder) {
        int sum = folder.stages.size();
        for (FolderNode child : folder.children.values()) sum += computeTotals(child);
        folder.total = sum;
        return sum;
    }

    /** Opens the path down to whatever the canvas is focused on, so the tree opens on it. */
    private void revealFocused() {
        String focused = canvas.focusedKey();
        if (focused == null || model == null) return;
        StageGraphModel.Node node = model.nodes().get(focused);
        if (node == null) return;
        expandAncestors(node.individual(), StageManager.getStageFolder(node.stageId(), node.individual()));
    }

    private void expandAncestors(boolean individual, String path) {
        if (path == null || path.isEmpty()) return;
        StringBuilder built = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            if (built.length() > 0) built.append('/');
            built.append(segment);
            expanded.add(folderKey(individual, built.toString()));
        }
    }

    private static String sectionKey(Section section) {
        return section.individual() ? "section:individual" : "section:global";
    }

    private static String folderKey(boolean individual, String path) {
        return (individual ? "i:" : "g:") + path;
    }

    // --- Layout -------------------------------------------------------------------------------

    /** Applies the search filter and lays out the surviving rows top-to-bottom. */
    private void rebuildPositions() {
        String query = searchBar.getText().trim().toLowerCase(Locale.ROOT);
        searching = !query.isEmpty();

        List<PositionedRow> out = new ArrayList<>();
        layoutCursor = 0;
        for (Section section : sections) {
            if (searching && !containsMatch(section.root(), query)) continue;
            String key = sectionKey(section);
            out.add(new PositionedRow(Kind.SECTION, section.title(), key, null, 0,
                    section.root().total, layoutCursor, SECTION_HEADER_HEIGHT));
            layoutCursor += SECTION_HEADER_HEIGHT;
            if (searching || expanded.contains(key)) {
                layoutFolder(out, section.individual(), section.root(), query, 0);
            }
        }
        positioned = out;
        maxScroll = Math.max(0, layoutCursor - listAreaHeight());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void layoutFolder(List<PositionedRow> out, boolean individual, FolderNode folder,
                              String query, int depth) {
        for (FolderNode child : folder.children.values()) {
            if (searching && !containsMatch(child, query)) continue;
            String key = folderKey(individual, child.path);
            out.add(new PositionedRow(Kind.FOLDER, child.name, key, null, depth, child.total,
                    layoutCursor, FOLDER_ROW_HEIGHT));
            layoutCursor += FOLDER_ROW_HEIGHT;
            if (searching || expanded.contains(key)) {
                layoutFolder(out, individual, child, query, depth + 1);
            }
        }
        for (StageRow row : folder.stages) {
            if (searching && !rowMatches(row, query)) continue;
            out.add(new PositionedRow(Kind.STAGE, null, row.graphKey(), row, depth, 0,
                    layoutCursor, STAGE_ROW_HEIGHT));
            layoutCursor += STAGE_ROW_HEIGHT;
        }
    }

    private static boolean containsMatch(FolderNode folder, String query) {
        for (StageRow row : folder.stages) {
            if (rowMatches(row, query)) return true;
        }
        for (FolderNode child : folder.children.values()) {
            if (containsMatch(child, query)) return true;
        }
        return false;
    }

    private static boolean rowMatches(StageRow row, String query) {
        return row.stageId().toLowerCase(Locale.ROOT).contains(query)
                || row.label().toLowerCase(Locale.ROOT).contains(query);
    }

    private int listTop() {
        return y + PADDING + SearchBar.HEIGHT + SEARCH_GAP;
    }

    private int listBottom() {
        return y + height - PADDING;
    }

    private int listAreaHeight() {
        return Math.max(0, listBottom() - listTop());
    }

    /** Right edge available to row text, kept clear of the style dot and the scrollbar. */
    private int textRight() {
        return x + width - DOT_COLUMN_W - Scrollbar.WIDTH - 2;
    }

    // --- Rendering ----------------------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (width <= 0 || height <= 0) return;

        g.fill(x, y, x + width, y + height, BG_COLOR);
        g.fill(x + width - 1, y, x + width, y + height, EDGE_COLOR);

        boolean animate = GraphConfig.GRAPH.animations.get();
        if (bodyVisible()) {
            renderBody(g, font, mouseX, mouseY, animate);
        }
        renderHandle(g, font, mouseX, mouseY);
    }

    private void renderBody(GuiGraphics g, Font font, int mouseX, int mouseY, boolean animate) {
        searchBar.setPosition(x + PADDING, y + PADDING, width - PADDING * 2);
        searchBar.render(g, font, mouseX, mouseY);

        int top = listTop();
        int bottom = listBottom();
        if (bottom <= top) return;

        if (positioned.isEmpty()) {
            String text = tr("editor.historystages.graph.sidebar.empty");
            g.drawString(font, MarqueeText.truncate(font, text, width - PADDING * 2),
                    x + PADDING, top + 6, EMPTY_TEXT_COLOR, false);
            return;
        }

        g.enableScissor(x, top, x + width, bottom);
        String focused = canvas.focusedKey();
        int scrollPx = Math.round(scroll);

        // The section header that has scrolled past the top of the list, and the one after it:
        // together they decide where the pinned copy sits, so a header is pushed up by the next
        // section rather than overlapping it.
        PositionedRow sticky = null;
        int nextSectionTop = Integer.MAX_VALUE;

        for (PositionedRow row : positioned) {
            int screenTop = top - scrollPx + row.top();
            int screenBottom = screenTop + row.height();

            if (row.kind() == Kind.SECTION) {
                if (screenTop <= top) {
                    sticky = row;
                } else if (nextSectionTop == Integer.MAX_VALUE) {
                    nextSectionTop = screenTop;
                }
            }
            if (screenBottom < top || screenTop > bottom) continue;

            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= Math.max(screenTop, top) && mouseY < Math.min(screenBottom, bottom);
            drawRow(g, font, row, screenTop, hovered, focused, animate, top, bottom);
        }

        if (sticky != null) {
            int pinned = Math.min(top, nextSectionTop - SECTION_HEADER_HEIGHT);
            g.fill(x, pinned, x + width, pinned + SECTION_HEADER_HEIGHT, BG_COLOR);
            drawSection(g, font, sticky, pinned);
        }
        g.disableScissor();

        scrollbar.render(g, x + width - Scrollbar.WIDTH - 1, top, bottom,
                scroll, maxScroll, mouseX, mouseY);
    }

    private void drawRow(GuiGraphics g, Font font, PositionedRow row, int top, boolean hovered,
                         String focused, boolean animate, int clipTop, int clipBottom) {
        switch (row.kind()) {
            case SECTION -> drawSection(g, font, row, top);
            case FOLDER -> drawFolder(g, font, row, top, hovered, animate);
            case STAGE -> drawStage(g, font, row, top, hovered,
                    row.key().equals(focused), animate, clipTop, clipBottom);
        }
    }

    private void drawSection(GuiGraphics g, Font font, PositionedRow row, int top) {
        int textY = top + 3;
        drawCount(g, font, row.count(), textY);
        int available = textRight() - font.width(String.valueOf(row.count())) - 6 - (x + ROW_LEFT_PAD);
        g.drawString(font, MarqueeText.truncate(font, row.text(), available),
                x + ROW_LEFT_PAD, textY, SECTION_TEXT_COLOR, false);
        g.fill(x + ROW_LEFT_PAD, top + SECTION_HEADER_HEIGHT - 1,
                textRight(), top + SECTION_HEADER_HEIGHT, SEPARATOR_COLOR);
    }

    private void drawFolder(GuiGraphics g, Font font, PositionedRow row, int top,
                            boolean hovered, boolean animate) {
        float hover = hoverProgress(row.key(), hovered, animate);
        if (hover > 0.01f) {
            g.fill(x, top, x + width, top + FOLDER_ROW_HEIGHT, Fade.rgba(ROW_FILL_COLOR, 0.4f * hover));
        }
        drawGuides(g, row.depth(), top, FOLDER_ROW_HEIGHT);

        int left = x + ROW_LEFT_PAD + row.depth() * INDENT_W;
        String chevron = isExpanded(row.key()) ? "▼ " : "▶ ";
        int textY = top + 4;
        g.drawString(font, chevron, left, textY, COUNT_TEXT_COLOR, false);

        drawCount(g, font, row.count(), textY);
        int nameLeft = left + font.width(chevron);
        int available = textRight() - font.width(String.valueOf(row.count())) - 6 - nameLeft;
        g.drawString(font, MarqueeText.truncate(font, row.text(), available), nameLeft, textY,
                Fade.mix(FOLDER_TEXT_COLOR, ROW_TEXT_COLOR_HOVER, hover), false);
    }

    private void drawStage(GuiGraphics g, Font font, PositionedRow row, int top, boolean hovered,
                           boolean selected, boolean animate, int clipTop, int clipBottom) {
        int bottom = top + STAGE_ROW_HEIGHT;
        float hover = hoverProgress(row.key(), hovered, animate);

        // Selection is a state, hover is a gesture: the selected row keeps a full fill and an
        // accent bar for as long as it is selected, while hover only tints. Before this they
        // were drawn identically and the picked stage was impossible to find again.
        if (selected) {
            g.fill(x, top, x + width, bottom, Fade.rgba(ROW_FILL_COLOR, 0.85f));
            g.fill(x, top, x + 2, bottom, 0xFF000000 | ACCENT_COLOR);
        } else if (hover > 0.01f) {
            g.fill(x, top, x + width, bottom, Fade.rgba(ROW_FILL_COLOR, 0.45f * hover));
        }
        drawGuides(g, row.depth(), top, STAGE_ROW_HEIGHT);

        StageRow stage = row.stage();
        String label = displayLabel(stage);
        int left = x + ROW_LEFT_PAD + row.depth() * INDENT_W + 2;
        int color = selected ? SELECTED_TEXT_COLOR : Fade.mix(ROW_TEXT_COLOR, ROW_TEXT_COLOR_HOVER, hover);
        marquee.draw(g, font, row.key(), label, left, top + 4, Math.max(0, textRight() - left),
                Math.max(top, clipTop), Math.min(bottom, clipBottom), color, hovered, animate);

        // Which stages deviate from the graph settings at all. Without this the only way to find
        // out in an eighty-stage pack is to open graph_stages.json.
        GraphStageData.Entry entry = GraphStageData.get().tree(stage.individual()).get(stage.stageId());
        if (entry != null && entry.hasStyles()) {
            int dotX = x + width - Scrollbar.WIDTH - DOT_COLUMN_W + 1;
            g.fill(dotX, top + 6, dotX + 3, top + 9, 0xFF000000 | ACCENT_COLOR);
        }
    }

    /** One faint vertical line per level above this row, so deep nesting stays readable. */
    private void drawGuides(GuiGraphics g, int depth, int top, int rowHeight) {
        for (int level = 1; level <= depth; level++) {
            int gx = x + ROW_LEFT_PAD + level * INDENT_W - 3;
            g.fill(gx, top, gx + 1, top + rowHeight, GUIDE_COLOR);
        }
    }

    private void drawCount(GuiGraphics g, Font font, int count, int textY) {
        String text = String.valueOf(count);
        g.drawString(font, text, textRight() - font.width(text), textY, COUNT_TEXT_COLOR, false);
    }

    private float hoverProgress(String key, boolean hovered, boolean animate) {
        if (!animate) return hovered ? 1.0f : 0.0f;
        Anim anim = hoverAnim.computeIfAbsent(key, k -> new Anim());
        return Ease.outCubic(anim.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
    }

    /**
     * The grab handle, at the same place on the right edge whether open or collapsed.
     *
     * <p>It sits <em>outside</em> the sidebar, protruding onto the canvas, rather than inside it:
     * within the sidebar it would carve a dead strip out of every row it crossed, and hovering a
     * row near the edge would light the handle instead.
     */
    private void renderHandle(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int hx = x + width;
        int hy = y + (height - HANDLE_H) / 2;
        boolean hovered = overHandle(mouseX, mouseY);

        g.fill(hx, hy, hx + HANDLE_W, hy + HANDLE_H, hovered ? HANDLE_BG_HOVER : HANDLE_BG);
        String glyph = open ? "◀" : "▶";
        g.drawString(font, glyph, hx + (HANDLE_W - font.width(glyph)) / 2,
                hy + (HANDLE_H - font.lineHeight) / 2, HANDLE_GLYPH_COLOR, false);
    }

    private boolean overHandle(double mx, double my) {
        int hx = x + width;
        int hy = y + (height - HANDLE_H) / 2;
        return mx >= hx && mx < hx + HANDLE_W && my >= hy && my < hy + HANDLE_H;
    }

    private boolean isExpanded(String key) {
        return searching || expanded.contains(key);
    }

    private static String displayLabel(StageRow row) {
        return row.label() == null || row.label().isEmpty() ? row.stageId() : row.label();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    // --- Input --------------------------------------------------------------------------------

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        // Checked before the bounds test: the handle deliberately sticks out past the sidebar's
        // right edge, so it is not inside within().
        if (overHandle(mx, my)) {
            toggleOpen();
            return true;
        }
        if (!within(mx, my)) return false;

        // Ahead of the rows: the bar overlaps the right end of every one of them.
        if (bodyVisible() && scrollbar.mouseClicked(mx, my)) {
            scroll = scrollbar.scrollFor(my);
            return true;
        }

        if (!bodyVisible()) {
            // Anywhere on the collapsed rail brings it back, so a player who has not spotted the
            // handle still gets out of the state they clicked themselves into.
            toggleOpen();
            return true;
        }
        if (searchBar.mouseClicked(mx, my)) return true;

        int top = listTop();
        int bottom = listBottom();
        if (my < top || my >= bottom) return true;

        double rel = my - top + scroll;
        for (PositionedRow row : positioned) {
            if (rel < row.top() || rel >= row.top() + row.height()) continue;
            switch (row.kind()) {
                case SECTION, FOLDER -> toggleExpanded(row.key());
                case STAGE -> selectStage(row.key());
            }
            return true;
        }
        return true;
    }

    /**
     * Centres the map on a stage and hands it to whoever owns the selection.
     *
     * <p>Focusing alone is not enough: {@code StageGraphScreen} drops the focus ring on any frame
     * where the detail panel is closed, so a click in this list used to centre the map and then
     * lose its own highlight one frame later.
     */
    private void selectStage(String graphKey) {
        canvas.focusOn(graphKey);
        if (onSelect != null) onSelect.accept(graphKey);
    }

    private void toggleOpen() {
        open = !open;
        searchBar.setFocused(false);
    }

    /**
     * Toggling while a search is running would edit a set nothing is reading — the tree is
     * force-expanded until the box is cleared — and would land the player on a surprise state
     * afterwards, so it is ignored.
     */
    private void toggleExpanded(String key) {
        if (searching) return;
        if (!expanded.remove(key)) expanded.add(key);
        rebuildPositions();
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!within(mx, my) || !bodyVisible()) return false;
        scroll = (float) Math.max(0, Math.min(maxScroll, scroll - delta * STAGE_ROW_HEIGHT));
        return true;
    }

    /** Continues a scrollbar drag. The owning screen has to forward its drags here. */
    public boolean mouseDragged(double my) {
        if (!scrollbar.isDragging()) return false;
        scroll = scrollbar.scrollFor(my);
        return true;
    }

    public void mouseReleased() {
        scrollbar.mouseReleased();
    }

    public boolean charTyped(char c) {
        return bodyVisible() && searchBar.charTyped(c);
    }

    public boolean keyPressed(int keyCode) {
        return bodyVisible() && searchBar.keyPressed(keyCode);
    }

    private boolean within(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
