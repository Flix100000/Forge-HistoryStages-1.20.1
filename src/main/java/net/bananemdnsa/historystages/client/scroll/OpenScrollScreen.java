package net.bananemdnsa.historystages.client.scroll;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.ClientToastHandler;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapter;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterMode;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapters;
import net.bananemdnsa.historystages.data.scroll.OpenScrollContent;
import net.bananemdnsa.historystages.data.scroll.OpenScrollDocument;
import net.bananemdnsa.historystages.data.scroll.OpenScrollEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollMarker;
import net.bananemdnsa.historystages.data.scroll.OpenScrollOverviewBlockEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollOverviewBlocks;
import net.bananemdnsa.historystages.data.scroll.OpenScrollSort;
import net.bananemdnsa.historystages.data.scroll.OpenScrollVisibility;
import net.bananemdnsa.historystages.data.scroll.OpenScrollWorldGroup;
import net.bananemdnsa.historystages.screen.OpenScrollGeometry;
import net.bananemdnsa.historystages.screen.OpenScrollPagination;
import net.bananemdnsa.historystages.screen.OpenScrollTabs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The document a right-clicked Open Scroll shows: what its stage unlocked, in chapters.
 *
 * <p>Pure client state. Stage definitions, graph descriptions and the reader's own unlock state
 * are all on the client already, so this screen needs no menu, no block entity and no packet.
 *
 * <p>Every control here is drawn from ink and paper rather than borrowed from the config editor:
 * the chapters are words in the header, the search is a chevron on a writing rule, and the reader
 * turns sheets instead of scrolling. Nothing in this file imports a widget.
 */
public class OpenScrollScreen extends Screen {

    private static final ResourceLocation SHEET =
            new ResourceLocation(HistoryStages.MOD_ID, "textures/gui/scroll/open_gui.png");

    /** Vanilla's Standard Galactic font — the one the enchanting table uses. No asset of ours. */
    private static final ResourceLocation GLYPH_FONT = new ResourceLocation("alt");

    /** Laid over a locked item so its shape survives but its identity does not. */
    private static final int SILHOUETTE = 0xB0241A0F;
    /** Replaces the icon entirely when the stage hides even the name — a hole in the parchment. */
    private static final int HOLE = 0x8C3C2D1C;

    /** Line advance for wrapped body text. Font lines are 9px; the extra pixel is leading. */
    private static final int LINE_ADVANCE = 10;

    /** How large an entity is drawn inside a cell. Eyeballed; tune against the real screen. */
    private static final int ENTITY_SCALE = 7;

    /** The overview icon is drawn at double size, which is why the pose scales rather than the call. */
    private static final int OVERVIEW_ICON_SIZE = 32;

    private final OpenScrollDocument document;
    private final List<OpenScrollChapterEntry> chapters;

    /**
     * Whether a reader without the stage sees its entries or only silhouettes. Read once in the
     * constructor: the config cannot change while the screen is open, and the chapters that draw
     * locked entries all have to agree on the answer.
     */
    private final OpenScrollVisibility visibility;

    /**
     * True when the scroll names a stage the pack no longer defines. Kept as its own flag rather
     * than inferred from an empty document, because a perfectly valid stage may unlock nothing and
     * carry no display name, and that reader deserves the counts page, not an error.
     */
    private final boolean unknownStage;

    /**
     * Whether this stage's own entries are hidden from this reader. Creature and world rows have
     * no per-entry lock test the way items do, so they all share this one answer.
     */
    private final boolean stageHidden;

    /** True when the stage asks for its locked names to vanish rather than merely blur. */
    private final boolean hidesName;

    // --- appearance, all resolved once: the config cannot change while the screen is open ---

    private final int inkHeading;
    private final int inkBody;
    private final int inkFaint;
    /** Alpha-shifted backdrop colour, from the client config's 0-100 percentage. */
    private final int backdrop;

    private final boolean showSearch;
    private final boolean showEntryIds;
    private final OpenScrollSort sort;
    private final List<OpenScrollOverviewBlockEntry> overviewBlocks;

    private int leftPos;
    private int topPos;
    private int activeTab;

    /**
     * A borderless vanilla {@link EditBox} sitting on the ink line.
     *
     * <p>Hand-rolling the field gave the right look but only handled typing and backspace, so
     * Ctrl+A, Ctrl+V, selection, word jumps and the cursor keys all did nothing. Vanilla's box
     * brings every one of those for free; stripping its border and colouring its text is enough to
     * keep it looking like ink on a rule rather than a widget.
     */
    private EditBox search;

    /** Mirror of the box's text, kept by its responder so the content builders stay widget-free. */
    private String filter = "";

    private int sheet;
    private int sheetCount = 1;
    private boolean footerShown;

