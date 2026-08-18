package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.RequestStageDependencyPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The right-hand column of the stage graph screen: everything known about the node last clicked
 * on {@link GraphCanvas}. Closed (drawing nothing) until {@link #select} is called with a key
 * that resolves in the current {@link StageGraphModel}.
 *
 * <p>Every requirement section renders {@link DependencyResult} / {@link DependencyResult.EntryResult}
 * as received from {@link ClientDependencyCache} — it never re-derives fulfilment from
 * {@code DependencyGroup} itself. When {@link #select} lands on a key the cache has nothing for,
 * it fires a {@link RequestStageDependencyPacket} (a Research Pedestal is not required) and shows
 * the "no dependency data" hint as a loading state until the reply lands. At most one request per
 * stage is sent per screen-open — see {@link #pendingDependencyRequests}.
 *
 * <p>Content rows are rebuilt on every {@link #render} call rather than cached behind a dirty
 * flag: the dependency cache can change asynchronously (a packet arriving while the panel is
 * open), and the rebuild is a handful of small objects, cheap enough that recomputing beats
 * risking stale text.
 */
public final class GraphDetailPanel {

    private static final int PADDING = 8;
    private static final int LINE_H = 10;
    private static final int HEADER_H = 14;
    private static final int SPACER_H = 6;
    private static final int CLOSE_SIZE = 12;
    private static final int HEADER_CHROME_H = 46;

    private static final int BG_COLOR = 0xFF17171A;
    private static final int EDGE_COLOR = 0x33FFFFFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int STAGE_ID_COLOR = 0xFF888888;
    private static final int SECTION_COLOR = 0xFFFFCC00;
    private static final int TEXT_COLOR = 0xFFDDDDDD;
    private static final int HINT_COLOR = 0xFFAAAAAA;
    private static final int CLOSE_COLOR = 0xFF999999;
    private static final int CLOSE_COLOR_HOVER = 0xFFFFFFFF;

    private static final int STATE_UNLOCKED_COLOR = 0xFF44CC99;
    private static final int STATE_REACHABLE_COLOR = 0xFFDDBB44;
    private static final int STATE_LOCKED_COLOR = 0xFF999999;

    /** One drawable content line; either already-wrapped rich text or a plain formatted string. */
    private interface ContentRow {
        int height();
    }

    private record StringRow(String text, int color, int height) implements ContentRow {}

    private record WrappedRow(FormattedCharSequence text, int color, int height) implements ContentRow {}

    private StageGraphModel model;
    private String selectedKey;

    private int x, y, width, height;
    private float scroll;
    private float maxScroll;

    private List<ContentRow> content = List.of();

    /**
     * Graph keys already asked for via {@link RequestStageDependencyPacket} in this panel's
     * lifetime (one per {@link net.bananemdnsa.historystages.client.editor.StageGraphScreen}
     * open — the panel is constructed once per screen and reused across re-inits, e.g. a
     * Re-arrange confirmation). Prevents re-sending a request while its reply is still in
     * flight; once the reply lands the cache is non-null and {@link #maybeRequestDependencyData}
     * short-circuits before consulting this set at all.
     */
    private final Set<String> pendingDependencyRequests = new HashSet<>();

    public GraphDetailPanel(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setModel(StageGraphModel model) {
        this.model = model;
    }

    /** Opens the panel on {@code graphKey}, or closes it when passed null. */
    public void select(String graphKey) {
        this.selectedKey = graphKey;
        this.scroll = 0;
        maybeRequestDependencyData();
    }

    /**
     * Fires a pedestal-free dependency request for the selected node when the cache has nothing
     * for it yet and one has not already been sent this screen-open.
     */
    private void maybeRequestDependencyData() {
        if (selectedKey == null || model == null) return;
        StageGraphModel.Node node = model.nodes().get(selectedKey);
        if (node == null) return;
        if (ClientDependencyCache.get(node.stageId(), node.individual()) != null) return;
        if (!pendingDependencyRequests.add(selectedKey)) return;
        PacketHandler.sendToServer(new RequestStageDependencyPacket(node.stageId(), node.individual()));
    }

    public String selectedKey() {
        return selectedKey;
    }

    public boolean isOpen() {
        return selectedKey != null && model != null && model.nodes().containsKey(selectedKey);
    }

    // --- Rendering ----------------------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!isOpen() || width <= 0 || height <= 0) return;
        StageGraphModel.Node node = model.nodes().get(selectedKey);
        if (node == null) return;

        g.fill(x, y, x + width, y + height, BG_COLOR);
        g.fill(x, y, x + 1, y + height, EDGE_COLOR);

        renderHeader(g, font, node, mouseX, mouseY);

        int contentWidth = Math.max(1, width - PADDING * 2);
        rebuildContent(font, node, contentWidth);

        int top = y + HEADER_CHROME_H;
        int bottom = y + height - PADDING;
        if (bottom <= top) return;

        maxScroll = Math.max(0, totalHeight() - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(x, top, x + width, bottom);
        int cy = top - Math.round(scroll);
        int left = x + PADDING;
        for (ContentRow row : content) {
            int rh = row.height();
            if (cy + rh >= top && cy <= bottom) {
                drawRow(g, font, row, left, cy);
            }
            cy += rh;
        }
        g.disableScissor();
    }

    private int totalHeight() {
        int h = 0;
        for (ContentRow row : content) h += row.height();
        return h;
    }

    private static void drawRow(GuiGraphics g, Font font, ContentRow row, int x, int y) {
        if (row instanceof StringRow r) {
            if (!r.text().isEmpty()) g.drawString(font, r.text(), x, y, r.color(), false);
        } else if (row instanceof WrappedRow r) {
            g.drawString(font, r.text(), x, y, r.color(), false);
        }
    }

    private void renderHeader(GuiGraphics g, Font font, StageGraphModel.Node node, int mouseX, int mouseY) {
        int hx = x + PADDING;
        int hy = y + PADDING;

        String title = node.label() == null || node.label().isEmpty() ? node.stageId() : node.label();
        g.drawString(font, title, hx, hy, TITLE_COLOR, false);

        String stateKey = switch (node.state()) {
            case UNLOCKED -> "editor.historystages.graph.state.unlocked";
            case REACHABLE -> "editor.historystages.graph.state.reachable";
            case LOCKED -> "editor.historystages.graph.state.locked";
        };
        int stateColor = switch (node.state()) {
            case UNLOCKED -> STATE_UNLOCKED_COLOR;
            case REACHABLE -> STATE_REACHABLE_COLOR;
            case LOCKED -> STATE_LOCKED_COLOR;
        };
        g.drawString(font, Component.translatable(stateKey).getString(), hx, hy + 11, stateColor, false);
        g.drawString(font, node.stageId(), hx, hy + 22, STAGE_ID_COLOR, false);

        int bx = x + width - PADDING - CLOSE_SIZE;
        int by = y + PADDING;
        boolean hoveredClose = mouseX >= bx && mouseX < bx + CLOSE_SIZE
                && mouseY >= by && mouseY < by + CLOSE_SIZE;
        String glyph = "×"; // "×" — matches the close glyph used elsewhere in the editor
        g.drawString(font, glyph, bx + (CLOSE_SIZE - font.width(glyph)) / 2, by + 2,
                hoveredClose ? CLOSE_COLOR_HOVER : CLOSE_COLOR, false);
    }

    // --- Content assembly -----------------------------------------------------------------------

    private void rebuildContent(Font font, StageGraphModel.Node node, int width) {
        List<ContentRow> rows = new ArrayList<>();
        GraphConfig.Graph cfg = GraphConfig.GRAPH;

        if (cfg.showDescription.get()) {
            addDescription(rows, font, node, width);
        }

        DependencyResult dep = ClientDependencyCache.get(node.stageId(), node.individual());
        boolean anyRequirementSection = cfg.showStageDeps.get() || cfg.showItems.get() || cfg.showXp.get()
                || cfg.showAdvancements.get() || cfg.showKills.get() || cfg.showStats.get()
                || cfg.showScoreboard.get();
        if (anyRequirementSection && dep == null) {
            addHint(rows, font, "editor.historystages.graph.no_dependency_data", width);
        }

        addRequirementSection(rows, cfg.showStageDeps.get(), dep,
                "editor.historystages.graph.section.stage_deps", "stage", "individual_stage");
        addRequirementSection(rows, cfg.showItems.get(), dep,
                "editor.historystages.graph.section.items", "item");
        addRequirementSection(rows, cfg.showXp.get(), dep,
                "editor.historystages.graph.section.xp", "xp_level");
        addRequirementSection(rows, cfg.showAdvancements.get(), dep,
                "editor.historystages.graph.section.advancements", "advancement");
        addRequirementSection(rows, cfg.showKills.get(), dep,
                "editor.historystages.graph.section.kills", "entity_kill");
        addRequirementSection(rows, cfg.showStats.get(), dep,
                "editor.historystages.graph.section.stats", "stat");
        addRequirementSection(rows, cfg.showScoreboard.get(), dep,
                "editor.historystages.graph.section.scoreboard", "scoreboard");

        if (cfg.showTriggers.get()) {
            addTriggers(rows, node);
        }
        if (cfg.showUnlocks.get()) {
            addUnlocks(rows, node);
        }

        content = rows;
    }

    private void addDescription(List<ContentRow> rows, Font font, StageGraphModel.Node node, int width) {
        String raw = GraphStageData.get().description(node.stageId(), node.individual());
        if (raw == null) return;

        rows.add(header("editor.historystages.graph.section.description"));
        Component desc = describe(raw);
        for (FormattedCharSequence line : font.split(desc, width)) {
            rows.add(new WrappedRow(line, TEXT_COLOR, LINE_H));
        }
        rows.add(spacer());
    }

    /** Literal text, unless a translation exists for it. */
    private static Component describe(String raw) {
        return I18n.exists(raw) ? Component.translatable(raw) : Component.literal(raw);
    }

    private void addRequirementSection(List<ContentRow> rows, boolean enabled, DependencyResult dep,
                                       String headerKey, String... types) {
        if (!enabled || dep == null) return;

        Set<String> typeSet = Set.of(types);
        List<DependencyResult.EntryResult> matches = new ArrayList<>();
        for (DependencyResult.GroupResult group : dep.getGroups()) {
            for (DependencyResult.EntryResult e : group.getEntries()) {
                if (typeSet.contains(e.getType())) matches.add(e);
            }
        }
        if (matches.isEmpty()) return;

        rows.add(header(headerKey));
        for (DependencyResult.EntryResult e : matches) rows.add(requirementRow(e));
        rows.add(spacer());
    }

    private static ContentRow requirementRow(DependencyResult.EntryResult e) {
        boolean fulfilled = e.isFulfilled();
        StringBuilder text = new StringBuilder(fulfilled ? "§a✓ " : "§7○ ");
        text.append(e.getDescription());
        boolean showsProgress = "item".equals(e.getType()) || "xp_level".equals(e.getType());
        if (!fulfilled && showsProgress) {
            text.append(" §8(").append(e.getCurrent()).append('/').append(e.getRequired()).append(')');
        }
        return new StringRow(text.toString(), TEXT_COLOR, LINE_H);
    }

    private void addTriggers(List<ContentRow> rows, StageGraphModel.Node node) {
        StageEntry entry = node.individual()
                ? StageManager.getIndividualStages().get(node.stageId())
                : StageManager.getStages().get(node.stageId());
        if (entry == null || !entry.getMode().usesAutoTrigger()) return;

        AutoTrigger trigger = entry.getAutoTrigger();
        if (trigger == null || trigger.isEmpty()) return;

        rows.add(header("editor.historystages.graph.section.triggers"));
        String modeKey = trigger.resolvedMode() == CombineMode.ANY
                ? "editor.historystages.auto_trigger.combine.any"
                : "editor.historystages.auto_trigger.combine.all";
        rows.add(new StringRow("§7" + Component.translatable(modeKey).getString(), HINT_COLOR, LINE_H));
        for (TriggerCondition t : trigger.getTriggers()) {
            String line = Component.translatable(triggerTypeKey(t)).getString() + ": " + triggerValueText(t);
            rows.add(new StringRow(line, TEXT_COLOR, LINE_H));
        }
        rows.add(spacer());
    }

    private static String triggerTypeKey(TriggerCondition t) {
        return "editor.historystages.auto_trigger.type." + t.type();
    }

    /**
     * Mirrors {@code AutoTriggerEditorScreen.triggerValueText} — kept local rather than shared
     * because this is scoped to creating only {@code GraphSidebar} and {@code GraphDetailPanel};
     * factoring this into a shared utility is a reasonable follow-up cleanup.
     */
    private static String triggerValueText(TriggerCondition t) {
        if (t instanceof BiomeTrigger b) return b.id();
        if (t instanceof StructureTrigger s) return s.id();
        if (t instanceof DimensionTrigger d) return d.id();
        if (t instanceof ItemTrigger i) return i.id();
        if (t instanceof EntityTrigger e) {
            return e.id() + " ("
                    + Component.translatable("editor.historystages.auto_trigger.entity."
                            + e.resolvedSubMode().serialize()).getString()
                    + ")";
        }
        if (t instanceof BlockPlaceTrigger bp) return bp.id();
        if (t instanceof BlockBreakTrigger bb) return bb.id();
        if (t instanceof AdvancementTrigger a) return a.id();
        if (t instanceof PlaytimeTrigger p) {
            return Component.translatable("editor.historystages.auto_trigger.playtime.days", p.days()).getString();
        }
        return "";
    }

    /**
     * Stages whose dependencies reference this one — read directly off
     * {@link StageGraphModel#edges()} (this node as the prerequisite side) rather than walking
     * {@code DependencyGroup} again.
     */
    private void addUnlocks(List<ContentRow> rows, StageGraphModel.Node node) {
        String key = StageManager.graphKey(node.stageId(), node.individual());
        List<String> labels = new ArrayList<>();
        for (StageGraphModel.Edge edge : model.edges()) {
            if (!edge.fromKey().equals(key)) continue;
            StageGraphModel.Node target = model.nodes().get(edge.toKey());
            if (target != null) {
                String label = target.label() == null || target.label().isEmpty()
                        ? target.stageId() : target.label();
                labels.add(label);
            }
        }
        if (labels.isEmpty()) return;

        rows.add(header("editor.historystages.graph.section.unlocks"));
        for (String label : labels) rows.add(new StringRow("§7- " + label, TEXT_COLOR, LINE_H));
        rows.add(spacer());
    }

    private static ContentRow header(String key) {
        return new StringRow(Component.translatable(key).getString(), SECTION_COLOR, HEADER_H);
    }

    /** Wrapped like {@link #addDescription} — the hint text is long enough to need it. */
    private static void addHint(List<ContentRow> rows, Font font, String key, int width) {
        for (FormattedCharSequence line : font.split(Component.translatable(key), width)) {
            rows.add(new WrappedRow(line, HINT_COLOR, LINE_H));
        }
        rows.add(spacer());
    }

    private static ContentRow spacer() {
        return new StringRow("", 0, SPACER_H);
    }

    // --- Input --------------------------------------------------------------------------------

    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOpen() || button != 0 || !within(mx, my)) return false;

        int bx = x + width - PADDING - CLOSE_SIZE;
        int by = y + PADDING;
        if (mx >= bx && mx < bx + CLOSE_SIZE && my >= by && my < by + CLOSE_SIZE) {
            select(null);
            return true;
        }
        return true; // swallow every other click inside the panel
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isOpen() || !within(mx, my)) return false;
        scroll = (float) Math.max(0, Math.min(maxScroll, scroll - delta * LINE_H * 2));
        return true;
    }

    private boolean within(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
