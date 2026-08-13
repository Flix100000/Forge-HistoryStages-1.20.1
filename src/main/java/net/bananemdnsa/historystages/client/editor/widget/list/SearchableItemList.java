package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;

import net.bananemdnsa.historystages.client.editor.widget.SearchBar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creative menu-style item grid with search bar.
 * Supports a Registry tab, an Inventory tab, and — when multi-select is
 * enabled — a "Selected" tab that lists all currently selected items for
 * review and deselection. The Selected tab reuses the shared SearchBar with
 * a different placeholder.
 */
public class SearchableItemList {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int PADDING = 6;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;
    private static final int SCROLLBAR_GAP = 6;
    private static final int ADD_BTN_W = 100;
    private static final int ADD_BTN_H = 20;
    private static final int SELECTALL_BTN_H = 14;
    private static final int SELECTALL_BTN_GAP = 4;

    private static final int TAB_REGISTRY = 0;
    private static final int TAB_INVENTORY = 1;
    private static final int TAB_SELECTED = 2;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    /**
     * Snapshot of selected items taken when the Selected tab is entered. Stays
     * stable while on the tab — toggling deselect/reselect modifies the
     * underlying sets but leaves the snapshot intact, so the grid doesn't
     * shift under the cursor and the user can undo a misclick. Cleared on tab
     * exit; rebuilt on next entry.
     */
    private final List<SelectedRef> selectedSnapshot = new ArrayList<>();
    /** Filtered view of {@link #selectedSnapshot} for the current search filter. */
    private final List<SelectedRef> selectedView = new ArrayList<>();
    private final Consumer<String> onSelect;
    /**
     * Optional callback invoked when the user holds Ctrl while confirming the
     * add. The JsonObject contains match criteria built from the selected
     * inventory ItemStack's NBT tag (legacy 1.20 NBT format — top-level keys
     * mirror the stack's CompoundTag). Null when the caller doesn't support
     * per-entry NBT (then Ctrl is treated as a regular add).
     */
    private BiConsumer<String, JsonObject> onSelectWithNbt = null;
    private final Supplier<Collection<String>> alreadyAddedSupplier;
    private final SearchBar searchBar;

    private int panelX, panelY, panelW, panelH;
    private int centerX, centerY;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

    private int currentTab = TAB_REGISTRY;
    private boolean multiSelect = false;
    private final Set<String> selectedRegistryIds = new LinkedHashSet<>();
    private final Set<Integer> selectedInventorySlots = new LinkedHashSet<>();
    /** Hover progress per inventory slot. Bounded by the 41 slots the grid can show. */
    private final Map<Integer, Anim> slotHover = new HashMap<>();
    /** Selection progress per slot, so ticking one is a visible change rather than a repaint. */
    private final Map<Integer, Anim> slotSelect = new HashMap<>();
    /** Subset of {@link #selectedInventorySlots} that were Ctrl-clicked and
     *  should be added with NBT match criteria built from the live ItemStack. */
    private final Set<Integer> nbtSelectedInventorySlots = new LinkedHashSet<>();

    private final Anim tabIndicatorXAnim = new Anim();
    private final Anim tabIndicatorWAnim = new Anim();
    private boolean tabIndicatorInit = false;

    private final Anim addHoverProgress = new Anim();

    private Set<String> modFilterSet = null;

    /** Optional "is locked by stage" predicate enabled via {@link #setLockedFilter}. */
    private java.util.function.Predicate<String> lockedFilterFn = null;

