package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Common scaffold for the family of Searchable*List overlay widgets — search bar,
 * filter dropdown, panel frame, scrollbar math, mouse/keyboard handling, hide/show.
 *
 * <p>Subclasses provide the data source, per-row rendering, and the matching predicate.
 * The select callback is exposed as {@code Consumer<String>} for API parity with the
 * pre-refactor widgets; subclasses use {@link #emitSelection(String)} to fire it.
 */
public abstract class AbstractSearchableList<T> {

    protected static final int ROW_HEIGHT = 16;
    protected static final int VISIBLE_ROWS = 10;
    protected static final int PADDING = 6;
    protected static final int DEFAULT_PANEL_WIDTH = 260;

    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    protected final List<T> allEntries = new ArrayList<>();
    protected final List<T> filteredEntries = new ArrayList<>();
    protected final SearchBar searchBar;

    private final Consumer<String> onSelect;
    private final Supplier<Collection<String>> alreadyAddedSupplier;

    /** Optional "is locked by stage" predicate enabled via {@link #addLockedFilter}. */
    private Predicate<String> lockedFilterFn = null;

    protected int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    protected int scrollRow = 0;
    protected int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

    // Marquee state for drawRowMarqueeText
    private int hoveredRow = -1;
    private long hoverStartTime = 0;
    private boolean anyRowHoveredThisFrame = false;

    protected AbstractSearchableList(String placeholder,
                                     Consumer<String> onSelect,
                                     Supplier<Collection<String>> alreadyAddedSupplier) {
        this.onSelect = onSelect;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.searchBar = new SearchBar(placeholder).onChange(this::applyFilter);
        if (alreadyAddedSupplier != null) {
            searchBar.filters().addOption("hide_added", "Hide already added", null);
        }
        configureFilters(searchBar);
    }

    // =============================================
    // Subclass extension points
    // =============================================

    /** Populates {@link #allEntries}. Called on construction and on each {@link #show}. */
    protected abstract List<T> loadEntries();

    /**
     * Returns the string identifier used for the "only vanilla" / "only modded" namespace
     * check, and as the default for the "hide already added" check.
     */
    protected abstract String getIdForFilter(T entry);

    /**
     * Returns the identifier used for the "hide already added" lookup against the supplier.
     * Defaults to {@link #getIdForFilter}. Override when the supplier stores a transformed
     * form of the id (e.g. tags prefixed with "#").
     */
    protected String getIdForAddedCheck(T entry) {
        return getIdForFilter(entry);
    }

    /** True if the entry matches the (already lower-cased) search query. */
    protected abstract boolean matchesQuery(T entry, String lowerCaseQuery);

    /** Renders the row contents within the given bounds. The background is already drawn. */
    protected abstract void renderRow(GuiGraphics g, Font font, T entry,
                                      int x, int y, int w, int h, boolean hovered, int rowIndex);

    /** Overridable panel width — default 260, override for wider or narrower lists. */
    protected int getPanelWidth() {
        return DEFAULT_PANEL_WIDTH;
    }

    /**
     * Additional vertical space reserved above the search bar — used by widgets that
     * render their own tab bar or header. Default 0 (no inset).
     */
    protected int getTopInsetHeight() {
        return 0;
    }

    /** Renders the top inset area. Default no-op. Called before the search bar. */
    protected void renderTopInset(GuiGraphics g, Font font, int mouseX, int mouseY) {
    }

    /**
     * Hook for handling clicks inside the top inset area (e.g. tab clicks).
     * Default returns false (no handling). When true, the click is consumed.
     */
    protected boolean onTopInsetClick(double mouseX, double mouseY) {
        return false;
    }

    /** Returns the value passed to {@code onSelect.accept(...)} when a row is clicked. */
    protected abstract String selectionValueOf(T entry);

    /** Default registers the "only vanilla / only modded" pair. Subclasses can override to add more. */
    protected void configureFilters(SearchBar bar) {
        bar.filters().addOption("only_vanilla", "Only vanilla", "source");
        bar.filters().addOption("only_modded", "Only modded", "source");
    }

    /** Optional per-entry exclusion (e.g. self-dependency). Default: nothing excluded. */
    protected boolean isExcluded(T entry) {
        return false;
    }

    /** Hook called after the list is rendered, while the panel is still on screen. */
    protected void afterRender(GuiGraphics g, Font font, int mouseX, int mouseY) {
    }

    /**
     * Renders a single-line text inside the row with simple "..."-truncation when too wide.
     * No marquee scroll on hover.
     */
    protected final void drawRowText(GuiGraphics g, Font font, String text,
                                     int x, int y, int w, boolean hovered) {
        if (font.width(text) > w - 4) {
            text = font.plainSubstrByWidth(text, w - 10) + "...";
        }
        g.drawString(font, text, x + 3, y + 4, hovered ? 0xFFFFFF : 0xBBBBBB, false);
    }

    /**
     * Like {@link #drawRowText} but, when the row is hovered for longer than 800ms and the
     * text is too wide to fit, scrolls horizontally (marquee) inside a scissor rect.
     */
    protected final void drawRowMarqueeText(GuiGraphics g, Font font, String text,
                                            int x, int y, int w, int h,
                                            boolean hovered, int rowIndex) {
        int textW = font.width(text);
        int textAvailW = w - 6;
        int textColor = hovered ? 0xFFFFFF : 0xBBBBBB;
        if (hovered) {
            anyRowHoveredThisFrame = true;
            if (hoveredRow != rowIndex) {
                hoveredRow = rowIndex;
                hoverStartTime = System.currentTimeMillis();
            }
        }
        if (textW > textAvailW && hovered && hoveredRow == rowIndex) {
            long elapsed = System.currentTimeMillis() - hoverStartTime;
            if (elapsed > MARQUEE_DELAY_MS) {
                float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                int maxMarquee = textW - textAvailW + 10;
                float cycle = (float) maxMarquee * 2;
                float pos = scrollProg % cycle;
                int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                g.enableScissor(x, y, x + w, y + h);
                g.drawString(font, text, x + 3 - scrollOff, y + 4, textColor, false);
                g.disableScissor();
            } else {
                g.drawString(font, font.plainSubstrByWidth(text, textAvailW - 6) + "...",
                        x + 3, y + 4, textColor, false);
            }
        } else if (textW > textAvailW) {
            g.drawString(font, font.plainSubstrByWidth(text, textAvailW - 6) + "...",
                    x + 3, y + 4, textColor, false);
        } else {
            g.drawString(font, text, x + 3, y + 4, textColor, false);
        }
    }

    // =============================================
    // Public API (matches the pre-refactor widgets)
    // =============================================

    public void show(int centerX, int centerY, int parentWidth) {
        allEntries.clear();
        allEntries.addAll(loadEntries());
        panelW = getPanelWidth();
        panelH = getTopInsetHeight() + SearchBar.HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        searchBar.setText(""); // triggers applyFilter against fresh allEntries
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setFilter(String filter) {
        searchBar.setText(filter);
    }

    private void applyFilter(String filter) {
        this.scrollRow = 0;
        filteredEntries.clear();
        String q = filter == null ? "" : filter;
        for (T entry : allEntries) {
            if (isExcluded(entry)) continue;
            if (!matchesDropdownFilters(entry)) continue;
            if (q.isEmpty() || matchesQuery(entry, q)) {
                filteredEntries.add(entry);
            }
        }
        updateMaxScroll();
    }

    /**
     * Registers a "Hide locked" filter that excludes entries whose
     * {@link #getIdForFilter id} matches {@code isLocked.test(id)}. The option appears
     * in the filter dropdown with the supplied label and starts active by default —
     * use this from the auto-trigger editor to hide things the current stage already
     * locks (player can't trigger them while the stage isn't unlocked).
     */
    public AbstractSearchableList<T> addLockedFilter(String label, Predicate<String> isLocked) {
        this.lockedFilterFn = isLocked;
        searchBar.filters().addOption("hide_locked", label, null, true);
        applyFilter(searchBar.getText() == null ? "" : searchBar.getText());
        return this;
    }

    private boolean matchesDropdownFilters(T entry) {
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            String checkId = getIdForAddedCheck(entry);
            if (checkId != null) {
                Collection<String> added = alreadyAddedSupplier.get();
                if (added != null && added.contains(checkId)) return false;
            }
        }
        if (lockedFilterFn != null && searchBar.filters().isActive("hide_locked")) {
            String checkId = getIdForFilter(entry);
            if (checkId != null && lockedFilterFn.test(checkId)) return false;
        }
        String nsId = getIdForFilter(entry);
        if (nsId == null) return true;
        String namespace = nsId.contains(":") ? nsId.substring(0, nsId.indexOf(':')) : "";
        boolean isVanilla = "minecraft".equals(namespace);
        if (searchBar.filters().isActive("only_vanilla") && !isVanilla) return false;
        if (searchBar.filters().isActive("only_modded") && isVanilla) return false;
        return true;
    }

    private void updateMaxScroll() {
        maxScrollRow = Math.max(0, filteredEntries.size() - VISIBLE_ROWS);
    }

    protected final void emitSelection(String value) {
        onSelect.accept(value);
    }

    /**
     * Reloads {@link #allEntries} from {@link #loadEntries()} and re-applies the current filter.
     * Use when a subclass switches between data sources (e.g. tab toggle) without re-opening.
     */
    protected final void reloadEntries() {
        allEntries.clear();
        allEntries.addAll(loadEntries());
        scrollRow = 0;
        // re-fire the current filter via setText (always fires onChange)
        searchBar.setText(searchBar.getText() == null ? "" : searchBar.getText());
    }

    // =============================================
    // Rendering
    // =============================================

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        anyRowHoveredThisFrame = false;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTopInset(g, font, mouseX, mouseY);

        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING + getTopInsetHeight();
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(g, font, mouseX, mouseY);

        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = !filterUiHovered && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            g.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filteredEntries.size()) {
                renderRow(g, font, filteredEntries.get(index),
                        listX, rowY, listW, ROW_HEIGHT, rowHovered, index);
            }
        }
        if (!anyRowHoveredThisFrame) hoveredRow = -1;

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + VISIBLE_ROWS * ROW_HEIGHT;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            g.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbHeight = Math.max(10,
                    (int) ((float) VISIBLE_ROWS / (maxScrollRow + VISIBLE_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        afterRender(g, font, mouseX, mouseY);
    }

    // =============================================
    // Input
    // =============================================

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        if (searchBar.mouseClicked(mouseX, mouseY)) return true;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        if (onTopInsetClick(mouseX, mouseY)) return true;

        int searchY = panelY + PADDING + getTopInsetHeight();
        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredEntries.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                emitSelection(selectionValueOf(filteredEntries.get(index)));
                hide();
                return true;
            }
        }
        searchBar.setFocused(true);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int searchY = panelY + PADDING + getTopInsetHeight();
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        updateScrollFromMouse(mouseY, listY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int listY) {
        int listH = VISIBLE_ROWS * ROW_HEIGHT;
        int totalRows = maxScrollRow + VISIBLE_ROWS;
        int thumbHeight = Math.max(10, (int) ((float) VISIBLE_ROWS / totalRows * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listY - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) scrollY));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (searchBar.keyPressed(keyCode)) return true;
        if (keyCode == 256) {
            hide();
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible) return false;
        return searchBar.charTyped(c);
    }
}
