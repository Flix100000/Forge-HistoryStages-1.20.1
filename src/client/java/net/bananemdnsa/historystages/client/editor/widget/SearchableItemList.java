package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.bananemdnsa.historystages.client.LockOverlayRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SearchableItemList {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int PADDING = 6;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;
    private static final int SCROLLBAR_GAP = 6;
    private static final int ADD_BTN_W = 100;
    private static final int ADD_BTN_H = 20;

    private static final int TAB_REGISTRY = 0;
    private static final int TAB_INVENTORY = 1;
    private static final int TAB_SELECTED = 2;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private final List<SelectedRef> selectedSnapshot = new ArrayList<>();
    private final List<SelectedRef> selectedView = new ArrayList<>();
    private final Consumer<String> onSelect;
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

    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    private float addHoverProgress = 0.0f;

    private Set<String> modFilterSet = null;

    public SearchableItemList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableItemList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        this.onSelect = onSelect;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.searchBar = new SearchBar("Search items...").onChange(this::applyFilter);
        if (alreadyAddedSupplier != null) {
            searchBar.filters().addOption("hide_added", "Hide already added", null);
        }
        searchBar.filters().addOption("only_vanilla", "Only vanilla", "source");
        searchBar.filters().addOption("only_modded", "Only modded", "source");

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

    public void show(int centerX, int centerY, int parentWidth) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        this.currentTab = TAB_REGISTRY;
        this.selectedRegistryIds.clear();
        this.selectedInventorySlots.clear();
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

    private int totalSelectionCount() {
        return selectedRegistryIds.size() + selectedInventorySlots.size();
    }

    private boolean showSelectedTab() {
        return multiSelect && (totalSelectionCount() > 0 || !selectedSnapshot.isEmpty());
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
        if (isInventoryTab()) {
            int gridW = SLOT_SIZE * 9;
            panelW = PADDING + gridW + PADDING + 8;
            int topAreaH = 4 * SLOT_SIZE + 4;
            panelH = PADDING + TAB_HEIGHT + 4
                    + topAreaH + 4
                    + 3 * SLOT_SIZE + 6
                    + SLOT_SIZE + 6
                    + ADD_BTN_H + PADDING;
        } else {
            panelW = GRID_COLS * SLOT_SIZE + PADDING * 2 + 8;
            panelH = TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4
                    + ADD_BTN_H + PADDING;
        }

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
        searchBar.filters().close();
        searchBar.setFocused(false);
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
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            Collection<String> added = alreadyAddedSupplier.get();
            if (added != null && added.contains(id))
                return false;
        }
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
        boolean isVanilla = "minecraft".equals(namespace);
        if (searchBar.filters().isActive("only_vanilla") && !isVanilla)
            return false;
        if (searchBar.filters().isActive("only_modded") && isVanilla)
            return false;
        return true;
    }

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
        maxScrollRow = Math.max(0, totalRows - GRID_ROWS);
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

        LockOverlayRenderer.pushSuppressed();
        try {
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        if (isInventoryTab()) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else if (isSelectedTab()) {
            renderSelectedMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
        } finally {
            LockOverlayRenderer.popSuppressed();
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
            tabIndicatorX = tabXs[activeIdx];
            tabIndicatorW = tabWs[activeIdx];
            tabIndicatorInit = true;
        }

        float targetX = tabXs[activeIdx];
        float targetW = tabWs[activeIdx];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f)
            tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f)
            tabIndicatorW = targetW;

        for (int i = 0; i < n; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);

            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        guiGraphics.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2,
                (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);

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

        int gridX = getGridStartX(true);
        int gridY = searchY + SearchBar.HEIGHT + PADDING;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
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
            for (int row = 0; row < GRID_ROWS; row++) {
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
        int scrollBarBottom = gridY + GRID_ROWS * SLOT_SIZE;
        int scrollBarHeight = scrollBarBottom - scrollBarTop;
        guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
        int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / (maxScrollRow + GRID_ROWS) * scrollBarHeight));
        int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
        guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderAddButton(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int addBtnX = panelX + (panelW - ADD_BTN_W) / 2;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;

        boolean canAdd = canConfirm();
        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN_W
                && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, ADD_BTN_W, ADD_BTN_H, addButtonLabel(),
                    addHoverProgress);
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
                entityAreaX, topY, entityAreaX + entityAreaW, topY + entityAreaH,
                25,
                0.0f,
                (float) mouseX,
                (float) mouseY,
                player);

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

        int gridX = getGridStartX(true);
        int gridY = searchY + SearchBar.HEIGHT + PADDING;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                if (index < selectedView.size()) {
                    boolean slotHovered = !filterUiHovered && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                            && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                    SelectedRef ref = selectedView.get(index);
                    boolean active = isStillSelected(ref);

                    int borderColor = active
                            ? (slotHovered ? 0xFFFF8800 : 0xFFFFCC00)
                            : (slotHovered ? 0xFF884444 : 0xFF552020);
                    int bgColor = active
                            ? (slotHovered ? 0xFF553A10 : 0xFF2A2510)
                            : (slotHovered ? 0xFF3A1A1A : 0xFF1A0D0D);
                    guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor);
                    guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, bgColor);

                    guiGraphics.renderItem(ref.entry.stack, slotX + 1, slotY + 1);
                    if (active) {
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

        if (maxScrollRow > 0) {
            renderScrollbar(guiGraphics, gridX, gridY);
        }

        renderAddButton(guiGraphics, font, mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (!filterUiHovered) {
            for (int row = 0; row < GRID_ROWS; row++) {
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
        boolean isHovered = !isEmpty && isAllowed && mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y
                && mouseY < y + SLOT_SIZE;

        int borderColor = isSelected ? 0xFFFFCC00 : 0xFF252525;
        int bgColor = isSelected ? 0xFF2A2510 : (isHovered ? 0xFF353535 : 0xFF1A1A1A);

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
            if (isSelected) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x40FFCC00);
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

        if (multiSelect) {
            List<String> ids = new ArrayList<>();
            ids.addAll(selectedRegistryIds);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                for (Integer slot : selectedInventorySlots) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (key != null && !ids.contains(key.toString())) {
                            ids.add(key.toString());
                        }
                    }
                }
            }
            for (String id : ids) {
                onSelect.accept(id);
            }
        } else if (!selectedRegistryIds.isEmpty()) {
            onSelect.accept(selectedRegistryIds.iterator().next());
        } else if (!selectedInventorySlots.isEmpty()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                int slot = selectedInventorySlots.iterator().next();
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null) {
                        onSelect.accept(key.toString());
                    }
                }
            }
        }
        hide();
        return true;
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

    private void toggleInventorySelection(int slot) {
        if (selectedInventorySlots.contains(slot)) {
            selectedInventorySlots.remove(slot);
        } else {
            if (!multiSelect) {
                selectedRegistryIds.clear();
                selectedInventorySlots.clear();
            }
            selectedInventorySlots.add(slot);
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
                toggleInventorySelection(clickedSlot);
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

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridX = getGridStartX(true);
        int gridY = searchY + SearchBar.HEIGHT + PADDING;

        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
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

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridX = getGridStartX(true);
        int gridY = searchY + SearchBar.HEIGHT + PADDING;

        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
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

    private void toggleSnapshotEntry(SelectedRef ref) {
        if (ref.fromInventory) {
            if (selectedInventorySlots.contains(ref.inventorySlot)) {
                selectedInventorySlots.remove(ref.inventorySlot);
            } else {
                selectedInventorySlots.add(ref.inventorySlot);
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
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridY = searchY + SearchBar.HEIGHT + PADDING;
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
        int gridH = GRID_ROWS * SLOT_SIZE;
        int totalRows = maxScrollRow + GRID_ROWS;
        int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / totalRows * gridH));
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

        if (keyCode == 256) {
            if (searchBar.keyPressed(keyCode))
                return true;
            hide();
            return true;
        }

        if (keyCode == 257 && canConfirm()) {
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
