package net.bananemdnsa.historystages.client.editor.widget.list;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.MarqueeText;
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
import java.util.HashMap;
import java.util.Iterator;
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
public abstract class AbstractSearchableList<T> implements PickerOverlay {

    protected static final int ROW_HEIGHT = 16;
    protected static final int VISIBLE_ROWS = 10;
    protected static final int PADDING = 6;
    protected static final int DEFAULT_PANEL_WIDTH = 260;
    protected static final int TAB_HEIGHT = 14;
    protected static final int TAB_PAD = 4;

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
    /** Distance from the thumb's top to the cursor when the drag started. */
    private int scrollGrabOffset = 0;

    /**
     * Sub-row scroll position chasing {@link #scrollRow}. The logical scroll stays an integer
     * so paging and clamping keep working on whole rows; this is what actually gets drawn, so
     * a mouse-wheel notch glides instead of jumping a row.
     */
    private final Anim scrollAnim = new Anim();
    /** Per-row hover progress, keyed by entry index. Pruned once a row is back at rest. */
    private final Map<Integer, Anim> rowHover = new HashMap<>();
    /** Scrollbar thumb hover/drag highlight. */
    private final Anim thumbHover = new Anim();
    /** Panel fade-in, so an overlay does not slam onto the screen behind it. */
    private final Anim panelOpen = new Anim();

    /** Index into {@link #allTabLabels()} — own tabs first, Selected tab last. */
    private int currentTab = 0;
    private final Anim tabIndicatorXAnim = new Anim();
    private final Anim tabIndicatorWAnim = new Anim();
    private boolean tabIndicatorInit = false;
    /** Per-tab hover progress, indexed by tab position. */
    private final Map<Integer, Anim> tabHover = new HashMap<>();

    private static final int ADD_BTN_W = 100;
    private static final int ADD_BTN_H = 20;
    private static final int SELECTALL_BTN_H = 14;
    private static final int SELECTALL_BTN_GAP = 4;

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
    private final Anim addHover = new Anim();

    private record Sel<E>(String value, E entry) {}

