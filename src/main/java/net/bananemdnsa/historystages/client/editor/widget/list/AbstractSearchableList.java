package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.client.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    protected static final int TAB_HEIGHT = 14;
    protected static final int TAB_PAD = 4;

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

    /** Index into {@link #allTabLabels()} — own tabs first, Selected tab last. */
    private int currentTab = 0;
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    private static final int ADD_BTN_W = 100;
    private static final int ADD_BTN_H = 20;

    private boolean multiSelect = false;
    /**
     * Selected entries, keyed by the value {@link #selectionValueOf} returned at click time.
     * The key is frozen deliberately: a subclass may derive the value from mutable state
     * (SearchableStructureList prefixes tag rows with "#" based on its active tab), so
     * recomputing it at confirm time would emit the wrong value after a tab switch. The
     * entry is kept alongside so the Selected tab can render rows that are no longer in
     * {@link #filteredEntries}. LinkedHashMap, not HashMap — emission follows click order.
     */
    private final LinkedHashMap<String, T> selected = new LinkedHashMap<>();
    /**
     * Frozen copy of {@link #selected} taken when the Selected tab is entered. Deselecting
     * mutates {@link #selected} but leaves this alone, so rows don't shift under the cursor
     * and a misclick can be undone. Cleared on tab exit, rebuilt on next entry.
     */
    private final List<Sel<T>> selectedSnapshot = new ArrayList<>();
    /** {@link #selectedSnapshot} filtered by the current query. */
    private final List<Sel<T>> selectedView = new ArrayList<>();
    private float addHoverProgress = 0.0f;

    private record Sel<E>(String value, E entry) {}

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

    /**
     * Renders a row on the Selected tab. {@code value} is the selection value frozen at
     * click time; {@code entry} is the entry it came from. Defaults to {@link #renderRow}.
     * Override when the row's display text depends on state that has since moved on — e.g.
     * SearchableStructureList decides the "#" prefix from its active tab, which is not the
     * tab being shown here.
     */
    protected void renderSelectedRow(GuiGraphics g, Font font, String value, T entry,
                                     int x, int y, int w, int h, boolean hovered, int rowIndex) {
        renderRow(g, font, entry, x, y, w, h, hovered, rowIndex);
    }

    /** Overridable panel width — default 260, override for wider or narrower lists. */
    protected int getPanelWidth() {
        return DEFAULT_PANEL_WIDTH;
    }

    /**
     * Label for the subclass's single tab. Only rendered when a tab bar is shown at all
     * (i.e. multi-select is on, or the subclass declares several tabs via
     * {@link #ownTabLabels()}).
     */
    protected String primaryTabLabel() {
        return Component.translatable("editor.historystages.search.tab.all").getString();
    }

    /**
     * The subclass's own tabs, left to right. Default is a single tab labelled
     * {@link #primaryTabLabel()}. The Selected tab is appended by the base class and must
     * not be included here.
     */
    protected List<String> ownTabLabels() {
        return List.of(primaryTabLabel());
    }

    /**
     * Called when the user switches to one of the subclass's own tabs. {@code index} is an
     * index into {@link #ownTabLabels()}. Implementations typically swap their data source
     * and call {@link #reloadEntries()}. Not called for the Selected tab.
     */
    protected void onOwnTabChanged(int index) {
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
        selected.clear();
        selectedSnapshot.clear();
        selectedView.clear();
        // The Selected tab is gone with the selection, so currentTab may point past the end.
        // Reset to the first own tab and tell the subclass — otherwise its own tab state (e.g.
        // StructureList's activeTab) keeps pointing at the tab it was on before, and it would
        // load entries the tab bar no longer highlights. A valid own tab is deliberately kept.
        if (currentTab >= ownTabLabels().size()) {
            currentTab = 0;
            onOwnTabChanged(currentTab);
        }
        allEntries.clear();
        allEntries.addAll(loadEntries());
        tabIndicatorInit = false;
        panelW = getPanelWidth();
        panelH = getTabBarHeight() + SearchBar.HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4
                + (multiSelect ? ADD_BTN_H + PADDING : 0);
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

    protected final void setPlaceholder(String placeholder) {
        searchBar.setPlaceholder(placeholder);
    }

    /**
     * Turns on multi-select: rows toggle instead of firing immediately, and an "Add (n)"
     * button confirms. On confirm the select callback fires once per selected entry, in
     * click order — every call site that enables this must tolerate n callback invocations.
     */
    public void setMultiSelect(boolean multi) {
        this.multiSelect = multi;
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
        if (isSelectedTabActive()) applySelectedFilter();
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
        maxScrollRow = Math.max(0, visibleCount() - VISIBLE_ROWS);
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
    // Tabs
    // =============================================

    /**
     * The tab bar is shown from the moment multi-select is on — not only once something is
     * selected. If it appeared with the Selected tab, the panel would grow taller mid-click
     * and shift out from under the cursor.
     */
    private boolean tabBarVisible() {
        return multiSelect || ownTabLabels().size() > 1;
    }

    protected final int getTabBarHeight() {
        return tabBarVisible() ? TAB_HEIGHT + 4 : 0;
    }

    private List<String> allTabLabels() {
        List<String> labels = new ArrayList<>(ownTabLabels());
        if (showSelectedTab()) {
            labels.add(Component.translatable("editor.historystages.search.tab.selected",
                    selected.size()).getString());
        }
        return labels;
    }

    private boolean isSelectedTabActive() {
        return showSelectedTab() && currentTab == ownTabLabels().size();
    }

    /**
     * The Selected tab is present for the whole lifetime of a multi-select panel, empty or
     * not. Showing it only once something is selected would resize the tab bar mid-click.
     */
    private boolean showSelectedTab() {
        return multiSelect;
    }

    private void rebuildSelectedSnapshot() {
        selectedSnapshot.clear();
        for (Map.Entry<String, T> e : selected.entrySet()) {
            selectedSnapshot.add(new Sel<>(e.getKey(), e.getValue()));
        }
        applySelectedFilter();
    }

    private void applySelectedFilter() {
        selectedView.clear();
        String q = searchBar.getText() == null ? "" : searchBar.getText();
        for (Sel<T> ref : selectedSnapshot) {
            if (q.isEmpty() || matchesQuery(ref.entry(), q)) selectedView.add(ref);
        }
    }

    private void toggleSelection(String value, T entry) {
        if (selected.containsKey(value)) {
            selected.remove(value);
        } else {
            selected.put(value, entry);
        }
    }

    /** Row count of whichever list the active tab renders. */
    private int visibleCount() {
        return isSelectedTabActive() ? selectedView.size() : filteredEntries.size();
    }

    private int getTabAt(double mouseX, double mouseY) {
        if (!tabBarVisible()) return -1;
        Font font = Minecraft.getInstance().font;
        int tabY = panelY + PADDING;
        List<String> labels = allTabLabels();
        int x = panelX + PADDING;
        for (int i = 0; i < labels.size(); i++) {
            int w = font.width(labels.get(i)) + TAB_PAD * 2;
            if (mouseX >= x && mouseX < x + w && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                return i;
            }
            x += w + 2;
        }
        return -1;
    }

    private void renderTabs(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!tabBarVisible()) return;
        int tabY = panelY + PADDING;
        List<String> labels = allTabLabels();
        int n = labels.size();
        int[] tabXs = new int[n];
        int[] tabWs = new int[n];

        int x = panelX + PADDING;
        for (int i = 0; i < n; i++) {
            tabWs[i] = font.width(labels.get(i)) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        int activeIdx = Math.min(currentTab, n - 1);
        if (!tabIndicatorInit) {
            tabIndicatorX = tabXs[activeIdx];
            tabIndicatorW = tabWs[activeIdx];
            tabIndicatorInit = true;
        }

        float targetX = tabXs[activeIdx];
        float targetW = tabWs[activeIdx];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f) tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f) tabIndicatorW = targetW;

        for (int i = 0; i < n; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            g.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            g.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        g.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2,
                (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);
        g.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING, tabY + TAB_HEIGHT + 1,
                0xFF555555);
    }

    private void switchTab(int newTab) {
        boolean leavingSelectedTab = isSelectedTabActive();
        currentTab = newTab;
        searchBar.filters().close();
        searchBar.setFocused(true);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        scrollRow = 0;
        if (leavingSelectedTab && !isSelectedTabActive()) {
            selectedSnapshot.clear();
            selectedView.clear();
        }
        if (isSelectedTabActive()) {
            searchBar.setPlaceholder(Component.translatable(
                    "editor.historystages.search.selected.placeholder", selected.size()).getString());
            rebuildSelectedSnapshot();
        } else {
            onOwnTabChanged(newTab);
        }
        searchBar.setText(""); // always fires applyFilter
        updateMaxScroll();
    }

    private int searchTopY() {
        return panelY + PADDING + getTabBarHeight();
    }

    /** Y of the first list row. */
    protected final int listTopY() {
        return searchTopY() + SearchBar.HEIGHT + PADDING;
    }

    private int listLeftX() {
        return panelX + PADDING;
    }

    private int listInnerW() {
        return panelW - PADDING * 2 - 8;
    }

    // =============================================
    // Rendering
    // =============================================

    protected enum RowState { NORMAL, SELECTED, DESELECTED }

    /**
     * Paints the row background. Selected rows get an accent border and a warm fill,
     * deselected ones (Selected tab only) a red border and dark red fill — the same colour
     * language the item and entity pickers use. The subclass's {@link #renderRow} draws only
     * the contents on top, so it needs no knowledge of selection state.
     */
    private void drawRowBackground(GuiGraphics g, int x, int y, int w, boolean hovered, RowState state) {
        if (state == RowState.NORMAL) {
            g.fill(x, y, x + w, y + ROW_HEIGHT, hovered ? 0xFF353535 : 0xFF252525);
            return;
        }
        int border;
        int bg;
        if (state == RowState.SELECTED) {
            border = hovered ? 0xFFFF8800 : 0xFFFFCC00;
            bg = hovered ? 0xFF553A10 : 0xFF2A2510;
        } else {
            border = hovered ? 0xFF884444 : 0xFF552020;
            bg = hovered ? 0xFF3A1A1A : 0xFF1A0D0D;
        }
        g.fill(x, y, x + w, y + ROW_HEIGHT, border);
        g.fill(x + 1, y + 1, x + w - 1, y + ROW_HEIGHT - 1, bg);
    }

    private int addButtonX() {
        return panelX + (panelW - ADD_BTN_W) / 2;
    }

    private int addButtonY() {
        return panelY + panelH - PADDING - ADD_BTN_H;
    }

    private boolean isAddButtonAt(double mouseX, double mouseY) {
        return mouseX >= addButtonX() && mouseX < addButtonX() + ADD_BTN_W
                && mouseY >= addButtonY() && mouseY < addButtonY() + ADD_BTN_H;
    }

    private boolean canConfirm() {
        return multiSelect && !selected.isEmpty();
    }

    private void renderAddButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = addButtonX();
        int y = addButtonY();
        boolean hovered = canConfirm() && isAddButtonAt(mouseX, mouseY);
        addHoverProgress = hovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canConfirm()) {
            String label = Component.translatable("editor.historystages.search.add",
                    selected.size()).getString();
            SearchPanelChrome.renderStyledButton(g, font, x, y, ADD_BTN_W, ADD_BTN_H, label,
                    addHoverProgress);
        } else {
            g.fill(x, y, x + ADD_BTN_W, y + ADD_BTN_H, 0x20FFFFFF);
            g.fill(x, y, x + ADD_BTN_W, y + 1, 0x10FFFFFF);
            String label = Component.translatable("editor.historystages.search.add.empty").getString();
            g.drawString(font, label, x + (ADD_BTN_W - font.width(label)) / 2,
                    y + (ADD_BTN_H - 8) / 2, 0xFF666666, false);
        }
    }

    private boolean confirmAndAdd() {
        if (!canConfirm()) return false;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        for (String value : new ArrayList<>(selected.keySet())) {
            emitSelection(value);
        }
        hide();
        return true;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        anyRowHoveredThisFrame = false;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(g, font, mouseX, mouseY);

        int searchX = panelX + PADDING;
        int searchY = searchTopY();
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

            if (index >= visibleCount()) {
                // Empty slots don't react to hover — there's nothing there to point at.
                drawRowBackground(g, listX, rowY, listW, false, RowState.NORMAL);
                continue;
            }

            if (isSelectedTabActive()) {
                Sel<T> ref = selectedView.get(index);
                boolean stillSelected = selected.containsKey(ref.value());
                drawRowBackground(g, listX, rowY, listW, rowHovered,
                        stillSelected ? RowState.SELECTED : RowState.DESELECTED);
                renderSelectedRow(g, font, ref.value(), ref.entry(),
                        listX, rowY, listW, ROW_HEIGHT, rowHovered, index);
            } else {
                T entry = filteredEntries.get(index);
                boolean isSelected = multiSelect && selected.containsKey(selectionValueOf(entry));
                drawRowBackground(g, listX, rowY, listW, rowHovered,
                        isSelected ? RowState.SELECTED : RowState.NORMAL);
                renderRow(g, font, entry, listX, rowY, listW, ROW_HEIGHT, rowHovered, index);
            }
        }
        if (!anyRowHoveredThisFrame) hoveredRow = -1;

        // Selected tab with nothing selected: hint in the middle rather than a blank grid.
        if (isSelectedTabActive() && selectedSnapshot.isEmpty()) {
            String hint = Component.translatable("editor.historystages.search.selected.empty").getString();
            int listH = VISIBLE_ROWS * ROW_HEIGHT;
            g.drawString(font, hint, listX + (listW - font.width(hint)) / 2,
                    listY + (listH - 8) / 2, 0xFF888888, false);
        }

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

        if (multiSelect) renderAddButton(g, font, mouseX, mouseY);

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

        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab >= 0) {
            if (clickedTab != currentTab) switchTab(clickedTab);
            return true;
        }

        if (multiSelect && isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
            return true;
        }

        int listX = listLeftX();
        int listY = listTopY();
        int listW = listInnerW();

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
            if (index < visibleCount() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (isSelectedTabActive()) {
                    // Toggle against the live selection; the snapshot stays put on purpose.
                    Sel<T> ref = selectedView.get(index);
                    toggleSelection(ref.value(), ref.entry());
                    searchBar.setPlaceholder(Component.translatable(
                            "editor.historystages.search.selected.placeholder",
                            selected.size()).getString());
                    return true;
                }
                T entry = filteredEntries.get(index);
                if (multiSelect) {
                    toggleSelection(selectionValueOf(entry), entry);
                    return true;
                }
                emitSelection(selectionValueOf(entry));
                hide();
                return true;
            }
        }
        searchBar.setFocused(true);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int listY = listTopY();
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
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
        if (keyCode == 257 && canConfirm()) { // Enter
            confirmAndAdd();
            return true;
        }
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
