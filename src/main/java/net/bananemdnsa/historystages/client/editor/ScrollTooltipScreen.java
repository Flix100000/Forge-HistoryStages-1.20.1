package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dialog.FormattedTextScreen;
import net.bananemdnsa.historystages.client.tooltip.ScrollTooltipContext;
import net.bananemdnsa.historystages.client.tooltip.ScrollTooltipRenderer;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLine;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Editor for the research scroll's tooltip layout: which lines exist, in which order, with
 * which text, colour and spacing. Edits the single {@code scrollTooltipLines} entry owned by
 * {@link ConfigEditorScreen} directly, so one Save on the parent screen covers it and the
 * unsaved-changes marker there stays honest without this screen keeping a save of its own.
 *
 * <p>Three fixed groups are shown, top to bottom: the always-first {@code name} line and the
 * reorderable sections ({@link ScrollTooltipLayout#MOVABLE_IDS}), the dependency block's fixed
 * templates, and the dependency block's plain-text options. Only the reorderable sections carry
 * a drag handle; the other two groups have a fixed shape that nothing here changes.
 *
 * <p>The row list is kept to the left half of the screen, leaving room to the right for a live
 * preview of the item name and hover tooltip, built from {@link #lines} — the edited state —
 * against a constant made-up {@link #SAMPLE_CONTEXT} rather than any real stage, so every
 * configurable line and every dependency icon state is visible at once regardless of what the
 * loaded pack actually defines.
 */
public class ScrollTooltipScreen extends Screen {

    private static final int ROWS_TOP = 30;
    private static final int BUTTON_BAND = 40;
    /** Height of one row; matches {@code ConfigRowList.ENTRY_HEIGHT} so the two lists read as one family. */
    private static final int ROW_H = 24;
    private static final int SECTION_HEADER_H = 18;
    private static final int SECTION_GAP = 10;

    private static final int HANDLE_W = 12;
    private static final int CHECKBOX_W = 12;
    private static final int SPACER_W = 14;
    private static final int CONTROL_GAP = 3;
    private static final int LABEL_W = 118;
    private static final int FIELD_GAP = 6;

    /** Width of the reset-to-default × itself, matching {@code ConfigRowList.CLEAR_WIDTH}. */
    private static final int RESET_W = 10;
    /**
     * Space kept clear at the left of every row for the reset ×, whether or not it currently
     * draws one — the same convention {@code ConfigRowList} uses for its clear-to-inherit gutter,
     * so a row switching in or out of "overridden" does not shift its label sideways.
     */
    private static final int RESET_GUTTER_W = RESET_W + 4;

    private static final int SWATCH_SIZE = 14;

    /** Inset of the previewed tooltip from the panel's top-left, before pan and zoom. */
    private static final int PREVIEW_PAD = 8;
    private static final float PREVIEW_ZOOM_MIN = 0.5f;
    private static final float PREVIEW_ZOOM_MAX = 3.0f;

    /** How far the cursor has to travel from the press before a handle press becomes a drag. */
    private static final int DRAG_THRESHOLD = 3;

    private static final String NAME_ID = ScrollTooltipLayout.NAME_ID;
    private static final int MOVABLE_START = 1;
    private static final int MOVABLE_COUNT = ScrollTooltipLayout.MOVABLE_IDS.size();
    private static final int TEMPLATE_START = MOVABLE_START + MOVABLE_COUNT;
    private static final int TEMPLATE_COUNT = ScrollTooltipLayout.DEP_TEMPLATE_IDS.size();
    private static final int OPTION_START = TEMPLATE_START + TEMPLATE_COUNT;
    private static final int OPTION_COUNT = ScrollTooltipLayout.DEP_OPTION_IDS.size();

    /** Built-in default text shown as a text field's hint, so an empty field's meaning is visible. */
    private static final Map<String, String> HINT_KEYS = Map.ofEntries(
            Map.entry("name", "tooltip.historystages.research_scroll.named"),
            Map.entry("individual_badge", "tooltip.historystages.scroll.individual"),
            Map.entry("owner", "tooltip.historystages.scroll.owner"),
            Map.entry("info1", "tooltip.historystages.research_scroll.info1"),
            Map.entry("info2", "tooltip.historystages.research_scroll.info2"),
            Map.entry("tier", "tooltip.historystages.research_scroll.tier.min"),
            Map.entry("dep.header", "tooltip.historystages.scroll.dependencies"),
            Map.entry("dep.item", "tooltip.historystages.dep.item"),
            Map.entry("dep.stage", "tooltip.historystages.dep.stage"),
            Map.entry("dep.individual", "tooltip.historystages.dep.individual"),
            Map.entry("dep.xp", "tooltip.historystages.dep.level"),
            Map.entry("dep.separator", "tooltip.historystages.dep.separator"));

    /** Placeholder tokens a row's text accepts, shown in a hover tooltip. No entry = no tooltip. */
    private static final Map<String, String> PLACEHOLDERS = Map.ofEntries(
            Map.entry("name", "%stage%"),
            Map.entry("individual_badge", "%stage%"),
            Map.entry("info1", "%stage%"),
            Map.entry("info2", "%stage%"),
            Map.entry("owner", "%owner%, %stage%"),
            Map.entry("tier", "%tier%, %tier_num%, %stage%"),
            Map.entry("dep.item", "%icon%, %name%, %current%, %required%"),
            Map.entry("dep.stage", "%icon%, %name%"),
            Map.entry("dep.individual", "%icon%, %name%, %mode%"),
            Map.entry("dep.xp", "%icon%, %level%"),
            Map.entry("dep.separator", "%logic%"));

    /**
     * Which controls a row draws, resolved once per id from its group — see the class javadoc.
     * {@code colorSwatch} draws the colour picker swatch. Bold and italic have no control here:
     * they are written into the text itself as {@code &} codes through the rich text dialog, and
     * a second place to set them would be a second source of truth for the same thing.
     */
    private record RowSpec(boolean handle, boolean checkbox, boolean spacer, boolean text,
                            boolean colorSwatch) {}

    private record PositionedRow(String id, RowSpec spec, int y) {}

    /** The colour and format pieces read out of one style (or, for the two colour options, text) field. */
    private record StyleState(@Nullable String colorHex, boolean bold, boolean italic) {}

    private static final Map<String, ScrollTooltipLine> DEFAULTS_BY_ID = ScrollTooltipLayout.defaults()
            .stream().collect(Collectors.toMap(ScrollTooltipLine::id, l -> l));

    private final ConfigEditorScreen parent;
    private final ConfigEditorScreen.ConfigEntry entry;

    /**
     * The whole layout, always exactly {@code name + MOVABLE_IDS + DEP_TEMPLATE_IDS +
     * DEP_OPTION_IDS} long and in that order — {@link ScrollTooltipLayout#parse} both fills in
     * anything missing from the saved value and drops anything it no longer recognises, so the
     * row list never has to guard against a short or malformed list. Reordering only ever
     * touches the movable slice, {@code [MOVABLE_START, MOVABLE_START + MOVABLE_COUNT)}.
     */
    private final List<ScrollTooltipLine> lines;

    private final Map<String, Anim> rowHover = new HashMap<>();
    /** Per-movable-id pixel offset used to slide rows out of a drag's way; see {@link #reorderMovable}. */
    private final Map<String, Anim> rowOffset = new HashMap<>();
    private final EditorTooltip tooltip = new EditorTooltip();

    private int rowsBottom;
    private int maxScroll;
    private double scrollOffset;
    private final Anim smoothScroll = new Anim();
    private boolean draggingScrollbar;

    /**
     * Zoom and offset of the previewed tooltip inside its panel. A full tooltip with every
     * dependency line is easily taller and wider than the panel, so it can be scaled down to
     * take it all in and dragged around to read one corner at a time.
     */
    private float previewZoom = 1.0f;
    private float previewPanX;
    private float previewPanY;
    private boolean draggingPreview;
    private double previewDragLastX;
    private double previewDragLastY;

    // --- drag & drop state (movable rows only) ---
    private boolean dragArmed;
    private boolean dragStarted;
    private double pressX, pressY;
    private int dragFromSlot = -1;
    private int dragTargetSlot = -1;

    /** Row whose drop just landed, highlighted for {@link Timing#DROP_PULSE_MS}. */
    private String pulseId;
    private long pulseStart;

    public ScrollTooltipScreen(ConfigEditorScreen parent, ConfigEditorScreen.ConfigEntry entry) {
        super(Component.translatable("editor.historystages.scrolltip.title"));
        this.parent = parent;
        this.entry = entry;
        // parse() both fills in any id missing from the saved value and drops anything unknown,
        // so this is already the complete, canonically-ordered 18-line layout the row list
        // assumes below — a plain split-and-decode would need the same fallback rebuilt by hand.
        this.lines = new ArrayList<>(ScrollTooltipLayout.parse(List.of(entry.value.split(";", -1))));
    }

    private void writeBack() {
        entry.value = lines.stream()
                .map(ScrollTooltipLayout::encodeLine)
                .collect(Collectors.joining(";"));
    }

    // --- sample preview context ---

    /**
     * Constant sample the preview renders against instead of any real stage, so every
     * configurable line — and every dependency icon state — is visible at once regardless of
     * what the loaded pack defines. Built once; nothing about it changes while this screen is
     * open.
     *
     * <p>The dependency stage ids below are never registered with {@code StageManager}. That is
     * deliberate — this context must not depend on the pack — but it means those two rows fall
     * back to showing the raw id rather than a nicely capitalised name, same as they would for
     * any other unresolvable id. That is an accepted limitation of a made-up sample, not
     * something worth faking a fake stage into the registry to avoid.
     */
    private static final ScrollTooltipContext SAMPLE_CONTEXT = buildSampleContext();

    private static ScrollTooltipContext buildSampleContext() {
        String stageName = Component.translatable("editor.historystages.scrolltip.sample_stage").getString();
        String ownerName = Component.translatable("editor.historystages.scrolltip.sample_owner").getString();

        DependencyGroup group1 = new DependencyGroup();
        group1.setLogic("AND");
        group1.setItems(List.of(
                new DependencyItem("minecraft:iron_ingot", 5),
                new DependencyItem("minecraft:diamond", 3)));

        DependencyGroup group2 = new DependencyGroup();
        group2.setLogic("OR");
        group2.setStages(List.of("bronze_age"));
        group2.setIndividualStages(List.of(new IndividualStageDep("apprenticeship", "all_online")));
        group2.setXpLevel(new XpLevelDep(10, false));

        // One fulfilled entry, one open entry with visible progress, and the "bronze_age" stage
        // above deliberately has no matching entry at all — the three icon states the preview
        // has to demonstrate.
        List<DependencyResult.EntryResult> entries = List.of(
                new DependencyResult.EntryResult("item", "minecraft:iron_ingot", "5x Iron Ingot", true, 5, 5),
                new DependencyResult.EntryResult("item", "minecraft:diamond", "3x Diamond", false, 1, 3),
                new DependencyResult.EntryResult("individual_stage", "apprenticeship", "Apprenticeship", true, 1, 1),
                new DependencyResult.EntryResult("xp_level", "xp", "Level 10", false, 4, 10));
        DependencyResult result = new DependencyResult(false,
                List.of(new DependencyResult.GroupResult("AND", false, entries)));

        return new ScrollTooltipContext(stageName, true, ownerName, 3, TierMode.MIN,
                List.of(group1, group2), result);
    }

    // --- model access ---

    private ScrollTooltipLine getLine(String id) {
        for (ScrollTooltipLine l : lines) if (l.id().equals(id)) return l;
        return null;
    }

    private void putLine(String id, ScrollTooltipLine updated) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                lines.set(i, updated);
                break;
            }
        }
        writeBack();
    }

    private int movableSlotOf(String id) {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            if (lines.get(MOVABLE_START + slot).id().equals(id)) return slot;
        }
        return -1;
    }

    private static int indexOfId(List<ScrollTooltipLine> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return -1;
    }

    private static boolean isColorOnlyOption(String id) {
        return "dep.color_fulfilled".equals(id) || "dep.color_open".equals(id);
    }

    /** Which field a row's colour lives in: {@code text} for the two plain colour options, {@code style} for everyone else. */
    private static String styleSource(@Nullable ScrollTooltipLine line, String id) {
        if (line == null) return "";
        return isColorOnlyOption(id) ? line.text() : line.style();
    }

    private RowSpec specFor(String id) {
        if (NAME_ID.equals(id)) return new RowSpec(false, false, false, true, true);
        if ("dependencies".equals(id)) return new RowSpec(true, true, true, false, false);
        if (ScrollTooltipLayout.MOVABLE_IDS.contains(id)) return new RowSpec(true, true, true, true, true);
        if (ScrollTooltipLayout.DEP_TEMPLATE_IDS.contains(id)) {
            // The four dependency entry templates deliberately have no colour swatch: their
            // colour comes from dep.color_fulfilled / dep.color_open, and a second colour
            // source for the same line would be a bug (two competing answers), not a feature.
            boolean styleAllowed = "dep.header".equals(id) || "dep.separator".equals(id);
            return new RowSpec(false, true, false, true, styleAllowed);
        }
        if (isColorOnlyOption(id)) return new RowSpec(false, false, false, false, true);
        return new RowSpec(false, false, false, true, false); // the three icon options
    }

    // --- style token read/write, shared by the swatch and the two format toggles ---

    private static StyleState readStyleState(String raw) {
        String colorHex = null;
        boolean bold = false;
        boolean italic = false;
        for (String token : ScrollTooltipLayout.styleTokens(raw)) {
            if (GraphColors.isValid(token)) {
                colorHex = GraphColors.format(GraphColors.parse(token, 0));
                continue;
            }
            ChatFormatting format = ChatFormatting.getByName(token);
            if (format == null) continue;
            if (format == ChatFormatting.BOLD) {
                bold = true;
            } else if (format == ChatFormatting.ITALIC) {
                italic = true;
            } else if (format.isColor() && format.getColor() != null) {
                colorHex = GraphColors.format(format.getColor());
            }
        }
        return new StyleState(colorHex, bold, italic);
    }

    private static String writeStyleState(@Nullable String colorHex, boolean bold, boolean italic) {
        List<String> tokens = new ArrayList<>();
        if (colorHex != null && GraphColors.isValid(colorHex)) tokens.add(colorHex);
        if (bold) tokens.add("bold");
        if (italic) tokens.add("italic");
        return String.join("+", tokens);
    }

    // --- layout ---

    @Override
    protected void init() {
        rowsBottom = this.height - BUTTON_BAND;
        updateMaxScroll();

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> onClose(), 10, this.height - 30, 60, 20));

        // Saves through the config editor, so this covers every tab and not just the block on
        // screen. Deliberately does not navigate: the point of saving here is to keep tweaking.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> parent.saveConfig(), this.width / 2 - 50, this.height - 30, 100, 20));

        // Puts the whole layout back to the built-in defaults. Behind a confirm because it
        // throws away every line at once, unlike the per-row reset which is one line and cheap
        // to undo by hand.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.reset"),
                btn -> this.minecraft.setScreen(new ConfirmDialog(
                        this,
                        Component.translatable("editor.historystages.scrolltip.reset_all_title"),
                        Component.translatable("editor.historystages.scrolltip.reset_all"),
                        () -> {
                            resetAll();
                            this.minecraft.setScreen(this);
                        })),
                this.width - 70, this.height - 30, 60, 20));
    }

    /** Restores every line to its default and puts the preview viewport back to 1:1. */
    private void resetAll() {
        lines.clear();
        lines.addAll(ScrollTooltipLayout.defaults());
        writeBack();
        previewZoom = 1.0f;
        previewPanX = 0;
        previewPanY = 0;
    }

    /**
     * The text column: the current value, or the built-in default greyed out when the line has
     * none of its own. Clicking opens {@link FormattedTextScreen} rather than editing in place —
     * these values carry format codes and placeholders, and a 100px box that scrolls sideways is
     * how they used to be edited blind.
     */
    private void renderTextValue(GuiGraphics g, PositionedRow row, int mouseX, int mouseY) {
        int fx = textFieldX();
        int fw = textFieldWidth(row.spec());
        int fy = row.y() + (ROW_H - 16) / 2;
        boolean hovered = mouseX >= fx && mouseX < fx + fw
                && mouseY >= row.y() && mouseY < row.y() + ROW_H;

        g.fill(fx - 1, fy - 1, fx + fw + 1, fy + 17, hovered ? 0xFFFFCC00 : 0xFF4A4A4A);
        g.fill(fx, fy, fx + fw, fy + 16, 0xFF0D0D0D);

        ScrollTooltipLine line = getLine(row.id());
        String own = line == null ? "" : line.text();
        String hintKey = HINT_KEYS.get(row.id());
        String shown = !own.isEmpty() ? own
                : hintKey != null ? Component.translatable(hintKey).getString() : "";
        int colour = own.isEmpty() ? 0xFF707070 : 0xFFFFFFFF;

        String clipped = this.font.plainSubstrByWidth(shown, fw - 8);
        if (!clipped.equals(shown)) clipped = clipped + "…";
        g.drawString(this.font, clipped, fx + 4, fy + 4, colour, false);
    }

    private void openTextEditorFor(String id) {
        ScrollTooltipLine line = getLine(id);
        if (line == null) return;
        String hintKey = HINT_KEYS.get(id);
        String placeholders = PLACEHOLDERS.get(id);
        List<String> tokens = placeholders == null ? List.of()
                : List.of(placeholders.split(",\\s*"));

        this.minecraft.setScreen(new FormattedTextScreen(this,
                Component.translatable("editor.historystages.scrolltip.line." + id),
                line.text(),
                hintKey == null ? "" : Component.translatable(hintKey).getString(),
                tokens,
                text -> {
                    ScrollTooltipLine current = getLine(id);
                    if (current != null) putLine(id, current.withText(text));
                }));
    }

    private static int totalContentHeight() {
        return SECTION_HEADER_H + ROW_H // name section header + name row
                + MOVABLE_COUNT * ROW_H
                + SECTION_GAP + SECTION_HEADER_H + TEMPLATE_COUNT * ROW_H
                + SECTION_GAP + SECTION_HEADER_H + OPTION_COUNT * ROW_H;
    }

    private void updateMaxScroll() {
        maxScroll = Math.max(0, totalContentHeight() - (rowsBottom - ROWS_TOP));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int contentLeft() { return 24; }

    /** Right edge of the row column — half the window, leaving room for the preview panel. */
    private int contentRight() { return this.width / 2 + 40; }

    private int scrollbarX() { return contentRight() + 8; }

    /** Left edge of a row's handle/checkbox/spacer/label controls, past the reset gutter every row reserves. */
    private int rowContentLeft() { return contentLeft() + RESET_GUTTER_W; }

    private int textFieldX() {
        return rowContentLeft() + HANDLE_W + CONTROL_GAP + CHECKBOX_W + CONTROL_GAP
                + SPACER_W + CONTROL_GAP + LABEL_W;
    }

    private int textFieldWidth(RowSpec spec) {
        int rightLimit = spec.colorSwatch() ? styleControlsX(spec) - FIELD_GAP : contentRight() - 6;
        return Math.max(50, rightLimit - textFieldX());
    }

    private static int styleControlsWidth(RowSpec spec) {
        return SWATCH_SIZE;
    }

    private int styleControlsX(RowSpec spec) { return contentRight() - styleControlsWidth(spec) - 4; }

    private int swatchX(RowSpec spec) { return styleControlsX(spec); }

    /** Top of the movable slice, i.e. the y the first movable row would sit at with no drag offset. */
    private int movableTop() {
        return ROWS_TOP - Math.round(smoothScroll.value()) + SECTION_HEADER_H + ROW_H;
    }

    /**
     * Where slot {@code originalIndex} should visually sit while a drag is running: shifted by
     * one slot towards the gap left by {@link #dragFromSlot}, or unchanged outside that span.
     * Feeds both the offset animation's target and, after a drop, the row's real new index —
     * the two agree by construction, which is what makes the drop land exactly where the slide
     * already showed it landing.
     */
    private int visualSlot(int originalIndex) {
        if (!dragStarted) return originalIndex;
        if (dragFromSlot < dragTargetSlot) {
            if (originalIndex > dragFromSlot && originalIndex <= dragTargetSlot) return originalIndex - 1;
        } else if (dragFromSlot > dragTargetSlot) {
            if (originalIndex >= dragTargetSlot && originalIndex < dragFromSlot) return originalIndex + 1;
        }
        return originalIndex;
    }

    private int computeDragTargetSlot(double mouseY, int movableTop) {
        int rel = (int) Math.floor((mouseY - movableTop + ROW_H / 2.0) / ROW_H);
        return Math.max(0, Math.min(MOVABLE_COUNT - 1, rel));
    }

    /** Advances every movable row's offset animation exactly once; called only from {@link #render}. */
    private void advanceRowAnims() {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            String id = lines.get(MOVABLE_START + slot).id();
            Anim anim = rowOffset.computeIfAbsent(id, k -> new Anim());
            float target = 0f;
            if (dragStarted && slot != dragFromSlot) {
                target = (visualSlot(slot) - slot) * (float) ROW_H;
            }
            anim.approach(target, Timing.SCROLL_HALF_LIFE_MS);
        }
    }

    /** True while any movable row still has to slide into place — see {@link #mouseClicked}. */
    private boolean isSettling() {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            Anim a = rowOffset.get(lines.get(MOVABLE_START + slot).id());
            if (a != null && !a.isAt(0f)) return true;
        }
        return false;
    }

    /**
     * Pure read of the current row positions — no animation stepping happens here, only in
     * {@link #advanceRowAnims}, so this can be called again from the click paths against the
     * values the last render left behind without double-advancing anything.
     */
    private List<PositionedRow> layoutRows() {
        List<PositionedRow> out = new ArrayList<>(lines.size());
        int y = ROWS_TOP - Math.round(smoothScroll.value());
        y += SECTION_HEADER_H;

        out.add(new PositionedRow(NAME_ID, specFor(NAME_ID), y));
        y += ROW_H;

        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            String id = lines.get(MOVABLE_START + slot).id();
            if (dragStarted && slot == dragFromSlot) {
                // Drawn as a ghost near the cursor instead — see render().
                y += ROW_H;
                continue;
            }
            float off = rowOffset.containsKey(id) ? rowOffset.get(id).value() : 0f;
            out.add(new PositionedRow(id, specFor(id), y + Math.round(off)));
            y += ROW_H;
        }

        y += SECTION_GAP + SECTION_HEADER_H;
        for (int i = 0; i < TEMPLATE_COUNT; i++) {
            String id = lines.get(TEMPLATE_START + i).id();
            out.add(new PositionedRow(id, specFor(id), y));
            y += ROW_H;
        }

        y += SECTION_GAP + SECTION_HEADER_H;
        for (int i = 0; i < OPTION_COUNT; i++) {
            String id = lines.get(OPTION_START + i).id();
            out.add(new PositionedRow(id, specFor(id), y));
            y += ROW_H;
        }

        return out;
    }

    // --- reorder ---

    /**
     * Moves the line at {@code fromSlot} to {@code toSlot} within the movable slice and
     * compensates every other row's offset animation for the index shift (a FLIP: First, Last,
     * Invert, Play), so a row that was mid-slide keeps its exact on-screen position across the
     * reorder instead of jumping by one row height and re-settling from scratch.
     */
    private void reorderMovable(int fromSlot, int toSlot) {
        if (fromSlot == toSlot) return;
        List<ScrollTooltipLine> before = new ArrayList<>(lines.subList(MOVABLE_START, MOVABLE_START + MOVABLE_COUNT));
        List<ScrollTooltipLine> after = new ArrayList<>(before);
        ScrollTooltipLine dragged = after.remove(fromSlot);
        after.add(toSlot, dragged);

        for (int oldIndex = 0; oldIndex < before.size(); oldIndex++) {
            if (oldIndex == fromSlot) continue;
            String id = before.get(oldIndex).id();
            int newIndex = indexOfId(after, id);
            if (newIndex == oldIndex) continue;
            Anim anim = rowOffset.computeIfAbsent(id, k -> new Anim());
            anim.set(anim.value() + (oldIndex - newIndex) * (float) ROW_H);
        }

        for (int i = 0; i < after.size(); i++) {
            lines.set(MOVABLE_START + i, after.get(i));
        }
    }

    // --- rendering ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own background, drawn in render() — avoids 1.21's menu blur shader, as the config screen does.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        if (dragStarted) {
            dragTargetSlot = computeDragTargetSlot(mouseY, movableTop());
        }
        advanceRowAnims();

        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        List<PositionedRow> rows = layoutRows();

        int left = contentLeft();
        int right = contentRight();
        g.enableScissor(left - 4, ROWS_TOP, right + 4, rowsBottom);

        drawSectionHeader(g, "editor.historystages.scrolltip.section.lines", headerY(rows, 0));
        drawSectionHeader(g, "editor.historystages.scrolltip.section.templates", headerY(rows, 1));
        drawSectionHeader(g, "editor.historystages.scrolltip.section.options", headerY(rows, 2));

        String hoveredId = null;
        // The toggle columns carry no label, so they explain themselves on hover.
        String hoveredControlKey = null;
        int rowLeft = rowContentLeft();
        int resetHoverX = left + 2;
        int checkboxHoverX = rowLeft + HANDLE_W + CONTROL_GAP;
        int spacerHoverX = checkboxHoverX + CHECKBOX_W + CONTROL_GAP;
        for (PositionedRow row : rows) {
            boolean visible = row.y() + ROW_H > ROWS_TOP && row.y() < rowsBottom;
            if (visible) renderRow(g, row, mouseX, mouseY);

            if (visible && row.spec().text()) {
                renderTextValue(g, row, mouseX, mouseY);
            }

            if (visible && mouseX >= left && mouseX <= right
                    && mouseY >= Math.max(row.y(), ROWS_TOP) && mouseY < Math.min(row.y() + ROW_H, rowsBottom)) {
                hoveredId = row.id();
                RowSpec spec = row.spec();
                if (isOverridden(row.id()) && mouseX >= resetHoverX && mouseX < resetHoverX + RESET_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.reset";
                } else if (spec.checkbox() && mouseX >= checkboxHoverX && mouseX < checkboxHoverX + CHECKBOX_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.enabled";
                } else if (spec.spacer() && mouseX >= spacerHoverX && mouseX < spacerHoverX + SPACER_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.spacer";
                } else if (spec.colorSwatch()) {
                    int sx = swatchX(spec);
                    if (mouseX >= sx && mouseX < sx + SWATCH_SIZE) {
                        hoveredControlKey = "editor.historystages.scrolltip.color";
                    }
                }
            }
        }

        if (pulseId != null) {
            long age = System.currentTimeMillis() - pulseStart;
            if (age >= Timing.DROP_PULSE_MS) {
                pulseId = null;
            } else {
                for (PositionedRow row : rows) {
                    if (!row.id().equals(pulseId)) continue;
                    if (row.y() + ROW_H > ROWS_TOP && row.y() < rowsBottom) {
                        drawPulseOutline(g, row.y(), Ease.pulse((float) age / Timing.DROP_PULSE_MS));
                    }
                    break;
                }
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(20, (rowsBottom - ROWS_TOP) * (rowsBottom - ROWS_TOP)
                    / (maxScroll + rowsBottom - ROWS_TOP));
            int barY = ROWS_TOP + Math.round(smoothScroll.value() / maxScroll * (rowsBottom - ROWS_TOP - barH));
            g.fill(scrollbarX(), ROWS_TOP, scrollbarX() + 3, rowsBottom, 0x20FFFFFF);
            g.fill(scrollbarX(), barY, scrollbarX() + 3, barY + barH, 0x80FFFFFF);
        }

        renderPreview(g, contentRight() + 30, ROWS_TOP, rowsBottom - ROWS_TOP);

        if (dragStarted) drawGhost(g, mouseX, mouseY);

        if (parent.hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS) / Timing.BREATHE_PERIOD_MS;
            g.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.unsaved").getString(), dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // A toggle column wins over the row's placeholder list: the cursor is on the toggle, and
        // that is what the player is asking about. Keyed separately so moving between the two
        // restarts the appear delay instead of swapping the text under the cursor.
        String placeholders = hoveredId == null ? null : PLACEHOLDERS.get(hoveredId);
        String tipKey = hoveredControlKey != null ? hoveredControlKey
                : (placeholders == null ? null : hoveredId);
        String tipText = hoveredControlKey != null
                ? Component.translatable(hoveredControlKey).getString()
                : (placeholders == null ? null
                        : Component.translatable("editor.historystages.scrolltip.placeholders", placeholders).getString());
        tooltip.render(g, this.font, tipKey, tipText, mouseX, mouseY, this.width, this.height);
    }

    /**
     * Live preview of the research scroll's item name and hover tooltip, rebuilt every frame
     * from {@link #lines} — the state currently being edited — against the constant
     * {@link #SAMPLE_CONTEXT} via {@link ScrollTooltipRenderer}, the same builder the real
     * tooltip uses. Recomputing every frame keeps it in sync with every keystroke and toggle
     * without a dirty flag to maintain.
     *
     * <p>Drawn into its own framed panel rather than through
     * {@link GuiGraphics#renderComponentTooltip}: that method treats its coordinates as a cursor
     * position and flips the box to the other side of the screen when it would overflow, which
     * is what previously sent a long dependency line — and the whole preview with it — jumping
     * left on top of the row list. A fixed panel, scissored to its own bounds, cannot do that;
     * a line too wide for it is clipped instead of relocating the panel.
     */
    private void renderPreview(GuiGraphics g, int x, int y, int h) {
        g.drawString(this.font,
                Component.translatable("editor.historystages.scrolltip.preview").getString(),
                x, y, 0xFF888888, false);

        int panelY = previewTop();
        int panelH = previewH();
        int w = previewW();
        if (w < 60 || panelH < 30) return;

        g.fill(x - 1, panelY - 1, x + w + 1, panelY + panelH + 1, 0xFF555555);
        g.fill(x, panelY, x + w, panelY + panelH, 0xFF1A1A1A);

        List<Component> components = new ArrayList<>();
        Component name = ScrollTooltipRenderer.name(lines, SAMPLE_CONTEXT);
        if (name != null) components.add(name);
        components.addAll(ScrollTooltipRenderer.preview(lines, SAMPLE_CONTEXT));
        if (components.isEmpty()) return;

        int boxW = 0;
        for (Component component : components) boxW = Math.max(boxW, this.font.width(component));
        int boxH = components.size() * (this.font.lineHeight + 1) - 1;

        // Keep a corner of the tooltip inside the panel no matter how far it was dragged —
        // otherwise it can be pushed out of sight with no way to bring it back.
        float scaledW = boxW * previewZoom;
        float scaledH = boxH * previewZoom;
        previewPanX = Math.max(-scaledW + 40, Math.min(w - PREVIEW_PAD - 20, previewPanX));
        previewPanY = Math.max(-scaledH + 20, Math.min(panelH - PREVIEW_PAD - 20, previewPanY));

        g.enableScissor(x, panelY, x + w, panelY + panelH);
        g.pose().pushPose();
        g.pose().translate(x + PREVIEW_PAD + previewPanX, panelY + PREVIEW_PAD + previewPanY, 0);
        g.pose().scale(previewZoom, previewZoom, 1.0f);

        // The real thing is a vanilla tooltip, so the preview draws vanilla's own background
        // rather than an editor-styled panel — a preview in a different skin than the item it
        // previews is worth less than no preview.
        TooltipRenderUtil.renderTooltipBackground(g, 0, 0, boxW, boxH, 0);
        int ly = 0;
        for (Component component : components) {
            g.drawString(this.font, component, 0, ly, 0xFFFFFF, true);
            ly += this.font.lineHeight + 1;
        }

        g.pose().popPose();
        g.disableScissor();

        ConfigEditorScreen.drawSmallText(g,
                Component.translatable("editor.historystages.scrolltip.preview_hint").getString(),
                x, panelY + panelH + 3, 0xFF666666);
    }

    // --- preview viewport ---

    private int previewX() { return contentRight() + 30; }

    private int previewTop() { return ROWS_TOP + 14; }

    private int previewW() { return this.width - previewX() - 24; }

    private int previewH() { return rowsBottom - previewTop() - 10; }

    private boolean overPreview(double mouseX, double mouseY) {
        return mouseX >= previewX() && mouseX < previewX() + previewW()
                && mouseY >= previewTop() && mouseY < previewTop() + previewH();
    }

    /** Header y for section {@code 0 = lines, 1 = templates, 2 = options}; matches {@link #layoutRows}. */
    private int headerY(List<PositionedRow> rows, int section) {
        int y = ROWS_TOP - Math.round(smoothScroll.value());
        if (section == 0) return y;
        y += SECTION_HEADER_H + ROW_H + MOVABLE_COUNT * ROW_H + SECTION_GAP;
        if (section == 1) return y;
        y += SECTION_HEADER_H + TEMPLATE_COUNT * ROW_H + SECTION_GAP;
        return y;
    }

    private void drawSectionHeader(GuiGraphics g, String key, int y) {
        if (y + SECTION_HEADER_H < ROWS_TOP || y > rowsBottom) return;
        g.fill(contentLeft(), y + 13, contentRight(), y + 14, 0xFF444444);
        g.drawString(this.font, Component.translatable(key).getString(), contentLeft(), y + 2, 0xFF888888, false);
    }

    /** True when the row's current line differs from its built-in default in any of its four fields. */
    private boolean isOverridden(String id) {
        ScrollTooltipLine def = DEFAULTS_BY_ID.get(id);
        ScrollTooltipLine cur = getLine(id);
        return def != null && cur != null && !cur.equals(def);
    }

    private void renderRow(GuiGraphics g, PositionedRow row, int mouseX, int mouseY) {
        String id = row.id();
        RowSpec spec = row.spec();
        int y = row.y();
        int left = contentLeft();
        int right = contentRight();

        boolean hovered = mouseX >= left && mouseX <= right
                && mouseY >= Math.max(y, ROWS_TOP) && mouseY < Math.min(y + ROW_H, rowsBottom);
        float hp = Ease.outCubic(rowHover.computeIfAbsent(id, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        if (hp > 0.001f) {
            g.fill(left, y, right, y + ROW_H - 2, Fade.rgba(0xFFFFFF, 0.08f * hp));
            g.fill(left, y, left + 1, y + ROW_H - 2, Fade.rgba(0xFFCC00, hp * 0.8f));
        }

        // The way back to the built-in default, in the gutter every row reserves. Only drawn
        // once the row actually differs from it — same convention ConfigRowList uses for its
        // clear-to-inherit ×, so the gutter reads as "what has been touched" at a glance.
        if (isOverridden(id)) {
            boolean resetHovered = mouseX >= left + 2 && mouseX < left + 2 + RESET_W
                    && mouseY >= y && mouseY < y + ROW_H;
            g.drawString(this.font, "✕", left + 2, y + (ROW_H - 8) / 2,
                    resetHovered ? 0xFFFF6666 : 0xFF888888, false);
        }

        ScrollTooltipLine line = getLine(id);

        int cx = rowContentLeft();
        if (spec.handle()) {
            boolean onHandle = mouseX >= cx && mouseX < cx + HANDLE_W && mouseY >= y && mouseY < y + ROW_H;
            drawHandle(g, cx + 1, y + (ROW_H - 12) / 2, onHandle);
        }
        cx += HANDLE_W + CONTROL_GAP;

        if (spec.checkbox()) {
            drawCheckbox(g, cx, y + (ROW_H - 10) / 2, line != null && line.enabled());
        }
        cx += CHECKBOX_W + CONTROL_GAP;

        if (spec.spacer()) {
            drawSpacerToggle(g, cx, y + (ROW_H - 10) / 2, line != null && line.spacerBefore());
        }
        cx += SPACER_W + CONTROL_GAP;

        String label = Component.translatable("editor.historystages.scrolltip.line." + id).getString();
        String shown = this.font.plainSubstrByWidth(label, Math.max(0, LABEL_W - 6));
        g.drawString(this.font, shown, cx, y + (ROW_H - 8) / 2, hp > 0.001f ? 0xFFFFFF : 0xDDDDDD, false);

        if (spec.colorSwatch()) {
            StyleState state = readStyleState(styleSource(line, id));
            Integer rgb = state.colorHex() != null ? GraphColors.parse(state.colorHex(), 0) : null;
            int sx = swatchX(spec);
            int sy = y + (ROW_H - SWATCH_SIZE) / 2;
            boolean swatchHovered = mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= y && mouseY < y + ROW_H;
            drawColorSwatch(g, sx, sy, rgb, swatchHovered);

        }
    }

    private void drawHandle(GuiGraphics g, int x, int y, boolean active) {
        int color = active ? 0xFFEEEEEE : 0xFF888888;
        for (int col = 0; col < 2; col++) {
            for (int row = 0; row < 3; row++) {
                int dx = x + col * 4;
                int dy = y + row * 4;
                g.fill(dx, dy, dx + 2, dy + 2, color);
            }
        }
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int border = checked ? 0xFFFFCC00 : 0xFF777777;
        g.fill(x, y, x + 10, y + 10, 0x40000000);
        g.fill(x, y, x + 10, y + 1, border);
        g.fill(x, y + 9, x + 10, y + 10, border);
        g.fill(x, y, x + 1, y + 10, border);
        g.fill(x + 9, y, x + 10, y + 10, border);
        if (checked) g.fill(x + 2, y + 2, x + 8, y + 8, 0xFFFFCC00);
    }

    /** Two short bars, standing for "blank line, then this one" — toggled on/off like a switch. */
    private void drawSpacerToggle(GuiGraphics g, int x, int y, boolean on) {
        int color = on ? 0xFFFFCC00 : 0xFF666666;
        g.fill(x, y, x + 10, y + 2, color);
        g.fill(x, y + 6, x + 10, y + 8, color);
    }

    /** Small colour chip; a checkerboard-free flat fill is enough since {@code null} already reads as "unset". */
    private void drawColorSwatch(GuiGraphics g, int x, int y, @Nullable Integer rgb, boolean hovered) {
        int border = hovered ? 0xFFFFCC00 : 0xFF555555;
        g.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, border);
        int fill = rgb != null ? (0xFF000000 | rgb) : 0xFF2A2A2A;
        g.fill(x + 1, y + 1, x + SWATCH_SIZE - 1, y + SWATCH_SIZE - 1, fill);
    }

    private void drawPulseOutline(GuiGraphics g, int y, float strength) {
        int alpha = (int) (0xFF * Math.max(0.0f, Math.min(1.0f, strength)));
        if (alpha < 4) return;
        int x1 = contentLeft() - 2, x2 = contentRight() + 2, y1 = y - 1, y2 = y + ROW_H - 1;
        int rgb = 0xFFCC00;
        g.fill(x1, y1, x2, y1 + 1, (alpha << 24) | rgb);
        g.fill(x1, y2 - 1, x2, y2, (alpha << 24) | rgb);
        g.fill(x1, y1, x1 + 1, y2, (alpha << 24) | rgb);
        g.fill(x2 - 1, y1, x2, y2, (alpha << 24) | rgb);
    }

    private void drawGhost(GuiGraphics g, int mouseX, int mouseY) {
        String id = lines.get(MOVABLE_START + dragFromSlot).id();
        String label = Component.translatable("editor.historystages.scrolltip.line." + id).getString();
        int gw = this.font.width(label) + 10;
        int gx = Math.min(mouseX + 8, this.width - gw - 4);
        int gy = Math.min(mouseY - 8, this.height - 18);
        g.pose().pushPose();
        g.pose().translate(0, 0, 410);
        g.fill(gx, gy, gx + gw, gy + 16, 0xE0101010);
        g.fill(gx, gy + 15, gx + gw, gy + 16, 0xFFFFCC00);
        g.drawString(this.font, label, gx + 5, gy + 4, 0xFFFFFF, false);
        g.pose().popPose();
    }

    // --- input ---

    private void updateScrollFromMouse(double mouseY) {
        int track = rowsBottom - ROWS_TOP;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - ROWS_TOP) / (double) track));
        scrollOffset = Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
        smoothScroll.set((float) scrollOffset);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Resets a line to its default: text, style, enabled and spacerBefore all restored, EditBox included. */
    private void resetLine(String id, ScrollTooltipLine def) {
        putLine(id, def);
    }

    private void openColorPickerFor(String id) {
        ScrollTooltipLine line = getLine(id);
        if (line == null) return;
        StyleState state = readStyleState(styleSource(line, id));
        ScrollTooltipLine def = DEFAULTS_BY_ID.get(id);
        String defaultHex = def == null ? null : readStyleState(styleSource(def, id)).colorHex();

        this.minecraft.setScreen(new ColorInputScreen(this,
                Component.translatable("editor.historystages.scrolltip.line." + id),
                state.colorHex() == null ? "" : state.colorHex(),
                defaultHex == null ? "" : defaultHex,
                hex -> applyColorPick(id, hex)));
    }

    /** Always writes hex: only a line the player actually edits through the picker loses its named colour. */
    private void applyColorPick(String id, String hex) {
        ScrollTooltipLine current = getLine(id);
        if (current == null) return;
        if (isColorOnlyOption(id)) {
            putLine(id, current.withText(hex));
        } else {
            StyleState state = readStyleState(current.style());
            putLine(id, current.withStyle(writeStyleState(hex, state.bold(), state.italic())));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Panning the preview is checked before the settle guard below: the preview sits well
        // clear of the row column, so nothing there is sliding under the cursor.
        if (button == 0 && overPreview(mouseX, mouseY)) {
            draggingPreview = true;
            previewDragLastX = mouseX;
            previewDragLastY = mouseY;
            return true;
        }

        // Rows still animate back into place for a short while after a drop; a click landing
        // in that window would hit whichever row happens to be sliding under the cursor rather
        // than the one the player is actually looking at — already paid for once in this editor.
        if (isSettling()) return false;

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (maxScroll > 0 && mouseX >= scrollbarX() - 1 && mouseX <= scrollbarX() + 4
                && mouseY >= ROWS_TOP && mouseY <= rowsBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if (mouseY < ROWS_TOP || mouseY >= rowsBottom) {
            return false;
        }

        List<PositionedRow> rows = layoutRows();
        int left = contentLeft();
        int rowLeft = rowContentLeft();
        int resetX = left + 2;
        int checkboxX = rowLeft + HANDLE_W + CONTROL_GAP;
        int spacerX = checkboxX + CHECKBOX_W + CONTROL_GAP;

        for (PositionedRow row : rows) {
            if (mouseY < row.y() || mouseY >= row.y() + ROW_H) continue;
            RowSpec spec = row.spec();
            String id = row.id();

            if (isOverridden(id) && mouseX >= resetX && mouseX < resetX + RESET_W) {
                resetLine(id, DEFAULTS_BY_ID.get(id));
                playClick();
                return true;
            }

            if (spec.handle() && mouseX >= rowLeft && mouseX < rowLeft + HANDLE_W) {
                int slot = movableSlotOf(id);
                if (slot >= 0) {
                    dragArmed = true;
                    dragStarted = false;
                    pressX = mouseX;
                    pressY = mouseY;
                    dragFromSlot = slot;
                    dragTargetSlot = slot;
                }
                return true;
            }
            if (spec.checkbox() && mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_W) {
                ScrollTooltipLine line = getLine(id);
                if (line != null) putLine(id, line.withEnabled(!line.enabled()));
                playClick();
                return true;
            }
            if (spec.spacer() && mouseX >= spacerX && mouseX < spacerX + SPACER_W) {
                ScrollTooltipLine line = getLine(id);
                if (line != null) putLine(id, line.withSpacerBefore(!line.spacerBefore()));
                playClick();
                return true;
            }
            if (spec.text() && mouseX >= textFieldX()
                    && mouseX < textFieldX() + textFieldWidth(spec)) {
                openTextEditorFor(id);
                return true;
            }
            if (spec.colorSwatch()) {
                int sx = swatchX(spec);
                if (mouseX >= sx && mouseX < sx + SWATCH_SIZE) {
                    openColorPickerFor(id);
                    return true;
                }
            }
            return true; // inside the row but on dead space between controls — swallow the click
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview) {
            previewPanX += (float) (mouseX - previewDragLastX);
            previewPanY += (float) (mouseY - previewDragLastY);
            previewDragLastX = mouseX;
            previewDragLastY = mouseY;
            return true;
        }
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (dragArmed && button == 0) {
            if (!dragStarted) {
                double dx = mouseX - pressX;
                double dy = mouseY - pressY;
                if (dx * dx + dy * dy > (double) DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    dragStarted = true;
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPreview) {
            draggingPreview = false;
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (dragArmed && button == 0) {
            if (dragStarted) {
                int target = computeDragTargetSlot(mouseY, movableTop());
                if (target != dragFromSlot) {
                    reorderMovable(dragFromSlot, target);
                    writeBack();
                    pulseId = lines.get(MOVABLE_START + target).id();
                    pulseStart = System.currentTimeMillis();
                    playClick();
                }
            }
            dragArmed = false;
            dragStarted = false;
            dragFromSlot = -1;
            dragTargetSlot = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Over the preview the wheel zooms instead of scrolling the row list — the two never
        // overlap on screen, so which one the player means is unambiguous.
        if (overPreview(mouseX, mouseY)) {
            previewZoom = Math.max(PREVIEW_ZOOM_MIN,
                    Math.min(PREVIEW_ZOOM_MAX, previewZoom + (float) scrollY * 0.1f));
            return true;
        }
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 16));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
