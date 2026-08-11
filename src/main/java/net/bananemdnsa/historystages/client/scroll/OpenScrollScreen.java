package net.bananemdnsa.historystages.client.scroll;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.ClientToastHandler;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.SearchBar;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapter;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterMode;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapters;
import net.bananemdnsa.historystages.data.scroll.OpenScrollContent;
import net.bananemdnsa.historystages.data.scroll.OpenScrollDocument;
import net.bananemdnsa.historystages.data.scroll.OpenScrollEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollMarker;
import net.bananemdnsa.historystages.data.scroll.OpenScrollVisibility;
import net.bananemdnsa.historystages.data.scroll.OpenScrollWorldGroup;
import net.bananemdnsa.historystages.screen.OpenScrollGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The document a right-clicked Open Scroll shows: what its stage unlocked, in chapters.
 *
 * <p>Pure client state. Stage definitions, graph descriptions and the reader's own unlock state
 * are all on the client already, so this screen needs no menu, no block entity and no packet.
 */
public class OpenScrollScreen extends Screen {

    private static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/scroll/open_gui.png");

    /** Vanilla's Standard Galactic font — the one the enchanting table uses. No asset of ours. */
    private static final ResourceLocation GLYPH_FONT = ResourceLocation.withDefaultNamespace("alt");

    /** Dark ink on parchment: headings and the stage name. */
    private static final int INK_PRIMARY = 0x3F2D13;
    /** The same ink, lightened for body text so a heading still leads the eye. */
    private static final int INK_BODY = 0x4A3416;
    /** Faded ink for the counts line, which is a footnote rather than content. */
    private static final int INK_FAINT = 0x7A5A2C;

    /** Wash over the active tab — warm, so it reads as lit parchment rather than a selection box. */
    private static final int TAB_ACTIVE = 0x66FFE9B0;
    /** Wash over an inactive tab: pushes it back without hiding its icon. */
    private static final int TAB_INACTIVE = 0x33000000;

    /** Laid over a locked item so its shape survives but its identity does not. */
    private static final int SILHOUETTE = 0xB0241A0F;
    /** Replaces the icon entirely when the stage hides even the name — a hole in the parchment. */
    private static final int HOLE = 0x8C3C2D1C;

    /** Line advance for wrapped body text. Font lines are 9px; the extra pixel is leading. */
    private static final int LINE_ADVANCE = 10;

    /** How large an entity is drawn inside an 18px cell. Eyeballed; tune against the real screen. */
    private static final int ENTITY_SCALE = 7;

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

    private final SearchBar search = new SearchBar("");
    private final Scrollbar scrollbar = new Scrollbar();

    private int leftPos;
    private int topPos;
    private int activeTab;

    private String filter = "";
    private float scroll;

    /**
     * The visible entries of the active chapter, rebuilt only when the tab or the filter changes.
     * Rebuilding per frame would allocate an {@link ItemStack} per entry sixty times a second.
     */
    private List<IconCell> cells = List.of();
    private List<TextRow> rows = List.of();
    private boolean contentStale = true;

    /** One entry of an icon chapter. {@code entityId} is null for items, {@code stack} for creatures. */
    private record IconCell(ItemStack stack, String entityId, boolean hidden, List<Component> tooltip) {}

    /** One line of a text chapter: either a group heading or an entry. */
    private record TextRow(Component text, boolean heading, Component tooltip) {}

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

    @Override
    protected void init() {
        this.leftPos = (this.width - OpenScrollGeometry.WIDTH) / 2;
        this.topPos = (this.height - OpenScrollGeometry.HEIGHT) / 2;
        if (activeTab >= chapters.size()) activeTab = 0;

        search.setLightStyle(true);
        search.setPlaceholder(Component.translatable("gui.historystages.open_scroll.search").getString());
        search.setPosition(leftPos + OpenScrollGeometry.PARCHMENT_X,
                topPos + OpenScrollGeometry.SEARCH_Y, OpenScrollGeometry.PARCHMENT_WIDTH);
        search.onChange(text -> {
            filter = text == null ? "" : text.toLowerCase(Locale.ROOT);
            scroll = 0.0f;
            contentStale = true;
        });
        contentStale = true;
    }

    // --- content ---

    private void rebuildIfStale() {
        if (!contentStale) return;
        contentStale = false;
        cells = List.of();
        rows = List.of();

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
    }

    private List<IconCell> itemCells() {
        List<IconCell> out = new ArrayList<>();
        for (String id : document.itemIds()) {
            ItemStack stack = ClientToastHandler.resolveIcon(id);
            if (stack.isEmpty()) continue;
            boolean hidden = isHidden(stack);
            if (!matchesFilter(stack.getHoverName().getString(), hidden)) continue;
            out.add(new IconCell(stack, null, hidden, List.of(name(stack.getHoverName().getString(), hidden))));
        }
        return out;
    }

