package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.client.editor.widget.MarqueeText;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractModalScreen;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerLabels;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything known about one node of the stage graph, as a window rather than a docked column.
 *
 * <p>The docked panel this replaces was 190px wide, which a stage with several requirement
 * kinds turned into eight stacked sections of mostly chrome. A window can take the width it
 * needs, so the content is simply listed: no collapsing, no separators per section, no marquee.
 *
 * <p>Modal on purpose. At this size the map behind it would be too far covered to be worth
 * keeping live, and {@link AbstractModalScreen} already draws the graph dimmed behind the box.
 * Its draw depth matters more than it looks: the base lifts the whole dialog above the ~232
 * that {@code GuiGraphics.renderItem} draws at, which is what lets this screen show real item
 * icons without them punching through the frame.
 *
 * <p>Requirement rows render {@link DependencyResult} exactly as received from
 * {@link ClientDependencyCache} and never re-derive fulfilment — the same rule the docked panel
 * carried. Rows are rebuilt whenever the cache version changes, because the reply to a
 * dependency request can land while this screen is already open.
 */
public final class GraphDetailScreen extends AbstractModalScreen {

    /** Text line height. The font itself is 9px tall; the rest is leading. */
    private static final int LINE_H = 11;
    /** Heading, its rule, and the air under it. */
    private static final int SECTION_H = 17;
    private static final int SPACER_H = 8;
    /** Breathing room under each entry, so consecutive requirements do not touch. */
    private static final int ENTRY_GAP = 3;
    private static final int LEAD_PAD_TOP = 5;
    private static final int LEAD_PAD_BOTTOM = 6;
    private static final int PILL_H = 11;
    /** Height of a row carrying a 16px item icon. */
    private static final int ICON_ROW_H = 18;
    private static final int ICON_SIZE = 16;
    /** Status stripe down the left edge of an entry, and the gap before its content. */
    private static final int STRIPE_W = 2;
    private static final int STRIPE_GAP = 4;
    /** Left offset every entry's content starts at, stripe or no stripe. */
    private static final int ENTRY_INDENT = STRIPE_W + STRIPE_GAP;
    /** The fixed band under the title holding the pills and the stage id. */
    private static final int HEADER_BAND_H = PILL_H + 10;

    private static final int MIN_WIDTH = 280;
    private static final int MAX_WIDTH = 420;
    /** Share of the window the box may grow to before its content starts scrolling. */
    private static final float MAX_HEIGHT_SHARE = 0.8f;
    private static final int MIN_CONTENT_H = 40;

    private static final int TEXT_COLOR = 0xFFDDDDDD;
    private static final int HINT_COLOR = 0xFFAAAAAA;
    /** Section headings and their hairline, in the editor's accent. */
    private static final int SECTION_TEXT_COLOR = 0xFFFFCC00;
    private static final int SECTION_RULE_COLOR = 0x50FFCC00;
    private static final int AMOUNT_TEXT_COLOR = 0xFF888888;
    private static final int STATE_PILL_ALPHA = 0x33000000;
    /** Band behind the pills, and the line closing it off from the list. */
    private static final int HEADER_BAND_COLOR = 0x18FFFFFF;
    private static final int HEADER_RULE_COLOR = 0x30FFFFFF;
    /** The description block: fainter than the pill band, since it is content, not chrome. */
    private static final int LEAD_BG_COLOR = 0x10FFFFFF;
    private static final int LEAD_RULE_COLOR = 0x28FFFFFF;

    // Stripe colours, taking the Research Pedestal's requirement palette so the two views of
    // the same list agree on what green and red mean.
    private static final int STRIPE_MET_COLOR = 0xFF2E8B57;
    private static final int STRIPE_OPEN_COLOR = 0xFFAA3333;
    /** Requirements this view cannot judge, and unlocks, which are not a status at all. */
    private static final int STRIPE_NEUTRAL_COLOR = 0x40FFFFFF;

    private static final int STATE_UNLOCKED_COLOR = 0xFF44CC99;
    private static final int STATE_REACHABLE_COLOR = 0xFFDDBB44;
    private static final int STATE_LOCKED_COLOR = 0xFF999999;
    /** Global vs. individual is a fact about the stage, not a status, so it stays neutral. */
    private static final int TYPE_PILL_COLOR = 0xFFBBBBBB;
    private static final int STAGE_ID_COLOR = 0xFF888888;