    /** Hover-scroll for row labels. One per list — only the hovered row can be scrolling. */
    private final MarqueeText marquee = new MarqueeText();

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
     * Like {@link #drawRowText} but, once the row has been hovered long enough and the text is
     * too wide to fit, scrolls it horizontally (marquee) inside the row's bounds.
     */
    protected final void drawRowMarqueeText(GuiGraphics g, Font font, String text,
                                            int x, int y, int w, int h,
                                            boolean hovered, int rowIndex) {
        // The row index alone is not a stable identity: scrolling or re-filtering puts a
        // different entry under the same index, and the label would carry on scrolling from
        // wherever the previous one had got to. Pairing it with the text restarts the delay
        // whenever either changes, while keeping two same-named rows apart.
        String key = rowIndex + "\0" + text;
        // Animations are always on here — unlike the graph, the searchable lists have no
        // config switch, and the rest of this class animates unconditionally too.
        marquee.draw(g, font, key, text, x + 3, y + 4, w - 6, y, y + h,
                hovered ? 0xFFFFFF : 0xBBBBBB, hovered, true);
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
        panelH = getTabBarHeight() + SearchBar.HEIGHT + PADDING * 2 + visibleRows() * ROW_HEIGHT + PADDING + 4
                + selectAllRowH()
                + (multiSelect ? ADD_BTN_H + PADDING : 0);
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        resetListAnim();
        panelOpen.set(0.0f);
        searchBar.setFocused(true);
        searchBar.setText(""); // triggers applyFilter against fresh allEntries
    }

    /**
     * Drops the scroll and hover animations onto their resting values. Called whenever the row
     * set is replaced: the indices now mean different entries, so animating from the old state
     * would show the new list gliding away from something that is no longer there.
     */
    private void resetListAnim() {
        scrollAnim.set(0.0f);
        rowHover.clear();
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
        resetListAnim();
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
        maxScrollRow = Math.max(0, visibleCount() - visibleRows());
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
        resetListAnim();
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

    /** Reserved height for the select-all/deselect-all row. Only present in multi-select. */
    private int selectAllRowH() {
        return multiSelect ? SELECTALL_BTN_H + PADDING : 0;
    }

    /**
     * Rendered list rows. Two fewer in multi-select so the panel doesn't grow taller than the
     * single-select overlays once the tab bar, button row and Add button are added.
     */
    protected int visibleRows() {
        return multiSelect ? VISIBLE_ROWS - 2 : VISIBLE_ROWS;
    }

    /** Y of the select-all button row (just below the search bar). */
    private int selectAllRowY() {
        return searchTopY() + SearchBar.HEIGHT + PADDING;
    }

    /** In multi-select the button row is always present; the buttons grey out when idle. */
    private boolean selectAllButtonsVisible() {
        return multiSelect;
    }

    /**
     * Whether the buttons are actionable: on own tabs only once a query narrows the list
     * (select-all with no filter would mean the whole registry), always on the Selected tab.
     */
    private boolean selectAllContextActive() {
        if (isSelectedTabActive()) return true;
        String q = searchBar.getText();
        return q != null && !q.isEmpty();
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

    /** How many currently-found entries "Select all" would add. 0 when idle (no filter). */
    private int selectableFoundCount() {
        if (!selectAllContextActive()) return 0;
        int n = 0;
        if (isSelectedTabActive()) {
            for (Sel<T> ref : selectedView) if (!selected.containsKey(ref.value())) n++;
        } else {
            for (T entry : filteredEntries) if (!selected.containsKey(selectionValueOf(entry))) n++;
        }
        return n;
    }

    /**
     * How many currently-found entries "Deselect all" would remove. Unlike selecting, this is
     * available without a filter too — it only ever removes things already selected, so clearing
     * the whole selection with no query is safe.
     */
    private int deselectableFoundCount() {
        int n = 0;
        if (isSelectedTabActive()) {
            for (Sel<T> ref : selectedView) if (selected.containsKey(ref.value())) n++;
        } else {
            for (T entry : filteredEntries) if (selected.containsKey(selectionValueOf(entry))) n++;
        }
        return n;
    }

    private void selectAllFound() {
        if (isSelectedTabActive()) {
            for (Sel<T> ref : selectedView) selected.put(ref.value(), ref.entry());
        } else {
            for (T entry : filteredEntries) selected.put(selectionValueOf(entry), entry);
        }
        refreshSelectedPlaceholder();
    }

    private void deselectAllFound() {
        if (isSelectedTabActive()) {
            for (Sel<T> ref : selectedView) selected.remove(ref.value());
        } else {
            for (T entry : filteredEntries) selected.remove(selectionValueOf(entry));
        }
        refreshSelectedPlaceholder();
    }

    private void refreshSelectedPlaceholder() {
        if (isSelectedTabActive()) {
            searchBar.setPlaceholder(Component.translatable(
                    "editor.historystages.search.selected.placeholder", selected.size()).getString());
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
            tabIndicatorXAnim.set(tabXs[activeIdx]);
            tabIndicatorWAnim.set(tabWs[activeIdx]);
            tabIndicatorInit = true;
        }

        // The underline slides between tabs rather than jumping, which is what tells the eye
        // the two tabs are the same control in different states.
        float indicatorX = tabIndicatorXAnim.approach(tabXs[activeIdx], Timing.SCROLL_HALF_LIFE_MS);
        float indicatorW = tabIndicatorWAnim.approach(tabWs[activeIdx], Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorXAnim.settle(tabXs[activeIdx], 0.5f);
        tabIndicatorWAnim.settle(tabWs[activeIdx], 0.5f);

        for (int i = 0; i < n; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            float hp = Ease.outCubic(tabHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hovered && !active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            int bg = active ? 0x40FFCC00 : Fade.mix(0x15FFFFFF, 0x25FFFFFF, hp);
            g.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);
            int textColor = active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, hp);
            g.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        g.fill(Math.round(indicatorX), tabY + TAB_HEIGHT - 2,
                Math.round(indicatorX + indicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);
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
        return searchTopY() + SearchBar.HEIGHT + PADDING + selectAllRowH();
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
    private void drawRowBackground(GuiGraphics g, int x, int y, int w, float hover, RowState state) {
        if (state == RowState.NORMAL) {
            g.fill(x, y, x + w, y + ROW_HEIGHT, Fade.mix(0xFF252525, 0xFF353535, hover));
            // Gold edge that grows in from the left, so a hovered row is identifiable at a
            // glance even where several rows sit at similar brightness.
            if (hover > 0.001f) {
                g.fill(x, y, x + 1, y + ROW_HEIGHT, Fade.rgba(0xFFCC00, hover * 0.8f));
            }
            return;
        }
        int border;
        int bg;
        if (state == RowState.SELECTED) {
            border = Fade.mix(0xFFFFCC00, 0xFFFF8800, hover);
            bg = Fade.mix(0xFF2A2510, 0xFF553A10, hover);
        } else {
            border = Fade.mix(0xFF552020, 0xFF884444, hover);
            bg = Fade.mix(0xFF1A0D0D, 0xFF3A1A1A, hover);
        }
        g.fill(x, y, x + w, y + ROW_HEIGHT, border);
        g.fill(x + 1, y + 1, x + w - 1, y + ROW_HEIGHT - 1, bg);
    }

    /**
     * Scroll position the last frame actually drew at. Clicks resolve against this rather than
     * {@link #scrollRow} so that during a scroll animation the player hits the row they can
     * see, not the one the logical scroll has already moved to.
     */
    private float drawnScroll() {
        return scrollAnim.value();
    }

    /** Index of the first (possibly partly clipped) row drawn at {@link #drawnScroll()}. */
    private int firstDrawnRow() {
        return (int) Math.floor(drawnScroll());
    }

    /** Pixels the drawn rows are shifted up by, i.e. how far into the first row we are. */
    private int drawnPixelOffset() {
        return Math.round((drawnScroll() - firstDrawnRow()) * ROW_HEIGHT);
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
        float hp = Ease.outCubic(addHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        if (canConfirm()) {
            String label = Component.translatable("editor.historystages.search.add",
                    selected.size()).getString();
            SearchPanelChrome.renderStyledButton(g, font, x, y, ADD_BTN_W, ADD_BTN_H, label, hp);
        } else {
            g.fill(x, y, x + ADD_BTN_W, y + ADD_BTN_H, 0x20FFFFFF);
            g.fill(x, y, x + ADD_BTN_W, y + 1, 0x10FFFFFF);
            String label = Component.translatable("editor.historystages.search.add.empty").getString();
            g.drawString(font, label, x + (ADD_BTN_W - font.width(label)) / 2,
                    y + (ADD_BTN_H - 8) / 2, 0xFF666666, false);
        }
    }

    private void renderSelectAllRow(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int rowX = panelX + PADDING;
        int rowW = panelW - PADDING * 2;
        int btnW = (rowW - SELECTALL_BTN_GAP) / 2;
        int leftX = rowX;
        int rightX = rowX + btnW + SELECTALL_BTN_GAP;
        int y = selectAllRowY();

        int selCount = selectableFoundCount();
        int deselCount = deselectableFoundCount();
        boolean canSelect = selCount > 0;
        boolean canDeselect = deselCount > 0;
        boolean selHovered = canSelect && isInBtn(mouseX, mouseY, leftX, y, btnW);
        boolean deselHovered = canDeselect && isInBtn(mouseX, mouseY, rightX, y, btnW);

        String selLabel = Component.translatable("editor.historystages.search.selectall", selCount).getString();
        String deselLabel = Component.translatable("editor.historystages.search.deselectall", deselCount).getString();
        drawSmallButton(g, font, leftX, y, btnW, selLabel, true, canSelect, selHovered);
        drawSmallButton(g, font, rightX, y, btnW, deselLabel, false, canDeselect, deselHovered);
    }

    private boolean isInBtn(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SELECTALL_BTN_H;
    }

    private void drawSmallButton(GuiGraphics g, Font font, int x, int y, int w,
                                 String label, boolean accentYellow, boolean enabled, boolean hovered) {
        int border;
        int bg;
        int text;
        if (!enabled) {
            border = 0xFF333333; bg = 0xFF1E1E1E; text = 0xFF555555;
        } else if (accentYellow) {
            border = hovered ? 0xFFFFCC00 : 0xFFB38F00;
            bg = hovered ? 0xFF553A10 : 0xFF2A2510;
            text = hovered ? 0xFFFFFFFF : 0xFFDDCC88;
        } else {
            border = hovered ? 0xFF884444 : 0xFF663030;
            bg = hovered ? 0xFF3A1A1A : 0xFF2A1010;
            text = hovered ? 0xFFFFFFFF : 0xFFDDAAAA;
        }
        g.fill(x, y, x + w, y + SELECTALL_BTN_H, border);
        g.fill(x + 1, y + 1, x + w - 1, y + SELECTALL_BTN_H - 1, bg);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (SELECTALL_BTN_H - 8) / 2, text, false);
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

        // Rises the last few pixels into place, matching the context menu's entrance. Only the
        // position is animated, not the opacity: the panel is opaque and its rows are drawn on
        // top, so fading the chrome alone would show the screen through the frame.
        float open = Ease.outCubic(panelOpen.ramp(1.0f, Timing.POPUP_MS));
        g.pose().pushPose();
        g.pose().translate(0.0f, (1.0f - open) * 5.0f, 0.0f);

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(g, font, mouseX, mouseY);

        int searchX = panelX + PADDING;
        int searchY = searchTopY();
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(g, font, mouseX, mouseY);
        if (selectAllButtonsVisible()) renderSelectAllRow(g, font, mouseX, mouseY);

        int listX = panelX + PADDING;
        int listY = listTopY();
        int listW = panelW - PADDING * 2 - 8;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);

        // Sub-row scroll: the wheel still moves whole rows, but the rows glide there.
        scrollAnim.approach(scrollRow, Timing.SCROLL_HALF_LIFE_MS);
        scrollAnim.settle(scrollRow, 0.01f);
        int firstRow = firstDrawnRow();
        int pixelOffset = drawnPixelOffset();
        int listH = visibleRows() * ROW_HEIGHT;

        // One extra row is drawn so the partly-scrolled row at the bottom edge has content;
        // the scissor is what stops both edge rows from spilling out of the panel.
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i <= visibleRows(); i++) {
            int index = firstRow + i;
            int rowY = listY + i * ROW_HEIGHT - pixelOffset;

            boolean rowHovered = !filterUiHovered && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= listY && mouseY < listY + listH;

            if (index < 0 || index >= visibleCount()) {
                // Empty slots don't react to hover — there's nothing there to point at.
                drawRowBackground(g, listX, rowY, listW, 0.0f, RowState.NORMAL);
                continue;
            }

            float hp = Ease.outCubic(rowHover.computeIfAbsent(index, k -> new Anim())
                    .ramp(rowHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            if (isSelectedTabActive()) {
                Sel<T> ref = selectedView.get(index);
                boolean stillSelected = selected.containsKey(ref.value());
                drawRowBackground(g, listX, rowY, listW, hp,
                        stillSelected ? RowState.SELECTED : RowState.DESELECTED);
                renderSelectedRow(g, font, ref.value(), ref.entry(),
                        listX, rowY, listW, ROW_HEIGHT, rowHovered, index);
            } else {
                T entry = filteredEntries.get(index);
                boolean isSelected = multiSelect && selected.containsKey(selectionValueOf(entry));
                drawRowBackground(g, listX, rowY, listW, hp,
                        isSelected ? RowState.SELECTED : RowState.NORMAL);
                renderRow(g, font, entry, listX, rowY, listW, ROW_HEIGHT, rowHovered, index);
            }
        }
        g.disableScissor();
        pruneRowHover(firstRow);

        // Selected tab with nothing selected: hint in the middle rather than a blank grid.
        if (isSelectedTabActive() && selectedSnapshot.isEmpty()) {
            String hint = Component.translatable("editor.historystages.search.selected.empty").getString();
            g.drawString(font, hint, listX + (listW - font.width(hint)) / 2,
                    listY + (listH - 8) / 2, 0xFF888888, false);
        }

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + listH;
            g.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            // Same helpers the press and drag paths use — the thumb has to be grabbable exactly
            // where it is drawn. It follows the drawn scroll, so thumb and rows move as one.
            int thumbHeight = thumbHeight();
            int thumbY = thumbTop(scrollBarTop, thumbHeight);

            boolean overThumb = mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= scrollBarTop && mouseY < scrollBarBottom;
            float th = Ease.outCubic(thumbHover.ramp(overThumb || draggingScrollbar,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight,
                    Fade.mix(0xFF888888, 0xFFFFCC00, th));
        }

        if (multiSelect) renderAddButton(g, font, mouseX, mouseY);

        afterRender(g, font, mouseX, mouseY);

        g.pose().popPose();
    }

    /**
     * Drops hover state for rows that have settled and are far from view. Without this the map
     * would keep one entry per row the cursor ever touched, for the lifetime of the panel.
     */
    private void pruneRowHover(int firstRow) {
        int keepFrom = firstRow - visibleRows();
        int keepTo = firstRow + visibleRows() * 2;
        Iterator<Map.Entry<Integer, Anim>> it = rowHover.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> e = it.next();
            int idx = e.getKey();
            if ((idx < keepFrom || idx > keepTo) && e.getValue().isAt(0.0f)) {
                it.remove();
            }
        }
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

        if (selectAllButtonsVisible()) {
            int rowX = panelX + PADDING;
            int rowW = panelW - PADDING * 2;
            int btnW = (rowW - SELECTALL_BTN_GAP) / 2;
            int leftX = rowX;
            int rightX = rowX + btnW + SELECTALL_BTN_GAP;
            int y = selectAllRowY();
            if (isInBtn(mouseX, mouseY, leftX, y, btnW)) {
                if (selectableFoundCount() > 0) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    selectAllFound();
                }
                return true;
            }
            if (isInBtn(mouseX, mouseY, rightX, y, btnW)) {
                if (deselectableFoundCount() > 0) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    deselectAllFound();
                }
                return true;
            }
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
                    && mouseY >= listY && mouseY < listY + visibleRows() * ROW_HEIGHT) {
                int thumbH = thumbHeight();
                int thumbTop = thumbTop(listY, thumbH);
                boolean onThumb = mouseY >= thumbTop && mouseY < thumbTop + thumbH;
                // Grabbing the thumb keeps it under the cursor; clicking the empty track brings
                // it to the cursor, which is what a click on the track is asking for.
                scrollGrabOffset = onThumb ? (int) (mouseY - thumbTop) : thumbH / 2;
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        // Resolved against the positions the last frame drew at, so a click during a scroll
        // animation selects the row under the cursor rather than the one the logical scroll
        // has already advanced to.
        int firstRow = firstDrawnRow();
        int pixelOffset = drawnPixelOffset();
        int listH = visibleRows() * ROW_HEIGHT;
        for (int i = 0; i <= visibleRows(); i++) {
            int index = firstRow + i;
            int rowY = listY + i * ROW_HEIGHT - pixelOffset;
            if (index >= 0 && index < visibleCount() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= listY && mouseY < listY + listH) {
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

    /** Height of the scrollbar thumb. Shared by the render, press and drag paths. */
    private int thumbHeight() {
        int listH = visibleRows() * ROW_HEIGHT;
        return Math.max(10, (int) ((float) visibleRows() / (maxScrollRow + visibleRows()) * listH));
    }

    /** Top of the thumb where it was last drawn, so grabbing it lands on what the user sees. */
    private int thumbTop(int listY, int thumbHeight) {
        if (maxScrollRow <= 0) return listY;
        int listH = visibleRows() * ROW_HEIGHT;
        return listY + Math.round(drawnScroll() / maxScrollRow * (listH - thumbHeight));
    }

    private void updateScrollFromMouse(double mouseY, int listY) {
        int listH = visibleRows() * ROW_HEIGHT;
        float usableH = listH - thumbHeight();
        if (usableH > 0) {
            // Offset by where the thumb was grabbed, not by half its height: centring it on the
            // cursor makes the list jump away the moment the user touches the thumb, and on a
            // list of a few thousand rows that jump is hundreds of entries.
            float ratio = (float) (mouseY - listY - scrollGrabOffset) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    /**
     * Rows moved per wheel notch. One row for anything of ordinary length, growing for long
     * lists — a thousand entries at one row per notch takes the better part of a hundred turns,
     * which reads as the wheel not working rather than as a long list.
     *
     * <p>Capped at half a page: a full page per notch leaves no overlap between what was on
     * screen and what replaces it, so the reader loses their place.
     */
    private int wheelStep() {
        int totalRows = maxScrollRow + visibleRows();
        return Math.max(1, Math.min(Math.max(1, visibleRows() / 2), totalRows / 100));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow,
                    scrollRow - (int) scrollY * wheelStep()));
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