    private List<TextRow> itemRows() {
        List<TextRow> out = new ArrayList<>();
        for (String id : document.itemIds()) {
            ItemStack stack = ClientToastHandler.resolveIcon(id);
            if (stack.isEmpty()) continue;
            boolean hidden = isHidden(stack);
            String shown = stack.getHoverName().getString();
            if (!matchesFilter(shown, hidden)) continue;
            out.add(new TextRow(name(shown, hidden), false, Component.literal(id)));
        }
        return out;
    }

    private List<IconCell> creatureCells() {
        List<IconCell> out = new ArrayList<>();
        for (OpenScrollEntry entry : document.creatures()) {
            String shown = shortName(entry.id());
            if (!matchesFilter(shown, stageHidden)) continue;
            out.add(new IconCell(ItemStack.EMPTY, entry.id(), stageHidden, creatureTooltip(entry, shown)));
        }
        return out;
    }

    private List<TextRow> creatureRows() {
        List<TextRow> out = new ArrayList<>();
        for (OpenScrollEntry entry : document.creatures()) {
            String shown = shortName(entry.id());
            if (!matchesFilter(shown, stageHidden)) continue;
            out.add(new TextRow(name(shown, stageHidden), false, Component.literal(entry.id())));
        }
        return out;
    }

    /** The entity's name, then one line per lock kind it came from. */
    private List<Component> creatureTooltip(OpenScrollEntry entry, String shown) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(shown, stageHidden));
        for (OpenScrollMarker marker : OpenScrollMarker.values()) {
            if (!entry.markers().contains(marker)) continue;
            lines.add(Component.translatable("gui.historystages.open_scroll.marker."
                    + marker.name().toLowerCase(Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private List<TextRow> worldRows() {
        List<TextRow> out = new ArrayList<>();
        for (OpenScrollWorldGroup group : document.world()) {
            List<TextRow> entries = new ArrayList<>();
            for (String id : group.ids()) {
                String shown = shortName(id);
                if (!matchesFilter(shown, stageHidden)) continue;
                entries.add(new TextRow(name(shown, stageHidden), false, Component.literal(id)));
            }
            // A heading whose entries were all filtered away would stand over nothing.
            if (entries.isEmpty()) continue;
            out.add(new TextRow(Component.translatable(group.labelKey()), true, null));
            out.addAll(entries);
        }
        return out;
    }

    /**
     * Hidden entries never match a search: finding one by typing its real name would hand over
     * exactly the word the glyphs are there to withhold.
     */
    private boolean matchesFilter(String displayName, boolean hidden) {
        if (filter.isEmpty()) return true;
        if (hidden) return false;
        return displayName.toLowerCase(Locale.ROOT).contains(filter);
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

    /** Drop the namespace so a modded id still fits; the full id lives in the hover tooltip. */
    private static String shortName(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuildIfStale();
        renderBackground(g, mouseX, mouseY, partialTick);
        // One blit of the whole sheet, offset so the drawn region lands on the panel. Blitting the
        // region alone would centre the screen on the sheet's empty margins instead.
        g.blit(SHEET, leftPos - OpenScrollGeometry.SHEET_X, topPos - OpenScrollGeometry.SHEET_Y,
                0, 0, OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE,
                OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE);
        renderTabs(g);
        renderHead(g);

        if (activeChapter() == OpenScrollChapter.OVERVIEW) {
            renderOverview(g);
        } else {
            search.render(g, this.font, mouseX, mouseY);
            if (drawsIcons()) renderGrid(g, mouseX, mouseY);
            else renderRows(g, mouseX, mouseY);
            renderScrollbar(g, mouseX, mouseY);
            renderEmptyNotice(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g) {
        for (int i = 0; i < chapters.size(); i++) {
            int x = leftPos + OpenScrollGeometry.tabX(i);
            int y = topPos + OpenScrollGeometry.TAB_Y;
            g.fill(x, y, x + OpenScrollGeometry.TAB_SIZE, y + OpenScrollGeometry.TAB_SIZE,
                    i == activeTab ? TAB_ACTIVE : TAB_INACTIVE);
            g.renderItem(tabIcon(chapters.get(i).chapter()), x + 1, y + 1);
        }
    }

    /**
     * The overview tab wears the stage's own icon; the others use a fixed stand-in item.
     *
     * <p>Deviation from the spec, which said "own sheet icon": vanilla items need no new artwork
     * and read clearly at 16x16. Swap in painted icons later if the artist supplies them.
     */
    private ItemStack tabIcon(OpenScrollChapter chapter) {
        return switch (chapter) {
            case OVERVIEW -> ClientToastHandler.resolveIcon(document.iconId());
            case ITEMS -> new ItemStack(Items.CHEST);
            case CREATURES -> new ItemStack(Items.ZOMBIE_HEAD);
            case WORLD -> new ItemStack(Items.FILLED_MAP);
        };
    }

    private void renderHead(GuiGraphics g) {
        OpenScrollChapter chapter = activeChapter();
        Component head = Component.translatable(
                "gui.historystages.open_scroll.chapter." + chapter.serialize());
        if (chapter != OpenScrollChapter.OVERVIEW) {
            head = head.copy().append(Component.literal(" · " + count(chapter)));
        }
        g.drawString(this.font, head, leftPos + OpenScrollGeometry.PARCHMENT_X,
                topPos + OpenScrollGeometry.HEAD_Y, INK_PRIMARY, false);
    }

    private int count(OpenScrollChapter chapter) {
        return switch (chapter) {
            case ITEMS -> document.itemCount();
            case CREATURES -> document.creatureCount();
            case WORLD -> document.worldCount();
            case OVERVIEW -> 0;
        };
    }

    // --- the overview page ---

    private void renderOverview(GuiGraphics g) {
        int x = leftPos + OpenScrollGeometry.PARCHMENT_X;
        int width = OpenScrollGeometry.PARCHMENT_WIDTH;

        if (unknownStage) {
            drawCentredWrapped(g, Component.translatable("gui.historystages.open_scroll.unknown_stage"),
                    x, topPos + OpenScrollGeometry.OVERVIEW_DESC_Y, width);
            return;
        }

        // renderItem always draws 16x16, and the page wants 32x32, so the pose does the doubling.
        ItemStack icon = ClientToastHandler.resolveIcon(document.iconId());
        g.pose().pushPose();
        g.pose().translate((float) (leftPos + OpenScrollGeometry.OVERVIEW_ICON_X),
                (float) (topPos + OpenScrollGeometry.OVERVIEW_ICON_Y), 0.0f);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();

        Component title = Component.literal(document.displayName());
        g.drawString(this.font, title, x + (width - this.font.width(title)) / 2,
                topPos + OpenScrollGeometry.OVERVIEW_TITLE_Y, INK_PRIMARY, false);

        if (!document.description().isEmpty()) {
            int y = topPos + OpenScrollGeometry.OVERVIEW_DESC_Y;
            // Stops at the counts line rather than scrolling: a description long enough to reach it
            // belongs in the graph screen, and a page that silently overflows the parchment is worse.
            int limit = topPos + OpenScrollGeometry.OVERVIEW_COUNTS_Y;
            for (FormattedCharSequence line : this.font.split(Component.literal(document.description()), width)) {
                if (y + this.font.lineHeight > limit) break;
                g.drawString(this.font, line, x, y, INK_BODY, false);
                y += LINE_ADVANCE;
            }
        }

        Component counts = Component.translatable("gui.historystages.open_scroll.counts",
                document.itemCount(), document.creatureCount(), document.worldCount());
        g.drawString(this.font, counts, x + (width - this.font.width(counts)) / 2,
                topPos + OpenScrollGeometry.OVERVIEW_COUNTS_Y, INK_FAINT, false);
    }

    private void drawCentredWrapped(GuiGraphics g, Component text, int x, int y, int width) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            g.drawString(this.font, line, x + (width - this.font.width(line)) / 2, y, INK_BODY, false);
            y += LINE_ADVANCE;
        }
    }

    // --- the icon grid ---

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int top = topPos + OpenScrollGeometry.CONTENT_Y;
        int bottom = topPos + OpenScrollGeometry.parchmentBottom();
        int offset = Math.round(scroll);
        IconCell hovered = null;
        int hoverX = 0;
        int hoverY = 0;

        g.enableScissor(leftPos + OpenScrollGeometry.PARCHMENT_X, top,
                leftPos + OpenScrollGeometry.PARCHMENT_X + OpenScrollGeometry.PARCHMENT_WIDTH, bottom);
        for (int i = 0; i < cells.size(); i++) {
            int x = leftPos + OpenScrollGeometry.cellX(i % OpenScrollGeometry.COLUMNS);
            int y = top + (i / OpenScrollGeometry.COLUMNS) * OpenScrollGeometry.CELL - offset;
            if (y + OpenScrollGeometry.CELL <= top || y >= bottom) continue;
            renderCell(g, cells.get(i), x, y);
            if (mouseX >= x && mouseX < x + OpenScrollGeometry.CELL
                    && mouseY >= Math.max(y, top) && mouseY < Math.min(y + OpenScrollGeometry.CELL, bottom)) {
                hovered = cells.get(i);
                hoverX = mouseX;
                hoverY = mouseY;
            }
        }
        g.disableScissor();

        // Outside the scissor, or the tooltip would be clipped to the parchment.
        if (hovered != null) {
            g.renderComponentTooltip(this.font, hovered.tooltip(), hoverX, hoverY);
        }
    }

    private void renderCell(GuiGraphics g, IconCell cell, int x, int y) {
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
            g.fill(x, y, x + OpenScrollGeometry.CELL, y + OpenScrollGeometry.CELL, SILHOUETTE);
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
        int x = leftPos + OpenScrollGeometry.PARCHMENT_X;
        int top = topPos + OpenScrollGeometry.CONTENT_Y;
        int bottom = topPos + OpenScrollGeometry.parchmentBottom();
        int y = top - Math.round(scroll);
        TextRow hovered = null;

        g.enableScissor(x, top, x + OpenScrollGeometry.PARCHMENT_WIDTH, bottom);
        for (TextRow row : rows) {
            int height = rowHeight(row);
            if (y + height > top && y < bottom) {
                g.drawString(this.font, row.text(), x, y, row.heading() ? INK_FAINT : INK_BODY, false);
                if (!row.heading() && row.tooltip() != null
                        && mouseX >= x && mouseX < x + OpenScrollGeometry.PARCHMENT_WIDTH
                        && mouseY >= Math.max(y, top) && mouseY < Math.min(y + height, bottom)) {
                    hovered = row;
                }
            }
            y += height;
        }
        g.disableScissor();

        if (hovered != null) {
            g.renderTooltip(this.font, hovered.tooltip(), mouseX, mouseY);
        }
    }

    private static int rowHeight(TextRow row) {
        return row.heading() ? OpenScrollGeometry.TEXT_GROUP_HEIGHT : OpenScrollGeometry.TEXT_ROW_HEIGHT;
    }

    private void renderEmptyNotice(GuiGraphics g) {
        if (filter.isEmpty() || !cells.isEmpty() || !rows.isEmpty()) return;
        g.drawString(this.font, Component.translatable("gui.historystages.open_scroll.no_results"),
                leftPos + OpenScrollGeometry.PARCHMENT_X, topPos + OpenScrollGeometry.CONTENT_Y,
                INK_FAINT, false);
    }

    // --- scrolling ---

    /** Total height of the active chapter's content, whether it is a grid or a list. */
    private int contentPixels() {
        if (drawsIcons()) {
            int gridRows = (cells.size() + OpenScrollGeometry.COLUMNS - 1) / OpenScrollGeometry.COLUMNS;
            return gridRows * OpenScrollGeometry.CELL;
        }
        int total = 0;
        for (TextRow row : rows) total += rowHeight(row);
        return total;
    }

    private float maxScroll() {
        return Math.max(0, contentPixels() - OpenScrollGeometry.contentHeight());
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        scrollbar.render(g, leftPos + OpenScrollGeometry.SCROLLBAR_X,
                topPos + OpenScrollGeometry.CONTENT_Y, topPos + OpenScrollGeometry.parchmentBottom(),
                scroll, maxScroll(), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (activeChapter() == OpenScrollChapter.OVERVIEW) return false;
        scroll = clampScroll(scroll - (float) deltaY * OpenScrollGeometry.TEXT_ROW_HEIGHT);
        return true;
    }

    private float clampScroll(float value) {
        return Math.max(0.0f, Math.min(maxScroll(), value));
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < chapters.size(); i++) {
            int x = leftPos + OpenScrollGeometry.tabX(i);
            int y = topPos + OpenScrollGeometry.TAB_Y;
            if (mouseX >= x && mouseX < x + OpenScrollGeometry.TAB_SIZE
                    && mouseY >= y && mouseY < y + OpenScrollGeometry.TAB_SIZE) {
                if (i != activeTab) {
                    activeTab = i;
                    scroll = 0.0f;
                    contentStale = true;
                }
                return true;
            }
        }
        if (activeChapter() != OpenScrollChapter.OVERVIEW) {
            if (scrollbar.mouseClicked(mouseX, mouseY)) {
                scroll = clampScroll(scrollbar.scrollFor(mouseY));
                return true;
            }
            if (search.mouseClicked(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbar.isDragging()) {
            scroll = clampScroll(scrollbar.scrollFor(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape closes the document even while the search box has focus, which is what every
        // other screen in the mod does and what a player expects from a book.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
        if (activeChapter() != OpenScrollChapter.OVERVIEW && search.isFocused()
                && search.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeChapter() != OpenScrollChapter.OVERVIEW && search.isFocused()
                && search.charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** The world keeps running behind the parchment — this is a book, not a menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