    private sealed interface Row permits SectionRow, LeadRow, LineRow, EntryRow, SpacerRow {
        int height();
    }

    private record SectionRow(String title, int height) implements Row {}

    /**
     * The hand-written description, set apart as the stage's opening paragraph.
     *
     * <p>It gets no heading of its own: it is prose about the stage, not one rubric among the
     * requirement lists below it. A tinted block closed off by a rule says the same thing
     * without adding a fifth gold heading to the window.
     */
    private record LeadRow(List<FormattedCharSequence> lines, int height) implements Row {}

    /** Free-running text: the description, a trigger line, a hint. */
    private record LineRow(FormattedCharSequence text, int color, int height) implements Row {}

    /**
     * One requirement or unlock, wrapped lines and all.
     *
     * <p>A whole entry is a single row rather than one row per wrapped line, so its status
     * stripe can run the full height of what it belongs to instead of breaking between lines.
     *
     * @param stripeColor status shown as a bar down the left edge rather than a glyph: the
     *                    Minecraft font has no dependable check mark, and the Research Pedestal
     *                    — where players read this same list — already marks state by colour
     * @param icon        the real item for item requirements, empty otherwise
     */
    private record EntryRow(List<FormattedCharSequence> lines, int stripeColor, String amount,
                            ItemStack icon, int height) implements Row {}

    private record SpacerRow(int height) implements Row {}

    /** A row with its content-space top resolved, so drawing never has to measure as it goes. */
    private record Placed(Row row, int top) {}

    private final StageGraphModel model;
    private final StageGraphModel.Node node;
    private final Scrollbar scrollbar = new Scrollbar();

    private List<Placed> rows = List.of();
    private int rowsHeight;
    /** Content height the box was last built for; a change means the box has to be rebuilt. */
    private int builtForHeight = -1;
    /** Dependency-cache state the rows were built from, so {@link #tick} knows when to rebuild. */
    private DependencyResult builtFromDependency;

    private float scroll;
    private float maxScroll;

    public GraphDetailScreen(Screen parent, StageGraphModel model, StageGraphModel.Node node) {
        super(parent, Component.literal(displayLabel(node)));
        this.model = model;
        this.node = node;
    }

    private static String displayLabel(StageGraphModel.Node node) {
        return node.label() == null || node.label().isEmpty() ? node.stageId() : node.label();
    }

    // --- Chrome -------------------------------------------------------------------------------