    public SearchableItemList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableItemList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        this.onSelect = onSelect;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.searchBar = SearchPanelChrome.createSearchBar("Search items...", this::applyFilter, alreadyAddedSupplier);

        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                ItemStack stack = new ItemStack(item);
                String searchName = stack.getHoverName().getString().toLowerCase();
                allItems.add(new ItemEntry(key.toString(), stack, searchName));
            }
        }
        filteredItems.addAll(allItems);
    }

    public void setMultiSelect(boolean multi) {
        this.multiSelect = multi;
    }

    /**
     * Enables Ctrl-add: when the user holds Ctrl while confirming the add, this
     * callback fires instead of {@link #onSelect}, with NBT criteria built from
     * the inventory ItemStack. Has no effect for registry-tab items (no stack
     * data to dump) — those still go through {@link #onSelect}.
     */
    public void setOnSelectWithNbt(BiConsumer<String, JsonObject> onSelectWithNbt) {
        this.onSelectWithNbt = onSelectWithNbt;
    }

    public void show(int centerX, int centerY, int parentWidth) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        this.currentTab = TAB_REGISTRY;
        this.selectedRegistryIds.clear();
        this.selectedInventorySlots.clear();
        this.nbtSelectedInventorySlots.clear();
        this.tabIndicatorInit = false;
        searchBar.setPlaceholder("Search items...");
        searchBar.setText("");
        recalcPanelSize();
    }

    private boolean isInventoryTab() {
        return currentTab == TAB_INVENTORY;
    }

    private boolean isSelectedTab() {
        return currentTab == TAB_SELECTED;
    }

    /** Reserved height for the select-all row in registry / selected modes. */
    private int selectAllRowReserve() {
        return multiSelect ? SELECTALL_BTN_H + 4 : 0;
    }

    /** Top Y of the item grid, shifted down by the reserved select-all row. */
    private int gridTopY() {
        int searchY = panelY + PADDING + TAB_HEIGHT + 4;
        return searchY + SearchBar.HEIGHT + PADDING + selectAllRowReserve();
    }

    /**
     * Number of item rows that fit between the grid top and the Add button on the Registry /
     * Selected tabs. Derived from the fixed panel height so the grid fills the panel instead of
     * the panel shrinking to the grid.
     */
    private int gridRows() {
        int gridBottom = panelY + panelH - PADDING - ADD_BTN_H - PADDING;
        int rows = (gridBottom - gridTopY()) / SLOT_SIZE;
        return Math.max(1, rows);
    }

    /** Y of the select-all button row (just below the search bar). */
    private int selectAllRowY() {
        int searchY = panelY + PADDING + TAB_HEIGHT + 4;
        return searchY + SearchBar.HEIGHT + PADDING;
    }

    /** The button row is always present on the Registry / Selected tabs; buttons grey out when idle. */
    private boolean selectAllButtonsVisible() {
        return multiSelect && !isInventoryTab();
    }

    /**
     * Whether the buttons are actionable: on the Registry tab only once a query narrows the list
     * (select-all with no filter would mean the whole registry), always on the Selected tab.
     */
    private boolean selectAllContextActive() {
        if (isSelectedTab()) return true;
        String q = searchBar.getText();
        return q != null && !q.isEmpty();
    }

    private int totalSelectionCount() {
        return selectedRegistryIds.size() + selectedInventorySlots.size();
    }

    /**
     * The Selected tab is present for the whole lifetime of a multi-select panel, empty or
     * not. Showing it only once something is selected would resize the tab bar mid-click.
     */
    private boolean showSelectedTab() {
        return multiSelect;
    }

    private boolean isStillSelected(SelectedRef ref) {
        return ref.fromInventory
                ? selectedInventorySlots.contains(ref.inventorySlot)
                : selectedRegistryIds.contains(ref.entry.id);
    }

    private int calcMinTabWidth() {
        Font font = Minecraft.getInstance().font;
        int total = PADDING * 2;
        List<String> labels = tabLabels();
        for (int i = 0; i < labels.size(); i++) {
            total += font.width(labels.get(i)) + TAB_PAD * 2;
            if (i < labels.size() - 1)
                total += 2;
        }
        return total + PADDING;
    }

    private void recalcPanelSize() {
        // Fixed size across all tabs = the Inventory tab's footprint (the tallest layout), so
        // switching tabs never resizes the window. On the Registry / Selected tabs the item grid
        // grows to fill the height instead of shrinking the panel (see gridRows()).
        int gridW = SLOT_SIZE * 9;
        panelW = PADDING + gridW + PADDING + 8;
        int topAreaH = 4 * SLOT_SIZE + 4;
        panelH = PADDING + TAB_HEIGHT + 4
                + topAreaH + 4
                + 3 * SLOT_SIZE + 6
                + SLOT_SIZE + 6
                + ADD_BTN_H + PADDING;

        int minW = calcMinTabWidth();
        if (panelW < minW)
            panelW = minW;

        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        clampToScreen();
    }

    private void clampToScreen() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;
        if (panelX + panelW > screenW - 4)
            panelX = screenW - panelW - 4;
        if (panelY + panelH > screenH - 4)
            panelY = screenH - panelH - 4;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setModFilter(Set<String> modIds) {
        this.modFilterSet = modIds;
        applyFilter(searchBar.getText());
    }

    public void setFilter(String filter) {
        searchBar.setText(filter);
    }

    private void applyFilter(String filter) {
        this.scrollRow = 0;

        filteredItems.clear();
        List<ItemEntry> baseItems = allItems;
        if (modFilterSet != null && !modFilterSet.isEmpty()) {
            baseItems = new ArrayList<>();
            for (ItemEntry entry : allItems) {
                if (matchesModFilter(entry))
                    baseItems.add(entry);
            }
        }
        for (ItemEntry entry : baseItems) {
            if (matchesFilter(entry, filter))
                filteredItems.add(entry);
        }

        if (isSelectedTab()) {
            applySelectedFilter();
        }
        updateMaxScroll();
    }

    private boolean matchesModFilter(ItemEntry entry) {
        if (modFilterSet == null)
            return true;
        String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
        return modFilterSet.contains(modId);
    }

    private boolean matchesFilter(ItemEntry entry, String f) {
        if (!matchesDropdownFilters(entry.id))
            return false;
        if (f.isEmpty())
            return true;
        if (f.startsWith("@")) {
            String modFilter = f.substring(1);
            String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
            return modId.contains(modFilter);
        }
        return entry.id.contains(f) || entry.searchName.contains(f);
    }

    private boolean matchesDropdownFilters(String id) {
        if (!SearchPanelChrome.passesDefaultFilters(searchBar, id, alreadyAddedSupplier)) return false;
        if (lockedFilterFn != null && searchBar.filters().isActive("hide_locked")
                && id != null && lockedFilterFn.test(id)) return false;
        return true;
    }

    /**
     * Registers a "Hide locked" filter (active by default) that excludes items whose id
     * matches {@code isLocked.test(id)}. Used by the auto-trigger editor to hide items
     * the current stage already locks.
     */
    public void setLockedFilter(String label, java.util.function.Predicate<String> isLocked) {
        this.lockedFilterFn = isLocked;
        searchBar.filters().addOption("hide_locked", label, null, true);
        applyFilter(searchBar.getText() == null ? "" : searchBar.getText());
    }

    /** Captures the current selection sets as the frozen Selected-tab snapshot. */
    private void rebuildSelectedSnapshot() {
        selectedSnapshot.clear();
        for (String id : selectedRegistryIds) {
            for (ItemEntry entry : allItems) {
                if (entry.id.equals(id)) {
                    selectedSnapshot.add(new SelectedRef(entry, false, -1));
                    break;
                }
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (Integer slot : selectedInventorySlots) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    String id = key != null ? key.toString() : "?";
                    String searchName = stack.getHoverName().getString().toLowerCase();
                    ItemEntry entry = new ItemEntry(id, stack, searchName);
                    selectedSnapshot.add(new SelectedRef(entry, true, slot));
                }
            }
        }
        applySelectedFilter();
    }

    /** Filters the snapshot by the current search filter into the rendered view. */
    private void applySelectedFilter() {
        selectedView.clear();
        String f = searchBar.getText();
        for (SelectedRef ref : selectedSnapshot) {
            if (matchesFilter(ref.entry, f))
                selectedView.add(ref);
        }
    }

    private void updateMaxScroll() {
        int total = isSelectedTab() ? selectedView.size() : filteredItems.size();
        int totalRows = (total + GRID_COLS - 1) / GRID_COLS;
        maxScrollRow = Math.max(0, totalRows - gridRows());
    }

    private List<String> tabLabels() {
        List<String> labels = new ArrayList<>(3);
        labels.add("Registry");
        labels.add("Inventory");
        if (showSelectedTab()) {
            labels.add("Selected (" + totalSelectionCount() + ")");
        }
        return labels;
    }

    private int getGridStartX(boolean withScrollbar) {
        int blockW = GRID_COLS * SLOT_SIZE + (withScrollbar ? SCROLLBAR_GAP : 0);
        return panelX + (panelW - blockW) / 2;
    }

    // --- Rendering ---

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        SearchPanelChrome.renderFrame(guiGraphics, panelX, panelY, panelW, panelH);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        if (isInventoryTab()) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else if (isSelectedTab()) {
            renderSelectedMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        List<String> labels = tabLabels();
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

        float targetX = tabXs[activeIdx];
        float targetW = tabWs[activeIdx];
        tabIndicatorXAnim.approach(targetX, Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorWAnim.approach(targetW, Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorXAnim.settle(targetX, 0.5f);
        tabIndicatorWAnim.settle(targetW, 0.5f);

        for (int i = 0; i < n; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);

            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        guiGraphics.fill(Math.round(tabIndicatorXAnim.value()), tabY + TAB_HEIGHT - 2,
                Math.round(tabIndicatorXAnim.value() + tabIndicatorWAnim.value()), tabY + TAB_HEIGHT, 0xFFFFCC00);

        guiGraphics.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING, tabY + TAB_HEIGHT + 1,
                0xFF555555);
    }

    private void renderRegistryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;

        if (modFilterSet != null && modFilterSet.isEmpty()) {
            renderEmptyState(guiGraphics, font, topOffset);
            return;
        }

        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);
        if (selectAllButtonsVisible()) renderSelectAllRow(guiGraphics, font, mouseX, mouseY);

        int gridX = getGridStartX(true);
        int gridY = gridTopY();

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                boolean slotHovered = !filterUiHovered && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                        slotHovered ? 0xFF4A4A4A : 0xFF252525);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        slotHovered ? 0xFF353535 : 0xFF1A1A1A);

                if (index < filteredItems.size()) {
                    ItemEntry entry = filteredItems.get(index);
                    boolean isSelected = selectedRegistryIds.contains(entry.id);
                    if (isSelected) {
                        guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFFFFCC00);
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0xFF2A2510);
                    }
                    guiGraphics.renderItem(entry.stack, slotX + 1, slotY + 1);
                    if (isSelected) {
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0x40FFCC00);
                    }
                }
            }
        }

        if (maxScrollRow > 0) {
            renderScrollbar(guiGraphics, gridX, gridY);
        }

        renderAddButton(guiGraphics, font, mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (!filterUiHovered) {
            for (int row = 0; row < gridRows(); row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    int index = startIndex + row * GRID_COLS + col;
                    int slotX = gridX + col * SLOT_SIZE;
                    int slotY = gridY + row * SLOT_SIZE;

                    if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                            && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                        ItemEntry entry = filteredItems.get(index);
                        renderTooltip(guiGraphics, font, mouseX, mouseY,
                                entry.stack.getHoverName().getString() + " §7(" + entry.id + ")");
                    }
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderEmptyState(GuiGraphics guiGraphics, Font font, int topOffset) {
        String msg = Component.translatable("editor.historystages.no_mods_locked").getString();
        int maxW = panelW - PADDING * 4;
        List<String> lines = wrapText(font, msg, maxW);
        int totalH = lines.size() * 10;
        int startY = panelY + topOffset + (panelH - topOffset - totalH) / 2;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int lw = font.width(l);
            guiGraphics.drawString(font, l, panelX + (panelW - lw) / 2, startY + i * 10, 0xFF888888, false);
        }
    }

    private List<String> wrapText(Font font, String msg, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : msg.split(" ")) {
            if (line.length() > 0 && font.width(line + " " + word) > maxW) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0)
                    line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0)
            lines.add(line.toString());
        return lines;
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int gridX, int gridY) {
        int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
        int scrollBarTop = gridY;
        int scrollBarBottom = gridY + gridRows() * SLOT_SIZE;
        int scrollBarHeight = scrollBarBottom - scrollBarTop;
        guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
        int thumbHeight = Math.max(10, (int) ((float) gridRows() / (maxScrollRow + gridRows()) * scrollBarHeight));
        int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
        guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
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

    private void renderAddButton(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int addBtnX = panelX + (panelW - ADD_BTN_W) / 2;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;

        boolean canAdd = canConfirm();
        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN_W
                && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
        float addHoverProgressValue = Ease.outCubic(addHoverProgress.ramp(addHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, ADD_BTN_W, ADD_BTN_H, addButtonLabel(),
                    addHoverProgressValue);
        } else {
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + ADD_BTN_W, addBtnY + ADD_BTN_H, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + ADD_BTN_W, addBtnY + 1, 0x10FFFFFF);
            String addText = "Select an Item";
            guiGraphics.drawString(font, addText, addBtnX + (ADD_BTN_W - font.width(addText)) / 2,
                    addBtnY + (ADD_BTN_H - 8) / 2, 0xFF666666, false);
        }
    }

    private boolean canConfirm() {
        if (multiSelect)
            return totalSelectionCount() > 0;
        if (isInventoryTab()) {
            if (selectedInventorySlots.isEmpty())
                return false;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
                return false;
            int slot = selectedInventorySlots.iterator().next();
            return !player.getInventory().getItem(slot).isEmpty();
        }
        return !selectedRegistryIds.isEmpty();
    }

    private String addButtonLabel() {
        return multiSelect ? "Add Items (" + totalSelectionCount() + ")" : "Add Item";
    }

    private int[] getInvLayout() {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int gridX = getGridStartX(false);
        int topY = panelY + topOffset + 2;
        int topAreaH = 4 * SLOT_SIZE + 4;
        int mainY = topY + topAreaH + 4;
        int hotbarY = mainY + 3 * SLOT_SIZE + 6;
        return new int[] { gridX, topY, mainY, hotbarY };
    }

    private boolean isItemAllowedByModFilter(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null)
            return false;
        if (modFilterSet != null && !modFilterSet.contains(key.getNamespace()))
            return false;
        return matchesDropdownFilters(key.toString());
    }

    private void renderInventoryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        if (modFilterSet != null && modFilterSet.isEmpty()) {
            renderEmptyState(guiGraphics, font, PADDING + TAB_HEIGHT + 4);
            return;
        }

        int[] layout = getInvLayout();
        int gridX = layout[0];
        int topY = layout[1];
        int mainY = layout[2];
        int hotbarY = layout[3];

        int armorX = gridX;
        int entityAreaX = gridX + SLOT_SIZE + 4;
        int entityAreaW = 9 * SLOT_SIZE - 2 * (SLOT_SIZE + 4);
        int entityAreaH = 4 * SLOT_SIZE;
        int offhandX = gridX + 9 * SLOT_SIZE - SLOT_SIZE;

        int[] armorSlots = { 39, 38, 37, 36 };
        String[] armorLabels = { "H", "C", "L", "F" };
        for (int i = 0; i < 4; i++) {
            renderInventorySlot(guiGraphics, font, armorX, topY + i * SLOT_SIZE,
                    player.getInventory().getItem(armorSlots[i]), armorSlots[i], mouseX, mouseY, armorLabels[i]);
        }

        guiGraphics.fill(entityAreaX, topY, entityAreaX + entityAreaW, topY + entityAreaH, 0xFF0D0D0D);
        int entityCenterX = entityAreaX + entityAreaW / 2;
        int entityBottomY = topY + entityAreaH - 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                entityCenterX, entityBottomY, 25,
                (float) (entityCenterX - mouseX), (float) (entityBottomY - 50 - mouseY), player);

        renderInventorySlot(guiGraphics, font, offhandX, topY + 3 * SLOT_SIZE,
                player.getInventory().getItem(40), 40, mouseX, mouseY, "O");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, mainY + row * SLOT_SIZE,
                        player.getInventory().getItem(slotIndex), slotIndex, mouseX, mouseY, null);
            }
        }

        guiGraphics.fill(gridX, hotbarY - 3, gridX + 9 * SLOT_SIZE, hotbarY - 2, 0xFF333333);

        for (int col = 0; col < 9; col++) {
            renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, hotbarY,
                    player.getInventory().getItem(col), col, mouseX, mouseY, null);
        }

        renderAddButton(guiGraphics, font, mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        int hoveredSlot = getInventorySlotAt(mouseX, mouseY);
        if (hoveredSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(hoveredSlot);
            if (!stack.isEmpty()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key != null) {
                    renderTooltip(guiGraphics, font, mouseX, mouseY,
                            stack.getHoverName().getString() + " §7(" + key + ")");
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderSelectedMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);
        if (selectAllButtonsVisible()) renderSelectAllRow(guiGraphics, font, mouseX, mouseY);

        int gridX = getGridStartX(true);
        int gridY = gridTopY();

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                if (index < selectedView.size()) {
                    boolean slotHovered = !filterUiHovered && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                            && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                    SelectedRef ref = selectedView.get(index);
                    boolean active = isStillSelected(ref);
                    boolean nbtFlagged = active && ref.fromInventory
                            && nbtSelectedInventorySlots.contains(ref.inventorySlot);

                    int borderColor;
                    int bgColor;
                    if (nbtFlagged) {
                        borderColor = slotHovered ? 0xFF44AADD : 0xFF66CCFF;
                        bgColor = slotHovered ? 0xFF1A3340 : 0xFF10222A;
                    } else if (active) {
                        borderColor = slotHovered ? 0xFFFF8800 : 0xFFFFCC00;
                        bgColor = slotHovered ? 0xFF553A10 : 0xFF2A2510;
                    } else {
                        borderColor = slotHovered ? 0xFF884444 : 0xFF552020;
                        bgColor = slotHovered ? 0xFF3A1A1A : 0xFF1A0D0D;
                    }
                    guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor);
                    guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, bgColor);

                    guiGraphics.renderItem(ref.entry.stack, slotX + 1, slotY + 1);
                    if (nbtFlagged) {
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0x4066CCFF);
                    } else if (active) {
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0x40FFCC00);
                    } else {
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0xB0000000);
                        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                                0x40CC0000);
                    }
                } else {
                    guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF252525);
                    guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFF1A1A1A);
                }
            }
        }

        // Nothing selected yet: hint in the middle rather than a blank grid.
        if (selectedSnapshot.isEmpty()) {
            String hint = Component.translatable("editor.historystages.search.selected.empty").getString();
            int gridH = gridRows() * SLOT_SIZE;
            int gridBlockW = GRID_COLS * SLOT_SIZE;
            guiGraphics.drawString(font, hint, gridX + (gridBlockW - font.width(hint)) / 2,
                    gridY + (gridH - 8) / 2, 0xFF888888, false);
        }

        if (maxScrollRow > 0) {
            renderScrollbar(guiGraphics, gridX, gridY);
        }

        renderAddButton(guiGraphics, font, mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (!filterUiHovered) {
            for (int row = 0; row < gridRows(); row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    int index = startIndex + row * GRID_COLS + col;
                    int slotX = gridX + col * SLOT_SIZE;
                    int slotY = gridY + row * SLOT_SIZE;

                    if (index < selectedView.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                            && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                        ItemEntry entry = selectedView.get(index).entry;
                        renderTooltip(guiGraphics, font, mouseX, mouseY,
                                entry.stack.getHoverName().getString() + " §7(" + entry.id + ")");
                    }
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderInventorySlot(GuiGraphics guiGraphics, Font font, int x, int y,
            ItemStack stack, int slotIndex, int mouseX, int mouseY, String placeholder) {
        boolean isEmpty = stack.isEmpty();
        boolean isAllowed = isEmpty || isItemAllowedByModFilter(stack);
        boolean isSelected = selectedInventorySlots.contains(slotIndex);
        boolean isNbtSelected = nbtSelectedInventorySlots.contains(slotIndex);
        boolean isHovered = !isEmpty && isAllowed && mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y
                && mouseY < y + SLOT_SIZE;

        float hp = Ease.outCubic(slotHover.computeIfAbsent(slotIndex, k -> new Anim())
                .ramp(isHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        // Selection fades in on its own timer: in multi-select the grid is ticked slot by slot,
        // and an instant repaint gives no sense of which one just took the click.
        float sp = Ease.outCubic(slotSelect.computeIfAbsent(slotIndex, k -> new Anim())
                .ramp(isSelected || isNbtSelected, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        // Cyan marks "will be added with NBT", gold a plain selection.
        int selBorder = isNbtSelected ? 0xFF66CCFF : 0xFFFFCC00;
        int selBg = isNbtSelected ? 0xFF10222A : 0xFF2A2510;

        // Resting slot brightens under the cursor; the selection colour is layered on top of
        // that, so a hovered selected slot still reacts instead of freezing on its accent.
        int borderColor = Fade.mix(Fade.mix(0xFF252525, 0xFF4A4A4A, hp), selBorder, sp);
        int bgColor = Fade.mix(Fade.mix(0xFF1A1A1A, 0xFF353535, hp), selBg, sp);

        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, bgColor);

        if (!isEmpty) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            if (stack.getCount() > 1) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);
                String count = String.valueOf(stack.getCount());
                guiGraphics.drawString(font, count, x + SLOT_SIZE - 1 - font.width(count), y + SLOT_SIZE - 9, 0xFFFFFF,
                        true);
                guiGraphics.pose().popPose();
            }
            if (sp > 0.001f) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                        Fade.rgba(isNbtSelected ? 0x66CCFF : 0xFFCC00, 0.25f * sp));
            }
            if (!isAllowed) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xC0000000);
            }
        } else if (placeholder != null) {
            guiGraphics.drawString(font, placeholder, x + (SLOT_SIZE - font.width(placeholder)) / 2, y + 5, 0xFF444444,
                    false);
        }
    }

    private void renderStyledButton(GuiGraphics guiGraphics, Font font, int x, int y, int w, int h,
            String text, float hoverProgress) {
        int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - hoverProgress * 0x33);
        int bgB = (int) (0xFF - hoverProgress * 0xFF);
        guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        int accentAlpha = (int) (0x60 + hoverProgress * 0x9F);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        int textGray = (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, String text) {
        int tooltipW = font.width(text) + 8;
        int tooltipH = 16;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        if (tooltipX + tooltipW + 2 > screenW - 4)
            tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > screenH - 4)
            tooltipY = screenH - tooltipH - 6;
        if (tooltipX < 4)
            tooltipX = 4;
        if (tooltipY < 4)
            tooltipY = 4;
        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH, 0xFF1A1A1A);
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1, 0xFF0D0D1A);
        guiGraphics.drawString(font, text, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);
    }

    // --- Hit detection ---

    private int getInventorySlotAt(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return -1;

        int[] layout = getInvLayout();
        int gridX = layout[0];
        int topY = layout[1];
        int mainY = layout[2];
        int hotbarY = layout[3];

        int armorX = gridX;
        int offhandX = gridX + 9 * SLOT_SIZE - SLOT_SIZE;

        int[] armorSlots = { 39, 38, 37, 36 };
        for (int i = 0; i < 4; i++) {
            int slotY = topY + i * SLOT_SIZE;
            if (mouseX >= armorX && mouseX < armorX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return armorSlots[i];
            }
        }

        int offhandY = topY + 3 * SLOT_SIZE;
        if (mouseX >= offhandX && mouseX < offhandX + SLOT_SIZE && mouseY >= offhandY
                && mouseY < offhandY + SLOT_SIZE) {
            return 40;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = mainY + row * SLOT_SIZE;
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return slotIndex;
                }
            }
        }

        for (int col = 0; col < 9; col++) {
            int slotX = gridX + col * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= hotbarY && mouseY < hotbarY + SLOT_SIZE) {
                return col;
            }
        }

        return -1;
    }

    private int getTabAt(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int tabY = panelY + PADDING;
        List<String> labels = tabLabels();
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

    // --- Input handling ---

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible)
            return false;

        // Funnel/popup gets first crack — the popup may extend outside the panel.
        // Skipped on Inventory tab (no search bar there).
        if (!isInventoryTab() && searchBar.mouseClicked(mouseX, mouseY)) {
            return true;
        }

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab >= 0 && clickedTab != currentTab) {
            int maxTab = showSelectedTab() ? TAB_SELECTED : TAB_INVENTORY;
            if (clickedTab <= maxTab) {
                switchTab(clickedTab);
                return true;
            }
        }

        if (isSelectedTab()) {
            return handleSelectedClick(mouseX, mouseY);
        }
        if (isInventoryTab()) {
            return handleInventoryClick(mouseX, mouseY);
        }
        return handleRegistryClick(mouseX, mouseY);
    }

    private void switchTab(int newTab) {
        int oldTab = currentTab;
        currentTab = newTab;
        searchBar.filters().close();
        searchBar.setFocused(currentTab != TAB_INVENTORY);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        scrollRow = 0;
        if (oldTab == TAB_SELECTED && newTab != TAB_SELECTED) {
            selectedSnapshot.clear();
            selectedView.clear();
        }
        if (newTab == TAB_SELECTED) {
            searchBar.setPlaceholder("Search selected (" + totalSelectionCount() + ")...");
        } else {
            searchBar.setPlaceholder("Search items...");
        }
        searchBar.setText("");
        if (currentTab == TAB_SELECTED) {
            rebuildSelectedSnapshot();
        }
        updateMaxScroll();
        recalcPanelSize();
    }

    private boolean confirmAndAdd() {
        if (!canConfirm())
            return false;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        LocalPlayer player = Minecraft.getInstance().player;

        if (multiSelect) {
            // Registry-tab selections never carry stack data, so they always go
            // through onSelect — there's nothing to derive NBT from.
            for (String id : selectedRegistryIds) {
                onSelect.accept(id);
            }
            if (player != null) {
                for (Integer slot : selectedInventorySlots) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.isEmpty()) continue;
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key == null) continue;
                    emitInventoryItem(key.toString(), stack, nbtSelectedInventorySlots.contains(slot));
                }
            }
        } else if (!selectedRegistryIds.isEmpty()) {
            onSelect.accept(selectedRegistryIds.iterator().next());
        } else if (!selectedInventorySlots.isEmpty()) {
            if (player != null) {
                int slot = selectedInventorySlots.iterator().next();
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null) {
                        emitInventoryItem(key.toString(), stack, nbtSelectedInventorySlots.contains(slot));
                    }
                }
            }
        }
        hide();
        return true;
    }

    private void emitInventoryItem(String id, ItemStack stack, boolean withNbt) {
        if (withNbt && onSelectWithNbt != null) {
            JsonObject nbt = buildNbtCriteriaFromStack(stack);
            onSelectWithNbt.accept(id, nbt.size() > 0 ? nbt : null);
        } else {
            onSelect.accept(id);
        }
    }

    /**
     * Builds an NBT-criteria JsonObject from an ItemStack's CompoundTag.
     * 1.20 items carry all custom data as a single CompoundTag accessible via
     * {@link ItemStack#getTag()}; the editor's criteria format mirrors that
     * structure (top-level keys like {@code display}, {@code Enchantments},
     * {@code Unbreakable}, …), so we just convert the tag to JSON and copy its
     * top-level entries into the result.
     * <p>
     * A fresh default item (tag == null) yields an empty object, which means
     * the entry will match any instance of that item — same end behavior as
     * the neoforge "no patched components" case.
     */
    private static JsonObject buildNbtCriteriaFromStack(ItemStack stack) {
        JsonObject result = new JsonObject();
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return result;
        }
        JsonElement asJson = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
        if (asJson != null && asJson.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : asJson.getAsJsonObject().entrySet()) {
                result.add(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private boolean isAddButtonAt(double mouseX, double mouseY) {
        int addBtnX = panelX + (panelW - ADD_BTN_W) / 2;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;
        return mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN_W && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
    }

    private void toggleRegistrySelection(String id) {
        if (selectedRegistryIds.contains(id)) {
            selectedRegistryIds.remove(id);
        } else {
            if (!multiSelect) {
                selectedRegistryIds.clear();
                selectedInventorySlots.clear();
            }
            selectedRegistryIds.add(id);
        }
    }

    private void toggleInventorySelection(int slot, boolean withNbt) {
        if (selectedInventorySlots.contains(slot)) {
            selectedInventorySlots.remove(slot);
            nbtSelectedInventorySlots.remove(slot);
        } else {
            if (!multiSelect) {
                selectedRegistryIds.clear();
                selectedInventorySlots.clear();
                nbtSelectedInventorySlots.clear();
            }
            selectedInventorySlots.add(slot);
            if (withNbt && onSelectWithNbt != null) {
                nbtSelectedInventorySlots.add(slot);
            }
        }
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return true;

        if (isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
            return true;
        }

        int clickedSlot = getInventorySlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(clickedSlot);
            if (!stack.isEmpty() && isItemAllowedByModFilter(stack)) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                toggleInventorySelection(clickedSlot, Screen.hasControlDown());
            }
            return true;
        }

        return true;
    }

    private boolean handleRegistryClick(double mouseX, double mouseY) {
        if (isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
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

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridX = getGridStartX(true);
        int gridY = gridTopY();

        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + gridRows() * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    toggleRegistrySelection(filteredItems.get(index).id);
                    return true;
                }
            }
        }

        searchBar.setFocused(true);
        return true;
    }

    private boolean handleSelectedClick(double mouseX, double mouseY) {
        if (isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
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

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridX = getGridStartX(true);
        int gridY = gridTopY();

        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + gridRows() * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                if (index < selectedView.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    toggleSnapshotEntry(selectedView.get(index));
                    return true;
                }
            }
        }
        searchBar.setFocused(true);
        return true;
    }

    /** How many currently-found items "Select all" would add. 0 when idle (no filter). */
    private int selectableFoundCount() {
        if (!selectAllContextActive()) return 0;
        int n = 0;
        if (isSelectedTab()) {
            for (SelectedRef ref : selectedView) if (!isStillSelected(ref)) n++;
        } else {
            for (ItemEntry entry : filteredItems) if (!selectedRegistryIds.contains(entry.id)) n++;
        }
        return n;
    }

    /**
     * How many currently-found items "Deselect all" would remove. Unlike selecting, this is
     * available without a filter too — it only ever removes things already selected, so clearing
     * the whole selection with no query is safe.
     */
    private int deselectableFoundCount() {
        int n = 0;
        if (isSelectedTab()) {
            for (SelectedRef ref : selectedView) if (isStillSelected(ref)) n++;
        } else {
            for (ItemEntry entry : filteredItems) if (selectedRegistryIds.contains(entry.id)) n++;
        }
        return n;
    }

    private void selectAllFound() {
        if (isSelectedTab()) {
            for (SelectedRef ref : selectedView) {
                if (ref.fromInventory) selectedInventorySlots.add(ref.inventorySlot);
                else selectedRegistryIds.add(ref.entry.id);
            }
        } else {
            for (ItemEntry entry : filteredItems) selectedRegistryIds.add(entry.id);
        }
        refreshSelectedPlaceholder();
    }

    private void deselectAllFound() {
        if (isSelectedTab()) {
            for (SelectedRef ref : selectedView) {
                if (ref.fromInventory) {
                    selectedInventorySlots.remove(ref.inventorySlot);
                    nbtSelectedInventorySlots.remove(ref.inventorySlot);
                } else {
                    selectedRegistryIds.remove(ref.entry.id);
                }
            }
        } else {
            for (ItemEntry entry : filteredItems) selectedRegistryIds.remove(entry.id);
        }
        refreshSelectedPlaceholder();
    }

    private void refreshSelectedPlaceholder() {
        if (isSelectedTab()) {
            searchBar.setPlaceholder(Component.translatable(
                    "editor.historystages.search.selected.placeholder", totalSelectionCount()).getString());
        }
    }

    /**
     * Toggles whether the snapshot entry is in the active selection sets, but
     * leaves it in the snapshot/view so the grid doesn't shift under the
     * cursor. Snapshot is purged on tab exit.
     */
    private void toggleSnapshotEntry(SelectedRef ref) {
        if (ref.fromInventory) {
            if (selectedInventorySlots.contains(ref.inventorySlot)) {
                selectedInventorySlots.remove(ref.inventorySlot);
                nbtSelectedInventorySlots.remove(ref.inventorySlot);
            } else {
                selectedInventorySlots.add(ref.inventorySlot);
                if (Screen.hasControlDown() && onSelectWithNbt != null) {
                    nbtSelectedInventorySlots.add(ref.inventorySlot);
                }
            }
        } else {
            if (selectedRegistryIds.contains(ref.entry.id)) {
                selectedRegistryIds.remove(ref.entry.id);
            } else {
                selectedRegistryIds.add(ref.entry.id);
            }
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar || isInventoryTab())
            return false;
        int gridY = gridTopY();
        updateScrollFromMouse(mouseY, gridY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int gridY) {
        int gridH = gridRows() * SLOT_SIZE;
        int totalRows = maxScrollRow + gridRows();
        int thumbHeight = Math.max(10, (int) ((float) gridRows() / totalRows * gridH));
        float usableH = gridH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - gridY - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || isInventoryTab())
            return false;

        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible)
            return false;

        if (keyCode == 256) { // ESC
            if (searchBar.keyPressed(keyCode))
                return true;
            hide();
            return true;
        }

        if (keyCode == 257 && canConfirm()) { // Enter
            confirmAndAdd();
            return true;
        }

        if (isInventoryTab())
            return true;

        return searchBar.keyPressed(keyCode);
    }

    public boolean charTyped(char c) {
        if (!visible || isInventoryTab())
            return false;
        return searchBar.charTyped(c);
    }

    private record ItemEntry(String id, ItemStack stack, String searchName) {
    }

    private record SelectedRef(ItemEntry entry, boolean fromInventory, int inventorySlot) {
    }
}