    /**
     * The visible entries of the active chapter, rebuilt only when the tab or the filter changes.
     * Rebuilding per frame would allocate an {@link ItemStack} per entry sixty times a second.
     */
    private List<IconCell> cells = List.of();
    private List<TextRow> rows = List.of();
    private boolean contentStale = true;

    /** Panel-relative positions of the chapter words, rebuilt with the content. */
    private List<OpenScrollTabs.Tab> tabs = List.of();

    /** Index of the first row on each sheet; only meaningful while a text chapter is shown. */
    private List<Integer> rowStarts = List.of(0);
    private int cellsPerSheet = 1;

    /** The description's wrapped lines, measured once so paging and drawing agree. */
    private List<FormattedCharSequence> descriptionLines = List.of();
    /** For each sheet: index of the first description line it shows. */
    private List<Integer> overviewStarts = List.of(0);

    /**
     * One entry of an icon chapter. {@code entityId} is null for items, {@code stack} for creatures.
     * {@code sortKey} carries the display name so sorting never re-resolves it.
     */
    private record IconCell(ItemStack stack, String entityId, boolean hidden,
                            List<Component> tooltip, String sortKey) {}

    /** One line of a text chapter: either a group heading or an entry. */
    private record TextRow(Component text, boolean heading, Component tooltip, String sortKey) {}

    public OpenScrollScreen(String stageId) {
        super(Component.translatable("gui.historystages.open_scroll.title"));

        boolean individual = StageManager.isIndividualStage(stageId);
        StageEntry entry = individual
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);

        this.unknownStage = entry == null;
        if (entry == null) {
            this.document = OpenScrollContent.unknown(stageId);
        } else {
            String description = GraphStageData.get().description(stageId, individual);
            this.document = OpenScrollContent.build(stageId, individual, entry,
                    ClientTagResolver.INSTANCE, description == null ? "" : description);
        }

        this.visibility = OpenScrollVisibility.parse(Config.COMMON.openScrollLockedDisplay.get());
        this.hidesName = entry != null
                && entry.getHiddenDisplay().getNameMode() == DisplayMode.HIDDEN;
        this.stageHidden = visibility.hidesLocked() && !readerHasStage(stageId, individual);
        this.chapters = visibleChapters();

