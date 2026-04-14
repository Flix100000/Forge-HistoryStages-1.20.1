package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Creative menu-style item grid with search bar.
 * Rendered as an overlay panel within the parent screen.
 * Supports toggling to an inventory view that mirrors the vanilla inventory
 * layout.
 */
public class SearchableItemList {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private final Consumer<String> onSelect;

    private int panelX, panelY, panelW, panelH;
    private int centerX, centerY;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;

    private boolean inventoryMode = false;
    private int selectedInventorySlot = -1;

    // Tab indicator animation (matching StageDetailScreen category tabs)
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    // Add button hover animation
    private float addHoverProgress = 0.0f;

    // Mod filter: if set, only items from these mods are shown
    private Set<String> modFilterSet = null;

    public SearchableItemList(Consumer<String> onSelect) {
        this.onSelect = onSelect;

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

    public void show(int centerX, int centerY, int parentWidth) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.visible = true;
        this.scrollRow = 0;
        this.searchFocused = true;
        this.inventoryMode = false;
        this.selectedInventorySlot = -1;
        this.tabIndicatorInit = false;
        setFilter("");
        recalcPanelSize();
    }

    private void recalcPanelSize() {
        if (inventoryMode) {
            // Vanilla inventory layout:
            // Top area: armor (1 col) + player entity + offhand (1 col) — spanning 9
            // slot-widths
            // Below: 3x9 main inventory
            // Below: 1x9 hotbar
            // Below: add button
            int gridW = SLOT_SIZE * 9;
            panelW = PADDING + gridW + PADDING + 8;
            int topAreaH = 4 * SLOT_SIZE + 4; // armor is 4 tall, player entity fits in same height
            int addButtonH = 20;
            panelH = PADDING + TAB_HEIGHT + 4
                    + topAreaH + 4
                    + 3 * SLOT_SIZE + 6
                    + SLOT_SIZE + 6
                    + addButtonH + PADDING;
        } else {
            panelW = GRID_COLS * SLOT_SIZE + PADDING * 2 + 8;
            panelH = TAB_HEIGHT + 4 + SEARCH_HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4;
        }

        // Always center
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

    /**
     * Sets a mod filter so only items from the given mod IDs are shown.
     * Pass null to clear the filter.
     */
    public void setModFilter(Set<String> modIds) {
        this.modFilterSet = modIds;
        setFilter(this.filter);
    }

    public void setFilter(String filter) {
        this.filter = filter.toLowerCase();
        this.scrollRow = 0;
        filteredItems.clear();

        // Base list: either all items or mod-filtered items
        List<ItemEntry> baseItems = allItems;
        if (modFilterSet != null && !modFilterSet.isEmpty()) {
            baseItems = new ArrayList<>();
            for (ItemEntry entry : allItems) {
                String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
                if (modFilterSet.contains(modId)) {
                    baseItems.add(entry);
                }
            }
        }

        if (this.filter.isEmpty()) {
            filteredItems.addAll(baseItems);
        } else if (this.filter.startsWith("@")) {
            String modFilter = this.filter.substring(1);
            for (ItemEntry entry : baseItems) {
                String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
                if (modId.contains(modFilter)) {
                    filteredItems.add(entry);
                }
            }
        } else {
            for (ItemEntry entry : baseItems) {
                if (entry.id.contains(this.filter) || entry.searchName.contains(this.filter)) {
                    filteredItems.add(entry);
                }
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int totalRows = (filteredItems.size() + GRID_COLS - 1) / GRID_COLS;
        maxScrollRow = Math.max(0, totalRows - GRID_ROWS);
    }

    // --- Rendering ---

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        // Panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        if (inventoryMode) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        String[] labels = { "Registry", "Inventory" };
        int[] tabXs = new int[2];
        int[] tabWs = new int[2];

        // Calculate tab positions (like StageDetailScreen categories)
        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            tabWs[i] = font.width(labels[i]) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        // Init indicator
        int activeIdx = inventoryMode ? 1 : 0;
        if (!tabIndicatorInit) {
            tabIndicatorX = tabXs[activeIdx];
            tabIndicatorW = tabWs[activeIdx];
            tabIndicatorInit = true;
        }

        // Animate indicator
        float targetX = tabXs[activeIdx];
        float targetW = tabWs[activeIdx];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f)
            tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f)
            tabIndicatorW = targetW;

        // Render tabs
        for (int i = 0; i < 2; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);

            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels[i], tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        // Sliding gold underline
        guiGraphics.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2,
                (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);

        // Separator line
        guiGraphics.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING, tabY + TAB_HEIGHT + 1,
                0xFF555555);
    }

    private void renderRegistryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;

        // Empty state: mod filter set but no mods locked
        if (modFilterSet != null && modFilterSet.isEmpty()) {
            String msg = Component.translatable("editor.historystages.no_mods_locked").getString();
            int msgY = panelY + topOffset + (panelH - topOffset) / 2 - 4;
            // Word-wrap the message if it's too wide
            int maxW = panelW - PADDING * 4;
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

            int totalH = lines.size() * 10;
            int startY = panelY + topOffset + (panelH - topOffset - totalH) / 2;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                int lw = font.width(l);
                guiGraphics.drawString(font, l, panelX + (panelW - lw) / 2, startY + i * 10, 0xFF888888, false);
            }
            return;
        }

        // Search bar
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String displayFilter = filter.isEmpty() ? "\u00A77" + "Search items..." : filter;

        if (allSelected && !filter.isEmpty()) {
            int textW = font.width(filter);
            guiGraphics.fill(searchX + 3, searchY + 3, searchX + 5 + textW, searchY + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }

        guiGraphics.drawString(font, displayFilter, searchX + 4, searchY + 6, filter.isEmpty() ? 0x666666 : 0xFFFFFF,
                false);

        if (searchFocused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = searchX + 4 + (filter.isEmpty() ? 0 : font.width(filter));
            guiGraphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + SEARCH_HEIGHT - 4, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();

        // Grid
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;

        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                boolean slotHovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                        slotHovered ? 0xFF4A4A4A : 0xFF252525);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        slotHovered ? 0xFF353535 : 0xFF1A1A1A);

                if (index < filteredItems.size()) {
                    guiGraphics.renderItem(filteredItems.get(index).stack, slotX + 1, slotY + 1);
                }
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            int scrollBarTop = gridY;
            int scrollBarBottom = gridY + GRID_ROWS * SLOT_SIZE;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / (maxScrollRow + GRID_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // Tooltip
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    ItemEntry entry = filteredItems.get(index);
                    renderTooltip(guiGraphics, font, mouseX, mouseY,
                            entry.stack.getHoverName().getString() + " \u00A77(" + entry.id + ")");
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    /**
     * Returns the layout coordinates for the inventory mode.
     * All slot positions are calculated relative to these anchors.
     */
    private int[] getInvLayout() {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int gridX = panelX + PADDING + 4; // left edge of 9-col grid
        int topY = panelY + topOffset + 2; // top of the upper area
        int topAreaH = 4 * SLOT_SIZE + 4;
        int mainY = topY + topAreaH + 4; // top of main 3x9 grid
        int hotbarY = mainY + 3 * SLOT_SIZE + 6; // top of hotbar
        return new int[] { gridX, topY, mainY, hotbarY };
    }

    private boolean isItemAllowedByModFilter(ItemStack stack) {
        if (modFilterSet == null)
            return true;
        if (stack.isEmpty())
            return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null)
            return false;
        return modFilterSet.contains(key.getNamespace());
    }

    private void renderInventoryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // Empty state: mod filter set but no mods locked
        if (modFilterSet != null && modFilterSet.isEmpty()) {
            int topOffset = PADDING + TAB_HEIGHT + 4;
            String msg = Component.translatable("editor.historystages.no_mods_locked").getString();
            int maxW = panelW - PADDING * 4;
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

            int totalH = lines.size() * 10;
            int startY = panelY + topOffset + (panelH - topOffset - totalH) / 2;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                int lw = font.width(l);
                guiGraphics.drawString(font, l, panelX + (panelW - lw) / 2, startY + i * 10, 0xFF888888, false);
            }
            return;
        }

        int[] layout = getInvLayout();
        int gridX = layout[0];
        int topY = layout[1];
        int mainY = layout[2];
        int hotbarY = layout[3];

        // --- Top area: Armor (left) | Player Entity (center) | Offhand (right) ---
        int armorX = gridX;
        int entityAreaX = gridX + SLOT_SIZE + 4;
        int entityAreaW = 9 * SLOT_SIZE - 2 * (SLOT_SIZE + 4);
        int entityAreaH = 4 * SLOT_SIZE;
        int offhandX = gridX + 9 * SLOT_SIZE - SLOT_SIZE;

        // Armor slots: vertical column on the left (Head, Chest, Legs, Feet)
        int[] armorSlots = { 39, 38, 37, 36 };
        String[] armorLabels = { "H", "C", "L", "F" };
        for (int i = 0; i < 4; i++) {
            renderInventorySlot(guiGraphics, font, armorX, topY + i * SLOT_SIZE,
                    player.getInventory().getItem(armorSlots[i]), armorSlots[i], mouseX, mouseY, armorLabels[i]);
        }

        // Player entity in the center
        guiGraphics.fill(entityAreaX, topY, entityAreaX + entityAreaW, topY + entityAreaH, 0xFF0D0D0D);
        int entityCenterX = entityAreaX + entityAreaW / 2;
        int entityBottomY = topY + entityAreaH - 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                entityCenterX, entityBottomY, 25,
                (float) (entityCenterX - mouseX), (float) (entityBottomY - 50 - mouseY), player);

        // Offhand slot: vertical column on the right, bottom-aligned
        renderInventorySlot(guiGraphics, font, offhandX, topY + 3 * SLOT_SIZE,
                player.getInventory().getItem(40), 40, mouseX, mouseY, "O");

        // --- Main inventory (3 rows x 9 cols, slots 9-35) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, mainY + row * SLOT_SIZE,
                        player.getInventory().getItem(slotIndex), slotIndex, mouseX, mouseY, null);
            }
        }

        // Separator line between main inv and hotbar
        guiGraphics.fill(gridX, hotbarY - 3, gridX + 9 * SLOT_SIZE, hotbarY - 2, 0xFF333333);

        // --- Hotbar (1 row x 9 cols, slots 0-8) ---
        for (int col = 0; col < 9; col++) {
            renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, hotbarY,
                    player.getInventory().getItem(col), col, mouseX, mouseY, null);
        }

        // --- Add button ---
        int addBtnW = 80;
        int addBtnH = 20;
        int addBtnX = panelX + (panelW - addBtnW) / 2;
        int addBtnY = panelY + panelH - PADDING - addBtnH;

        boolean hasSelection = selectedInventorySlot >= 0;
        ItemStack selectedStack = hasSelection ? player.getInventory().getItem(selectedInventorySlot) : ItemStack.EMPTY;
        boolean canAdd = hasSelection && !selectedStack.isEmpty();

        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + addBtnW
                && mouseY >= addBtnY && mouseY < addBtnY + addBtnH;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, addBtnW, addBtnH, "Add Item", addHoverProgress);
        } else {
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 1, 0x10FFFFFF);
            String addText = "Select an Item";
            guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2,
                    addBtnY + (addBtnH - 8) / 2, 0xFF666666, false);
        }

        // Tooltip
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        int hoveredSlot = getInventorySlotAt(mouseX, mouseY);
        if (hoveredSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(hoveredSlot);
            if (!stack.isEmpty()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key != null) {
                    renderTooltip(guiGraphics, font, mouseX, mouseY,
                            stack.getHoverName().getString() + " \u00A77(" + key + ")");
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderInventorySlot(GuiGraphics guiGraphics, Font font, int x, int y,
            ItemStack stack, int slotIndex, int mouseX, int mouseY, String placeholder) {
        boolean isEmpty = stack.isEmpty();
        boolean isAllowed = isEmpty || isItemAllowedByModFilter(stack);
        boolean isSelected = selectedInventorySlot == slotIndex;
        boolean isHovered = !isEmpty && isAllowed && mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y
                && mouseY < y + SLOT_SIZE;

        // Slot background — gold tint when selected (matching editor theme)
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
            // Gold highlight overlay for selected slot
            if (isSelected) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x40FFCC00);
            }
            // Dark overlay for items not in mod filter (not selectable)
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

        // Armor slots (vertical left column)
        int[] armorSlots = { 39, 38, 37, 36 };
        for (int i = 0; i < 4; i++) {
            int slotY = topY + i * SLOT_SIZE;
            if (mouseX >= armorX && mouseX < armorX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return armorSlots[i];
            }
        }

        // Offhand (bottom-right of top area)
        int offhandY = topY + 3 * SLOT_SIZE;
        if (mouseX >= offhandX && mouseX < offhandX + SLOT_SIZE && mouseY >= offhandY
                && mouseY < offhandY + SLOT_SIZE) {
            return 40;
        }

        // Main inventory (slots 9-35)
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

        // Hotbar (slots 0-8)
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
        String[] labels = { "Registry", "Inventory" };
        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            int w = font.width(labels[i]) + TAB_PAD * 2;
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

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Tab clicks
        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab == 0 && inventoryMode) {
            inventoryMode = false;
            selectedInventorySlot = -1;
            searchFocused = true;
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            recalcPanelSize();
            return true;
        }
        if (clickedTab == 1 && !inventoryMode) {
            inventoryMode = true;
            selectedInventorySlot = -1;
            searchFocused = false;
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            recalcPanelSize();
            return true;
        }

        if (inventoryMode) {
            return handleInventoryClick(mouseX, mouseY);
        } else {
            return handleRegistryClick(mouseX, mouseY);
        }
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return true;

        // Add button
        int addBtnW = 80;
        int addBtnH = 20;
        int addBtnX = panelX + (panelW - addBtnW) / 2;
        int addBtnY = panelY + panelH - PADDING - addBtnH;

        if (mouseX >= addBtnX && mouseX < addBtnX + addBtnW && mouseY >= addBtnY && mouseY < addBtnY + addBtnH) {
            if (selectedInventorySlot >= 0) {
                ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
                if (!stack.isEmpty()) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null) {
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        onSelect.accept(key.toString());
                        hide();
                        return true;
                    }
                }
            }
            return true;
        }

        // Slot clicks
        int clickedSlot = getInventorySlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(clickedSlot);
            if (!stack.isEmpty() && isItemAllowedByModFilter(stack)) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectedInventorySlot = (selectedInventorySlot == clickedSlot) ? -1 : clickedSlot;
            }
            return true;
        }

        return true;
    }

    private boolean handleRegistryClick(double mouseX, double mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;

        if (maxScrollRow > 0) {
            int searchY = panelY + topOffset;
            int gridX = panelX + PADDING + 4;
            int gridY = searchY + SEARCH_HEIGHT + PADDING;
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int searchY = panelY + topOffset;
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;

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
                    onSelect.accept(filteredItems.get(index).id);
                    hide();
                    return true;
                }
            }
        }

        searchFocused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar || inventoryMode)
            return false;
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;
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
        if (!visible || inventoryMode)
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
            hide();
            return true;
        }

        if (inventoryMode) {
            if (keyCode == 257 && selectedInventorySlot >= 0) { // Enter
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
                    if (!stack.isEmpty() && isItemAllowedByModFilter(stack)) {
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (key != null) {
                            Minecraft.getInstance().getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            onSelect.accept(key.toString());
                            hide();
                            return true;
                        }
                    }
                }
            }
            return true;
        }

        if (!searchFocused)
            return false;

        if (keyCode == 259) { // BACKSPACE
            if (allSelected) {
                allSelected = false;
                setFilter("");
            } else if (!filter.isEmpty()) {
                setFilter(filter.substring(0, filter.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 65) { // Ctrl+A
            if (!filter.isEmpty())
                allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) { // Ctrl+C
            if (!filter.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(filter);
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 86) { // Ctrl+V
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                setFilter(allSelected ? clipboard : filter + clipboard);
                allSelected = false;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible || !searchFocused || inventoryMode)
            return false;

        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' ' || c == '-' || c == '@') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }

    private record ItemEntry(String id, ItemStack stack, String searchName) {
    }
}