    @Override
    protected int dialogWidth() {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width * 2 / 3));
    }

    // subtitle() is deliberately not overridden. The base reserves SUBTITLE_H under an already
    // generous TITLE_H, which left the stage id floating a long way below its own name. The id
    // is drawn in the pill band instead, on the same line as the pills.

    @Override
    protected boolean showCancelButton() {
        return false; // nothing to cancel — this window only shows something
    }

    @Override
    protected Component confirmLabel() {
        return Component.translatable("editor.historystages.close");
    }

    @Override
    protected void onConfirm() {
        this.minecraft.setScreen(parent);
    }

    /** The pill band plus however much list fits under it. */
    @Override
    protected int contentHeight() {
        rebuildRows();
        builtForHeight = HEADER_BAND_H + listHeight();
        return builtForHeight;
    }

    private int listHeight() {
        return Math.max(MIN_CONTENT_H, Math.min(rowsHeight, maxListHeight()));
    }

    /**
     * What is left for the list once the frame, title, subtitle, button row and pill band have
     * taken theirs.
     */
    private int maxListHeight() {
        // No SUBTITLE_H: this screen draws no subtitle, so the base reserves none.
        int chrome = TITLE_H + PAD * 3 + BUTTON_H + HEADER_BAND_H;
        return Math.max(MIN_CONTENT_H, Math.round(this.height * MAX_HEIGHT_SHARE) - chrome);
    }

    /**
     * Resizes the box when the content changed size under it.
     *
     * <p>{@link AbstractModalScreen} measures the box once in {@code init}. The reply to a
     * dependency request routinely lands after that, so a window opened on a stage whose data
     * was not cached yet would keep the height of the single "loading" line and scroll twenty
     * rows inside it.
     */
    @Override
    public void tick() {
        super.tick();
        DependencyResult current = ClientDependencyCache.get(node.stageId(), node.individual());
        if (current == builtFromDependency) return;
        rebuildRows();
        if (HEADER_BAND_H + listHeight() != builtForHeight) {
            this.rebuildWidgets(); // re-runs init, which re-measures through contentHeight()
        }
    }

    // --- Content ------------------------------------------------------------------------------

    private void rebuildRows() {
        int width = dialogWidth() - PAD * 2 - Scrollbar.WIDTH - 2;
        // Free-running text is indented to the entries' content column, so it wraps to what is
        // left of the width rather than to all of it.
        int textWidth = Math.max(1, width - ENTRY_INDENT);
        Font font = this.font;
        List<Row> out = new ArrayList<>();
        GraphConfig.Graph cfg = GraphConfig.GRAPH;

        if (cfg.showDescription.get()) {
            String raw = GraphStageData.get().description(node.stageId(), node.individual());
            if (raw != null && !raw.isBlank()) {
                List<FormattedCharSequence> lines =
                        font.split(describe(raw), Math.max(1, width - ENTRY_INDENT * 2));
                if (!lines.isEmpty()) {
                    out.add(new LeadRow(lines,
                            LEAD_PAD_TOP + lines.size() * LINE_H + LEAD_PAD_BOTTOM + 1));
                    out.add(new SpacerRow(SPACER_H));
                }
            }
        }

        DependencyResult dep = ClientDependencyCache.get(node.stageId(), node.individual());
        builtFromDependency = dep;
        boolean anyRequirementSection = cfg.showStageDeps.get() || cfg.showItems.get() || cfg.showXp.get()
                || cfg.showAdvancements.get() || cfg.showKills.get() || cfg.showStats.get()
                || cfg.showScoreboard.get();
        if (anyRequirementSection && dep == null) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("editor.historystages.graph.detail.loading"), textWidth)) {
                out.add(new LineRow(line, HINT_COLOR, LINE_H));
            }
            out.add(new SpacerRow(SPACER_H));
        }

        // Sections come from the registry rather than seven fixed calls, so a requirement kind the
        // graph has never heard of still lands somewhere instead of vanishing.
        Map<String, List<String>> sections = new LinkedHashMap<>();
        for (Requirement requirement : RequirementTypes.all()) {
            sections.computeIfAbsent(requirement.sectionLangKey(), key -> new ArrayList<>())
                    .add(requirement.id());
        }
        // The built-in sections keep the order players already know, which is deliberately not
        // registry order — stage dependencies have always come first, and XP before advancements.
        // Anything left over belongs to an addon and follows.
        List<String> ordered = new ArrayList<>(BUILT_IN_SECTION_ORDER);
        for (String sectionKey : sections.keySet()) {
            if (!ordered.contains(sectionKey)) ordered.add(sectionKey);
        }
        for (String sectionKey : ordered) {
            List<String> types = sections.get(sectionKey);
            if (types == null) continue;
            addRequirements(out, font, width, sectionVisible(cfg, sectionKey), dep, sectionKey,
                    types.toArray(new String[0]));
        }

        if (cfg.showTriggers.get()) addTriggers(out, font, width);
        if (cfg.showUnlocks.get()) addUnlocks(out, font, width);

        if (out.isEmpty()) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("editor.historystages.graph.detail.empty"), textWidth)) {
                out.add(new LineRow(line, HINT_COLOR, LINE_H));
            }
        }

        place(out);
    }

    private void place(List<Row> built) {
        List<Placed> placed = new ArrayList<>(built.size());
        int cursor = 0;
        for (Row row : built) {
            placed.add(new Placed(row, cursor));
            cursor += row.height();
        }
        rows = placed;
        rowsHeight = cursor;
        maxScroll = 0; // recomputed on the next render, once the drawn height is known
    }

    /** The seven built-in sections, in the order they have always been drawn. */
    private static final List<String> BUILT_IN_SECTION_ORDER = List.of(
            "editor.historystages.graph.section.stage_deps",
            "editor.historystages.graph.section.items",
            "editor.historystages.graph.section.xp",
            "editor.historystages.graph.section.advancements",
            "editor.historystages.graph.section.kills",
            "editor.historystages.graph.section.stats",
            "editor.historystages.graph.section.scoreboard");

    /**
     * Whether this section is switched on.
     *
     * <p>The seven built-in sections each have their own config toggle; an addon section has none
     * and cannot get one, because NeoForge builds config specs at mod construction and the
     * requirement registry does not close until common setup. Always showing it is the honest
     * fallback — the alternative is a section nobody can turn on.
     *
     * <p>Yes, this is a fixed key table of the kind the rest of this phase removed. It stays,
     * because it maps sections onto config values that genuinely only exist for those seven.
     */
    private static boolean sectionVisible(GraphConfig.Graph cfg, String sectionLangKey) {
        return switch (sectionLangKey) {
            case "editor.historystages.graph.section.stage_deps" -> cfg.showStageDeps.get();
            case "editor.historystages.graph.section.items" -> cfg.showItems.get();
            case "editor.historystages.graph.section.xp" -> cfg.showXp.get();
            case "editor.historystages.graph.section.advancements" -> cfg.showAdvancements.get();
            case "editor.historystages.graph.section.kills" -> cfg.showKills.get();
            case "editor.historystages.graph.section.stats" -> cfg.showStats.get();
            case "editor.historystages.graph.section.scoreboard" -> cfg.showScoreboard.get();
            default -> true;
        };
    }

    private void addRequirements(List<Row> out, Font font, int width, boolean enabled,
                                 DependencyResult dep, String headerKey, String... types) {
        if (!enabled || dep == null) return;

        Set<String> typeSet = Set.of(types);
        List<Row> body = new ArrayList<>();
        for (DependencyResult.GroupResult group : dep.getGroups()) {
            for (DependencyResult.EntryResult e : group.getEntries()) {
                if (typeSet.contains(e.getType())) addRequirement(body, font, e, width);
            }
        }
        if (body.isEmpty()) return;

        out.add(new SectionRow(Component.translatable(headerKey).getString(), SECTION_H));
        out.addAll(body);
        out.add(new SpacerRow(SPACER_H));
    }

    /**
     * One requirement, presented only as far as this view can vouch for it.
     *
     * <p>Deposited requirements get neither a status glyph nor a figure: without a pedestal the
     * server reports them as untouched whatever the player has actually handed in, so both would
     * be a claim this screen cannot make. They are listed as what they are here — the shopping
     * list for the stage.
     */
    private void addRequirement(List<Row> out, Font font, DependencyResult.EntryResult e, int width) {
        RequirementDisplay.Kind kind = RequirementDisplay.kindOf(e.getType(), e.canDeposit());
        boolean met = e.isFulfilled();
        String amount = RequirementDisplay.showsAmount(kind) && !met
                ? e.getCurrent() + "/" + e.getRequired()
                : "";
        ItemStack icon = iconFor(e);
        int stripe = RequirementDisplay.showsStatus(kind)
                ? (met ? STRIPE_MET_COLOR : STRIPE_OPEN_COLOR)
                : STRIPE_NEUTRAL_COLOR;

        int indent = ENTRY_INDENT + (icon.isEmpty() ? 0 : ICON_SIZE + 3);
        int available = width - indent - (amount.isEmpty() ? 0 : font.width(amount) + 4);
        List<FormattedCharSequence> lines =
                font.split(Component.literal(e.getDescription()), Math.max(1, available));
        if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);

        int textH = lines.size() * LINE_H;
        int height = (icon.isEmpty() ? textH : Math.max(ICON_ROW_H, textH)) + ENTRY_GAP;
        out.add(new EntryRow(lines, stripe, amount, icon, height));
    }

    /**
     * The item an entry stands for, or an empty stack when it is not an item requirement or the
     * id names nothing. {@code BuiltInRegistries.ITEM.get} answers with air rather than null for
     * an unknown id, so the emptiness check is the real guard here.
     */
    private static ItemStack iconFor(DependencyResult.EntryResult e) {
        if (!"item".equals(e.getType())) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(e.getId());
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private void addTriggers(List<Row> out, Font font, int width) {
        StageEntry entry = node.individual()
                ? StageManager.getIndividualStages().get(node.stageId())
                : StageManager.getStages().get(node.stageId());
        if (entry == null || !entry.getMode().usesAutoTrigger()) return;

        AutoTrigger trigger = entry.getAutoTrigger();
        if (trigger == null || trigger.isEmpty()) return;

        int textWidth = Math.max(1, width - ENTRY_INDENT);
        out.add(new SectionRow(
                Component.translatable("editor.historystages.graph.section.triggers").getString(), SECTION_H));
        String modeKey = trigger.resolvedMode() == CombineMode.ANY
                ? "editor.historystages.auto_trigger.combine.any"
                : "editor.historystages.auto_trigger.combine.all";
        for (FormattedCharSequence line : font.split(Component.translatable(modeKey), textWidth)) {
            out.add(new LineRow(line, HINT_COLOR, LINE_H));
        }
        for (TriggerCondition t : trigger.getTriggers()) {
            String line = TriggerLabels.typeLabel(t) + ": " + TriggerLabels.valueText(t);
            for (FormattedCharSequence wrapped : font.split(Component.literal(line), textWidth)) {
                out.add(new LineRow(wrapped, TEXT_COLOR, LINE_H));
            }
        }
        out.add(new SpacerRow(SPACER_H));
    }

    /**
     * Stages whose dependencies reference this one — read directly off
     * {@link StageGraphModel#edges()} (this node as the prerequisite side) rather than walking
     * {@code DependencyGroup} again.
     */
    private void addUnlocks(List<Row> out, Font font, int width) {
        String key = StageManager.graphKey(node.stageId(), node.individual());
        List<Row> body = new ArrayList<>();
        for (StageGraphModel.Edge edge : model.edges()) {
            if (!edge.fromKey().equals(key)) continue;
            StageGraphModel.Node target = model.nodes().get(edge.toKey());
            if (target == null) continue;
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(displayLabel(target)), Math.max(1, width - ENTRY_INDENT));
            // Neutral stripe: an unlock is a consequence of this stage, not a requirement with
            // a state of its own, so it must not read as met or open.
            body.add(new EntryRow(lines, STRIPE_NEUTRAL_COLOR, "", ItemStack.EMPTY,
                    lines.size() * LINE_H + ENTRY_GAP));
        }
        if (body.isEmpty()) return;

        out.add(new SectionRow(
                Component.translatable("editor.historystages.graph.section.unlocks").getString(), SECTION_H));
        out.addAll(body);
        out.add(new SpacerRow(SPACER_H));
    }

    // --- Drawing ------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        renderHeaderBand(g, x, y, w);

        int listTop = y + HEADER_BAND_H;
        int bottom = y + builtForHeight;
        maxScroll = Math.max(0, rowsHeight - (bottom - listTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int right = x + w - Scrollbar.WIDTH - 2;
        int scrollPx = Math.round(scroll);

        g.enableScissor(x, listTop, x + w, bottom);
        for (Placed placed : rows) {
            int rowTop = listTop - scrollPx + placed.top();
            if (rowTop + placed.row().height() < listTop || rowTop > bottom) continue;
            drawRow(g, placed.row(), x, right, rowTop);
        }
        g.disableScissor();

        scrollbar.render(g, x + w - Scrollbar.WIDTH, listTop, bottom, scroll, maxScroll, mouseX, mouseY);
    }

    /**
     * The state and type pills with the stage id alongside them, on their own band above the
     * list.
     *
     * <p>Fixed rather than scrolled with the content: what a stage is, and whether it is global
     * or individual, stays true however far down the requirements you have read. The id shares
     * the line rather than sitting on its own under the title, which is where the base class
     * would have put it — two stacked lines of chrome before any content had begun.
     */
    private void renderHeaderBand(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + HEADER_BAND_H - 1, HEADER_BAND_COLOR);
        g.fill(x, y + HEADER_BAND_H - 1, x + w, y + HEADER_BAND_H, HEADER_RULE_COLOR);

        int pillY = y + 5;
        int px = drawPill(g, x + 3, pillY,
                Component.translatable(stateKey()).getString(), stateColor());
        px = drawPill(g, px + 4, pillY, Component.translatable(node.individual()
                ? "editor.historystages.stage_type.individual"
                : "editor.historystages.stage_type.global").getString(), TYPE_PILL_COLOR);

        // Right-aligned, and truncated rather than allowed to run into the pills on a narrow box.
        int idRoom = x + w - 3 - (px + 8);
        if (idRoom > 12) {
            String id = MarqueeText.truncate(this.font, node.stageId(), idRoom);
            g.drawString(this.font, id, x + w - 3 - this.font.width(id), pillY + 2,
                    STAGE_ID_COLOR, false);
        }
    }

    /** @return x just past the pill drawn */
    private int drawPill(GuiGraphics g, int x, int y, String text, int color) {
        int pillW = this.font.width(text) + 6;
        g.fill(x, y, x + pillW, y + PILL_H, (color & 0x00FFFFFF) | STATE_PILL_ALPHA);
        g.drawString(this.font, text, x + 3, y + 2, color, false);
        return x + pillW;
    }

    private void drawRow(GuiGraphics g, Row row, int left, int right, int top) {
        switch (row) {
            case SectionRow r -> {
                g.drawString(this.font, r.title(), left, top + 2, SECTION_TEXT_COLOR, false);
                // Rule sits below the text with air on both sides, not flush against the entry
                // that follows it.
                g.fill(left, top + 13, right, top + 14, SECTION_RULE_COLOR);
            }
            case LeadRow r -> {
                int blockH = r.height() - 1;
                g.fill(left, top, right, top + blockH, LEAD_BG_COLOR);
                g.fill(left, top + blockH, right, top + blockH + 1, LEAD_RULE_COLOR);
                for (int i = 0; i < r.lines().size(); i++) {
                    g.drawString(this.font, r.lines().get(i), left + ENTRY_INDENT,
                            top + LEAD_PAD_TOP + i * LINE_H, TEXT_COLOR, false);
                }
            }
            case LineRow r -> g.drawString(this.font, r.text(), left + ENTRY_INDENT, top,
                    r.color(), false);
            case EntryRow r -> {
                g.fill(left, top, left + STRIPE_W, top + r.height(), r.stripeColor());

                int textLeft = left + ENTRY_INDENT;
                int textTop = top;
                if (!r.icon().isEmpty()) {
                    g.renderItem(r.icon(), textLeft, top + (r.height() - ICON_SIZE) / 2);
                    g.renderItemDecorations(this.font, r.icon(), textLeft, top + (r.height() - ICON_SIZE) / 2);
                    textLeft += ICON_SIZE + 3;
                    // Single-line entries centre against the icon; taller ones start at the top,
                    // where centring would push the first line away from its own stripe.
                    if (r.lines().size() == 1) {
                        textTop = top + (r.height() - this.font.lineHeight) / 2 + 1;
                    }
                }
                for (int i = 0; i < r.lines().size(); i++) {
                    g.drawString(this.font, r.lines().get(i), textLeft, textTop + i * LINE_H,
                            TEXT_COLOR, false);
                }
                drawAmount(g, r.amount(), right, textTop);
            }
            case SpacerRow ignored -> {
            }
        }
    }

    private void drawAmount(GuiGraphics g, String amount, int right, int top) {
        if (amount.isEmpty()) return;
        g.drawString(this.font, amount, right - this.font.width(amount), top, AMOUNT_TEXT_COLOR, false);
    }

    // --- Input --------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scroll = (float) Math.max(0, Math.min(maxScroll, scroll - scrollY * LINE_H * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbar.mouseClicked(mouseX, mouseY)) {
            scroll = scrollbar.scrollFor(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbar.isDragging()) {
            scroll = scrollbar.scrollFor(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- Text ---------------------------------------------------------------------------------

    /** Literal text, unless a translation exists for it — mirrors the docked panel it replaces. */
    /**
     * A description is either a lang key or literal text. Literal text may carry {@code &} colour
     * codes, the same convention as the mod's other display strings — which is what the rich text
     * editor behind this field writes.
     */
    private static Component describe(String raw) {
        return I18n.exists(raw) ? Component.translatable(raw)
                : Component.literal(raw.replace('&', '§'));
    }

    private String stateKey() {
        return switch (node.state()) {
            case UNLOCKED -> "editor.historystages.graph.state.unlocked";
            case REACHABLE -> "editor.historystages.graph.state.reachable";
            case LOCKED -> "editor.historystages.graph.state.locked";
        };
    }

    private int stateColor() {
        return switch (node.state()) {
            case UNLOCKED -> STATE_UNLOCKED_COLOR;
            case REACHABLE -> STATE_REACHABLE_COLOR;
            case LOCKED -> STATE_LOCKED_COLOR;
        };
    }

}