        this.inkHeading = GraphColors.parse(Config.COMMON.openScrollInkHeading.get(), 0x3F2D13);
        this.inkBody = GraphColors.parse(Config.COMMON.openScrollInkBody.get(), 0x4A3416);
        this.inkFaint = GraphColors.parse(Config.COMMON.openScrollInkFaint.get(), 0x7A5A2C);
        int percent = Math.max(0, Math.min(100, Config.CLIENT.openScrollBackdrop.get()));
        this.backdrop = (percent * 255 / 100) << 24 | 0x101010;
        this.showSearch = Config.COMMON.openScrollShowSearch.get();
        this.showEntryIds = Config.COMMON.openScrollShowEntryIds.get();
        this.sort = OpenScrollSort.parse(Config.COMMON.openScrollEntrySort.get());
        this.overviewBlocks = OpenScrollOverviewBlocks.parse(
                Config.COMMON.openScrollOverviewBlocks.get());
    }

    private static boolean readerHasStage(String stageId, boolean individual) {
        return individual
                ? ClientIndividualStageCache.isStageUnlocked(stageId)
                : ClientStageCache.isStageUnlocked(stageId);
    }

    /** Enabled chapters that actually have content. The overview always survives. */
    private List<OpenScrollChapterEntry> visibleChapters() {
        List<OpenScrollChapterEntry> out = new ArrayList<>();
        for (OpenScrollChapterEntry entry : OpenScrollChapters.parse(Config.COMMON.openScrollChapters.get())) {
            if (!entry.enabled() || document.isEmpty(entry.chapter())) continue;
            out.add(entry);
        }
        if (out.isEmpty()) {
            out.add(new OpenScrollChapterEntry(OpenScrollChapter.OVERVIEW, true, OpenScrollChapterMode.TEXT));
        }
        return out;
    }

    /** The chapter the reader is looking at. Never null — {@link #visibleChapters()} guarantees one. */
    private OpenScrollChapter activeChapter() {
        return chapters.get(activeTab).chapter();
    }

    private boolean drawsIcons() {
        return chapters.get(activeTab).mode() == OpenScrollChapterMode.ICONS
                && activeChapter() != OpenScrollChapter.OVERVIEW;
    }

    /** True when the search line is both configured on and meaningful for this chapter. */
    private boolean searchShown() {
        return showSearch && activeChapter() != OpenScrollChapter.OVERVIEW;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - OpenScrollGeometry.WIDTH) / 2;
        this.topPos = (this.height - OpenScrollGeometry.HEIGHT) / 2;
        if (activeTab >= chapters.size()) activeTab = 0;
        // Word widths depend on the font, which only exists once the screen is initialised.
        rebuildTabs();

        // Starts 9px in, leaving room for the chevron, and one pixel high of the band so the
        // box's own vertical centring lands the text exactly on SEARCH_Y.
        search = new InkSearchBox(leftPos + OpenScrollGeometry.CONTENT_X + 9,
                topPos + OpenScrollGeometry.SEARCH_Y - 1,
                OpenScrollGeometry.CONTENT_WIDTH - 9, 10,
                Component.translatable("gui.historystages.open_scroll.search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setValue(filter);
        search.setResponder(this::setFilter);
        addRenderableWidget(search);
        updateSearchVisibility();

        contentStale = true;
    }

    /** The overview page has nothing to search, so the box leaves rather than sitting there inert. */
    private void updateSearchVisibility() {
        if (search == null) return;
        boolean shown = searchShown();
        search.visible = shown;
        search.active = shown;
        if (!shown) search.setFocused(false);
    }

    // --- content ---

    private void rebuildIfStale() {
        if (!contentStale) return;
        contentStale = false;
        cells = List.of();
        rows = List.of();
        rebuildTabs();

        switch (activeChapter()) {
            case OVERVIEW -> { /* the overview page draws straight from the document */ }
            case ITEMS -> {
                if (drawsIcons()) cells = itemCells();
                else rows = itemRows();
            }
            case CREATURES -> {
                if (drawsIcons()) cells = creatureCells();
                else rows = creatureRows();
            }
            case WORLD -> rows = worldRows();
        }
        repaginate();
    }

    private void rebuildTabs() {
        List<String> labels = new ArrayList<>();
        for (OpenScrollChapterEntry entry : chapters) {
            labels.add(Component.translatable("gui.historystages.open_scroll.chapter."
                    + entry.chapter().serialize()).getString());
        }
        tabs = OpenScrollTabs.layout(labels, activeTab, OpenScrollGeometry.CONTENT_WIDTH,
                this.font::width);
    }

    private List<IconCell> itemCells() {
        List<IconCell> out = new ArrayList<>();
        for (String id : document.itemIds()) {
            ItemStack stack = ClientToastHandler.resolveIcon(id);
            if (stack.isEmpty()) continue;
            boolean hidden = isHidden(stack);
            String shown = stack.getHoverName().getString();
            if (!matchesFilter(shown, id, hidden)) continue;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(name(shown, hidden));
            // A hidden entry never reveals its id: that is the word the glyphs are withholding.
            if (showEntryIds && !hidden) tooltip.add(idLine(id));
            out.add(new IconCell(stack, null, hidden, tooltip, shown));
        }
        sortCells(out);
        return out;
    }

    private List<TextRow> itemRows() {
        List<TextRow> out = new ArrayList<>();
        for (String id : document.itemIds()) {
            ItemStack stack = ClientToastHandler.resolveIcon(id);
            if (stack.isEmpty()) continue;
            boolean hidden = isHidden(stack);
            String shown = stack.getHoverName().getString();
            if (!matchesFilter(shown, id, hidden)) continue;
            out.add(new TextRow(name(shown, hidden), false, idTooltip(id, hidden), shown));
        }
        sortRows(out);
        return out;
    }

    private List<IconCell> creatureCells() {
        List<IconCell> out = new ArrayList<>();
        for (OpenScrollEntry entry : document.creatures()) {
            String shown = OpenScrollNames.creature(entry.id());
            if (!matchesFilter(shown, entry.id(), stageHidden)) continue;
            out.add(new IconCell(ItemStack.EMPTY, entry.id(), stageHidden,
                    creatureTooltip(entry, shown), shown));
        }
        sortCells(out);
        return out;
    }

    private List<TextRow> creatureRows() {
        List<TextRow> out = new ArrayList<>();
        for (OpenScrollEntry entry : document.creatures()) {
            String shown = OpenScrollNames.creature(entry.id());
            if (!matchesFilter(shown, entry.id(), stageHidden)) continue;
            out.add(new TextRow(name(shown, stageHidden), false,
                    idTooltip(entry.id(), stageHidden), shown));
        }
        sortRows(out);
        return out;
    }

    /** The entity's name, then one line per lock kind it came from. */
    private List<Component> creatureTooltip(OpenScrollEntry entry, String shown) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(shown, stageHidden));
        for (OpenScrollMarker marker : OpenScrollMarker.values()) {
            if (!entry.markers().contains(marker)) continue;
            lines.add(Component.translatable("gui.historystages.open_scroll.marker."
                    + marker.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        }
        if (showEntryIds && !stageHidden) lines.add(idLine(entry.id()));
        return lines;
    }

    private List<TextRow> worldRows() {
        List<TextRow> out = new ArrayList<>();
        for (OpenScrollWorldGroup group : document.world()) {
            List<TextRow> entries = new ArrayList<>();
            for (String id : group.ids()) {
                String shown = worldName(group, id);
                if (!matchesFilter(shown, id, stageHidden)) continue;
                entries.add(new TextRow(name(shown, stageHidden), false,
                        idTooltip(id, stageHidden), shown));
            }
            // A heading whose entries were all filtered away would stand over nothing.
            if (entries.isEmpty()) continue;
            // Sorting stays inside a group: across groups it would mix biomes into structures.
            sortRows(entries);
            out.add(new TextRow(Component.translatable(group.labelKey()), true, null, ""));
            out.addAll(entries);
        }
        return out;
    }

    /** The label key tells the groups apart; the record carries no kind field. */
    private String worldName(OpenScrollWorldGroup group, String id) {
        String key = group.labelKey();
        if (key.endsWith("dimensions")) return OpenScrollNames.dimension(id);
        if (key.endsWith("structures")) return OpenScrollNames.structure(id);
        return OpenScrollNames.biome(id);
    }

    private void sortCells(List<IconCell> list) {
        if (!sort.sortsByName()) return;
        list.sort(Comparator.comparing(IconCell::sortKey, String.CASE_INSENSITIVE_ORDER));
    }

    private void sortRows(List<TextRow> list) {
        if (!sort.sortsByName()) return;
        list.sort(Comparator.comparing(TextRow::sortKey, String.CASE_INSENSITIVE_ORDER));
    }

    /** The raw id belongs in the tooltip, and only when the pack wants it there. */
    private Component idLine(String id) {
        return Component.literal(id).withStyle(ChatFormatting.DARK_GRAY);
    }

    private Component idTooltip(String id, boolean hidden) {
        return showEntryIds && !hidden ? idLine(id) : null;
    }

    /**
     * Now that rows carry a display name, the raw id is worth matching too — a pack maker looking
     * for {@code iron_ingot} should find it. Hidden entries stay out of the search entirely: a hit
     * would hand over exactly the word the glyphs are there to withhold.
     */
    private boolean matchesFilter(String displayName, String id, boolean hidden) {
        if (filter.isEmpty()) return true;
        if (hidden) return false;
        String needle = filter.toLowerCase(Locale.ROOT);
        return displayName.toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Component name(String text, boolean hidden) {
        return hidden ? glyphs(text) : Component.literal(text);
    }

    private static Component glyphs(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(GLYPH_FONT));
    }

    /** Items answer through the same check the inventory lock overlay uses, so the two agree. */
    private boolean isHidden(ItemStack stack) {
        return visibility.hidesLocked() && LockIconRenderer.iconFor(stack) != null;
    }

    // --- pagination ---

    private void repaginate() {
        if (activeChapter() == OpenScrollChapter.OVERVIEW) {
            repaginateOverview();
        } else if (drawsIcons()) {
            OpenScrollPagination.Sheets sheets = OpenScrollPagination.ofUniform(cells.size(),
                    OpenScrollGeometry.cellsPerSheet(searchShown(), false),
                    OpenScrollGeometry.cellsPerSheet(searchShown(), true));
            sheetCount = sheets.count();
            footerShown = sheets.footerShown();
            cellsPerSheet = sheets.capacity();
        } else {
            List<OpenScrollPagination.Row> measured = new ArrayList<>();
            for (TextRow row : rows) {
                measured.add(new OpenScrollPagination.Row(rowHeight(row), row.heading()));
            }
            OpenScrollPagination.Result result = OpenScrollPagination.ofRows(measured,
                    OpenScrollGeometry.contentHeight(searchShown(), false),
                    OpenScrollGeometry.contentHeight(searchShown(), true));
            rowStarts = result.starts();
            sheetCount = rowStarts.size();
            footerShown = result.footerShown();
        }
        if (sheet >= sheetCount) sheet = sheetCount - 1;
    }

    /**
     * The overview's fixed blocks are redrawn on every sheet, the way a running header repeats in a
     * book, so their height comes off each sheet's budget rather than only the first. Only the
     * description may split.
     */
    private void repaginateOverview() {
        descriptionLines = document.description().isEmpty()
                ? List.of()
                : this.font.split(Component.literal(document.description()),
                        OpenScrollGeometry.CONTENT_WIDTH);

        int fixed = 0;
        for (OpenScrollOverviewBlockEntry entry : overviewBlocks) {
            if (!entry.enabled()) continue;
            fixed += switch (entry.block()) {
                case ICON -> OVERVIEW_ICON_SIZE + 2;
                case TITLE -> LINE_ADVANCE + 1;
                // Three stacked lines, see drawOverviewCounts.
                case COUNTS -> 3 * LINE_ADVANCE + 1;
                case DESCRIPTION -> 0;
            };
        }

        int top = OpenScrollGeometry.RULE_TOP_Y + 4;
        int linesWithoutFooter =
                Math.max(0, OpenScrollGeometry.parchmentBottom() - top - fixed) / LINE_ADVANCE;
        // Same one-shot resolution as OpenScrollPagination: measure against the roomier sheet
        // first, and only fall back to the footer sheet if that overflows.
        if (descriptionLines.size() <= linesWithoutFooter) {
            overviewStarts = List.of(0);
            sheetCount = 1;
            footerShown = false;
            return;
        }

        int perSheet = Math.max(1,
                Math.max(0, OpenScrollGeometry.contentBottom(true) - top - fixed) / LINE_ADVANCE);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < descriptionLines.size(); i += perSheet) starts.add(i);
        overviewStarts = starts;
        sheetCount = starts.size();
        footerShown = true;
    }

    private static int rowHeight(TextRow row) {
        return row.heading() ? OpenScrollGeometry.TEXT_GROUP_HEIGHT : OpenScrollGeometry.TEXT_ROW_HEIGHT;
    }

    // --- rendering ---

    // No renderBackground override here: unlike 1.21, Screen#render on 1.20.1 never calls it
    // automatically, so overriding it would be dead code. The dimmed backdrop below is drawn
    // directly at the top of render() instead.

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuildIfStale();
        // Dimmed rather than blurred: the world keeps running behind a scroll you are only reading.
        g.fill(0, 0, this.width, this.height, backdrop);
        // One blit of the whole sheet, offset so the drawn region lands on the panel. Blitting the
        // region alone would centre the screen on the sheet's empty margins instead.
        g.blit(SHEET, leftPos - OpenScrollGeometry.SHEET_X, topPos - OpenScrollGeometry.SHEET_Y,
                0, 0, OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE,
                OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE);
        renderTabs(g);

        if (activeChapter() == OpenScrollChapter.OVERVIEW) {
            renderOverview(g);
        } else {
            if (showSearch) renderSearchLine(g);
            if (drawsIcons()) renderGrid(g, mouseX, mouseY);
            else renderRows(g, mouseX, mouseY);
            renderEmptyNotice(g);
        }
        renderFooter(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g) {
        int x = leftPos + OpenScrollGeometry.CONTENT_X;
        int y = topPos + OpenScrollGeometry.TABS_Y;
        for (int i = 0; i < tabs.size(); i++) {
            OpenScrollTabs.Tab tab = tabs.get(i);
            boolean active = i == activeTab;
            g.drawString(this.font, tab.label(), x + tab.x(), y, active ? inkHeading : inkFaint, false);
            // The underline is what marks the active chapter — no coloured box, no second heading.
            if (active) {
                g.fill(x + tab.x(), y + OpenScrollGeometry.TABS_HEIGHT,
                        x + tab.x() + tab.width(), y + OpenScrollGeometry.TABS_HEIGHT + 1, inkHeading);
            }
        }
        g.fill(x, topPos + OpenScrollGeometry.RULE_TOP_Y,
                x + OpenScrollGeometry.CONTENT_WIDTH,
                topPos + OpenScrollGeometry.RULE_TOP_Y + 1, withAlpha(inkFaint, 0x59));
    }

    /** An ink rule is the same ink, thinned — not a separate configurable colour. */
    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** Only the chevron and the writing rule — the text and caret belong to the {@link EditBox}. */
    private void renderSearchLine(GuiGraphics g) {
        int x = leftPos + OpenScrollGeometry.CONTENT_X;
        int y = topPos + OpenScrollGeometry.SEARCH_Y;
        boolean focused = search != null && search.isFocused();
        g.drawString(this.font, "»", x, y, focused ? inkHeading : inkFaint, false);
        g.fill(x + 9, y + 9, x + OpenScrollGeometry.CONTENT_WIDTH, y + 10,
                withAlpha(inkFaint, focused ? 0xB0 : 0x73));
    }

    private void renderFooter(GuiGraphics g) {
        if (!footerShown) return;
        int x = leftPos + OpenScrollGeometry.CONTENT_X;
        g.fill(x, topPos + OpenScrollGeometry.RULE_BOTTOM_Y,
                x + OpenScrollGeometry.CONTENT_WIDTH,
                topPos + OpenScrollGeometry.RULE_BOTTOM_Y + 1, withAlpha(inkFaint, 0x59));

        String line = "‹  " + Component.translatable("gui.historystages.open_scroll.sheet",
                sheet + 1, sheetCount).getString() + "  ›";
        g.drawString(this.font, line,
                x + (OpenScrollGeometry.CONTENT_WIDTH - this.font.width(line)) / 2,
                topPos + OpenScrollGeometry.FOOT_Y, inkFaint, false);
    }

    // --- the overview page ---

    private void renderOverview(GuiGraphics g) {
        int x = leftPos + OpenScrollGeometry.CONTENT_X;
        int width = OpenScrollGeometry.CONTENT_WIDTH;
        int y = topPos + OpenScrollGeometry.RULE_TOP_Y + 4;

        if (unknownStage) {
            drawCentredWrapped(g, Component.translatable("gui.historystages.open_scroll.unknown_stage"),
                    x, y, width);
            return;
        }

        for (OpenScrollOverviewBlockEntry entry : overviewBlocks) {
            if (!entry.enabled()) continue;
            y = switch (entry.block()) {
                case ICON -> drawOverviewIcon(g, x, y, width);
                case TITLE -> drawOverviewTitle(g, x, y, width);
                case DESCRIPTION -> drawOverviewDescription(g, x, y);
                case COUNTS -> drawOverviewCounts(g, x, y, width);
            };
        }
    }

    /** renderItem always draws 16x16, and the page wants 32x32, so the pose does the doubling. */
    private int drawOverviewIcon(GuiGraphics g, int x, int y, int width) {
        ItemStack icon = ClientToastHandler.resolveIcon(document.iconId());
        g.pose().pushPose();
        g.pose().translate((float) (x + (width - OVERVIEW_ICON_SIZE) / 2), (float) y, 0.0f);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        return y + OVERVIEW_ICON_SIZE + 2;
    }

    private int drawOverviewTitle(GuiGraphics g, int x, int y, int width) {
        Component title = Component.literal(document.displayName());
        g.drawString(this.font, title, x + (width - this.font.width(title)) / 2, y, inkHeading, false);
        return y + LINE_ADVANCE + 1;
    }

    private int drawOverviewDescription(GuiGraphics g, int x, int y) {
        int first = overviewStarts.get(sheet);
        int last = sheet + 1 < overviewStarts.size()
                ? overviewStarts.get(sheet + 1) : descriptionLines.size();
        for (int i = first; i < last; i++) {
            g.drawString(this.font, descriptionLines.get(i), x, y, inkBody, false);
            y += LINE_ADVANCE;
        }
        return y + 1;
    }

    /**
     * Three stacked lines rather than one "a · b · c" run: at 132px the single line ran past the
     * text block on any language with longer words, and a tally that overflows the page is worse
     * than a tally that takes three rows.
     */
    private int drawOverviewCounts(GuiGraphics g, int x, int y, int width) {
        int[] values = {document.itemCount(), document.creatureCount(), document.worldCount()};
        String[] keys = {"items", "creatures", "world"};
        for (int i = 0; i < keys.length; i++) {
            Component line = Component.translatable(
                    "gui.historystages.open_scroll.counts." + keys[i], values[i]);
            g.drawString(this.font, line, x + (width - this.font.width(line)) / 2, y, inkFaint, false);
            y += LINE_ADVANCE;
        }
        return y + 1;
    }

    private void drawCentredWrapped(GuiGraphics g, Component text, int x, int y, int width) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            g.drawString(this.font, line, x + (width - this.font.width(line)) / 2, y, inkBody, false);
            y += LINE_ADVANCE;
        }
    }

    // --- the icon grid ---

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int top = topPos + OpenScrollGeometry.contentY(searchShown());
        int first = sheet * cellsPerSheet;
        int last = Math.min(cells.size(), first + cellsPerSheet);
        IconCell hovered = null;

        for (int i = first; i < last; i++) {
            int index = i - first;
            int x = leftPos + OpenScrollGeometry.cellX(index % OpenScrollGeometry.COLUMNS);
            int y = OpenScrollGeometry.cellY(top, index / OpenScrollGeometry.COLUMNS);
            renderCell(g, cells.get(i), x, y);
            if (mouseX >= x && mouseX < x + OpenScrollGeometry.CELL
                    && mouseY >= y && mouseY < y + OpenScrollGeometry.CELL) {
                hovered = cells.get(i);
            }
        }
        if (hovered != null) {
            g.renderComponentTooltip(this.font, hovered.tooltip(), mouseX, mouseY);
        }
    }

    /**
     * The ruled box every entry sits in. Drawn in ink rather than as a vanilla slot texture: a
     * grey stone slot on parchment is the same borrowed-widget mistake the search box used to be.
     * Its job is grouping — without it the icons float and nothing says where one entry ends.
     */
    private void drawSlot(GuiGraphics g, int x, int y) {
        int size = OpenScrollGeometry.CELL;
        g.fill(x, y, x + size, y + size, withAlpha(inkFaint, 0x14));
        g.fill(x, y, x + size, y + 1, withAlpha(inkFaint, 0x40));
        g.fill(x, y + size - 1, x + size, y + size, withAlpha(inkFaint, 0x40));
        g.fill(x, y, x + 1, y + size, withAlpha(inkFaint, 0x40));
        g.fill(x + size - 1, y, x + size, y + size, withAlpha(inkFaint, 0x40));
    }

    private void renderCell(GuiGraphics g, IconCell cell, int x, int y) {
        drawSlot(g, x, y);
        // A stage that hides even the name gets a hole in the parchment rather than a shape.
        if (cell.hidden() && hidesName) {
            g.fill(x + 1, y + 1, x + OpenScrollGeometry.CELL - 1, y + OpenScrollGeometry.CELL - 1, HOLE);
            return;
        }
        if (cell.entityId() != null) {
            renderCreature(g, cell, x, y);
            return;
        }
        g.renderItem(cell.stack(), x + 1, y + 1);
        if (cell.hidden()) {
            // Inset by one so the wash darkens the item without swallowing the box around it.
            g.fill(x + 1, y + 1, x + OpenScrollGeometry.CELL - 1,
                    y + OpenScrollGeometry.CELL - 1, SILHOUETTE);
        }
    }

    /**
     * A locked creature is drawn as a hole rather than a tinted shape: an entity render cannot be
     * washed over the way a flat item icon can, and a recognisable silhouette would give the mob
     * away anyway.
     */
    private void renderCreature(GuiGraphics g, IconCell cell, int x, int y) {
        if (cell.hidden()) {
            g.fill(x + 1, y + 1, x + OpenScrollGeometry.CELL - 1, y + OpenScrollGeometry.CELL - 1, HOLE);
            return;
        }
        LivingEntity entity = EntityPreviewRenderer.getOrCreate(cell.entityId());
        if (entity == null) return;
        // Angle 0: thirty-five spinning mobs at once is a fairground, not a document.
        EntityPreviewRenderer.renderSpinning(g, x + OpenScrollGeometry.CELL / 2,
                y + OpenScrollGeometry.CELL - 1, ENTITY_SCALE, 0.0f, entity);
    }

    // --- the text list ---

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + OpenScrollGeometry.CONTENT_X;
        int y = topPos + OpenScrollGeometry.contentY(searchShown());
        int first = rowStarts.get(sheet);
        int last = sheet + 1 < rowStarts.size() ? rowStarts.get(sheet + 1) : rows.size();
        TextRow hovered = null;

        for (int i = first; i < last; i++) {
            TextRow row = rows.get(i);
            int height = rowHeight(row);
            if (row.heading()) {
                // A group heading is underlined rather than boxed: it labels the rows below it,
                // it is not one of them.
                g.fill(x, y + height - 2, x + OpenScrollGeometry.CONTENT_WIDTH, y + height - 1,
                        withAlpha(inkFaint, 0x40));
            } else {
                g.fill(x, y - 1, x + OpenScrollGeometry.CONTENT_WIDTH, y + height - 2,
                        withAlpha(inkFaint, 0x12));
            }
            g.drawString(this.font, row.text(), row.heading() ? x : x + 2, y,
                    row.heading() ? inkFaint : inkBody, false);
            if (!row.heading() && row.tooltip() != null
                    && mouseX >= x && mouseX < x + OpenScrollGeometry.CONTENT_WIDTH
                    && mouseY >= y && mouseY < y + height) {
                hovered = row;
            }
            y += height;
        }
        if (hovered != null) {
            g.renderTooltip(this.font, hovered.tooltip(), mouseX, mouseY);
        }
    }

    private void renderEmptyNotice(GuiGraphics g) {
        if (filter.isEmpty() || !cells.isEmpty() || !rows.isEmpty()) return;
        g.drawString(this.font, Component.translatable("gui.historystages.open_scroll.no_results"),
                leftPos + OpenScrollGeometry.CONTENT_X,
                topPos + OpenScrollGeometry.contentY(searchShown()), inkFaint, false);
    }

    // --- turning sheets ---

    private void turnTo(int target) {
        int clamped = Math.max(0, Math.min(sheetCount - 1, target));
        if (clamped == sheet) return;
        sheet = clamped;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (sheetCount <= 1) return false;
        turnTo(sheet - (int) Math.signum(delta));
        return true;
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = leftPos + OpenScrollGeometry.CONTENT_X;
        int ty = topPos + OpenScrollGeometry.TABS_Y;
        if (mouseY >= ty && mouseY < ty + OpenScrollGeometry.TABS_HEIGHT) {
            for (int i = 0; i < tabs.size(); i++) {
                OpenScrollTabs.Tab tab = tabs.get(i);
                if (mouseX < px + tab.x() || mouseX >= px + tab.x() + tab.width()) continue;
                if (i != activeTab) {
                    activeTab = i;
                    sheet = 0;
                    contentStale = true;
                    updateSearchVisibility();
                }
                return true;
            }
        }

        if (footerShown) {
            int fy = topPos + OpenScrollGeometry.FOOT_Y;
            if (mouseY >= fy && mouseY < fy + OpenScrollGeometry.FOOT_HEIGHT) {
                boolean left = mouseX < px + OpenScrollGeometry.CONTENT_WIDTH / 2.0;
                turnTo(sheet + (left ? -1 : 1));
                return true;
            }
        }

        // A click anywhere but the box drops its focus. Screen only ever *gives* focus to a child
        // that consumed the click; it never takes it back, so a field would otherwise keep
        // swallowing the keyboard after the reader had visibly moved on.
        if (search != null && search.visible && !overSearch(mouseX, mouseY)) {
            search.setFocused(false);
            setFocused(null);
        }

        // Everything else, the search box included, is a widget — let Screen route the click so
        // focus, selection and double-click-to-select-a-word all behave the way they do elsewhere.
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overSearch(double mouseX, double mouseY) {
        return mouseX >= search.getX() && mouseX < search.getX() + search.getWidth()
                && mouseY >= search.getY() && mouseY < search.getY() + search.getHeight();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape closes the document even while the search line has focus, which is what every
        // other screen in the mod does and what a player expects from a book.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
        // While the box has focus the arrow keys move the caret. Turning sheets under a typing
        // reader would be the same mistake as a text field that scrolls the page.
        if (search != null && search.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            turnTo(sheet - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            turnTo(sheet + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setFilter(String text) {
        String next = text == null ? "" : text;
        if (next.equals(filter)) return;
        filter = next;
        sheet = 0;
        contentStale = true;
    }

    /** The world keeps running behind the parchment — this is a book, not a menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A vanilla {@link EditBox} that draws itself in ink.
     *
     * <p>All the editing behaviour is inherited untouched — Ctrl+A/C/V/X, word jumps, selection,
     * Home/End, double-click — because every one of those lives in {@code keyPressed} and
     * {@code onClick}. Only {@link #renderWidget} is replaced: vanilla draws its text with a drop
     * shadow, which is right on a dark widget and wrong on parchment, where every other glyph on
     * the page is shadowless.
     *
     * <p>The selection range is recovered from {@link #getHighlighted()} and
     * {@link #getCursorPosition()} rather than read directly, because the field holding it has no
     * getter. When the same run of characters sits on both sides of the caret the guess can pick
     * the wrong side; that costs a highlight drawn one word over and nothing else.
     */
    private final class InkSearchBox extends EditBox {

        private final Component hint;

        InkSearchBox(int x, int y, int width, int height, Component hint) {
            super(OpenScrollScreen.this.font, x, y, width, height, hint);
            this.hint = hint;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!this.isVisible()) return;
            var font = OpenScrollScreen.this.font;
            String value = getValue();
            int x = getX();
            int y = getY() + (getHeight() - 8) / 2;

            if (value.isEmpty() && !isFocused()) {
                g.drawString(font, hint, x, y, inkFaint, false);
                return;
            }

            // Keep the caret in view on a string longer than the rule.
            int cursor = Math.min(getCursorPosition(), value.length());
            int start = 0;
            while (font.width(value.substring(start, cursor)) > getWidth() - 2) start++;
            String shown = font.plainSubstrByWidth(value.substring(start), getWidth() - 2);

            int selStart = selectionStart(value, cursor);
            if (selStart >= 0) {
                // The end comes from the highlighted run's own length: when the selection sits to
                // the right of the caret, the caret *is* its lower bound and would collapse it.
                int a = Math.max(selStart, start);
                int b = Math.min(selStart + getHighlighted().length(), start + shown.length());
                if (b > a) {
                    int sx = x + font.width(value.substring(start, a));
                    int ex = x + font.width(value.substring(start, b));
                    g.fill(sx, y - 1, ex, y + 9, withAlpha(inkHeading, 0x40));
                }
            }

            g.drawString(font, shown, x, y, inkBody, false);

            if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + font.width(value.substring(start, cursor));
                g.fill(cx, y - 1, cx + 1, y + 9, inkHeading);
            }
        }

        /** Lower bound of the selection, or -1 when nothing is selected. */
        private int selectionStart(String value, int cursor) {
            String highlighted = getHighlighted();
            if (highlighted.isEmpty()) return -1;
            int length = highlighted.length();
            int before = cursor - length;
            if (before >= 0 && value.startsWith(highlighted, before)) return before;
            if (cursor + length <= value.length() && value.startsWith(highlighted, cursor)) return cursor;
            return -1;
        }
    }
}
