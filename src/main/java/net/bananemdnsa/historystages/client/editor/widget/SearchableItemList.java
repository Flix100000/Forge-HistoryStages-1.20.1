package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creative menu-style item grid with search bar.
 * Rendered as an overlay panel within the parent screen.
 * Supports toggling to an inventory view that mirrors the vanilla inventory layout.
 */
public class SearchableItemList {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int TOGGLE_HEIGHT = 16;
    private static final int TOGGLE_GAP = 4;

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

    // Smooth hover animation state (matching StyledButton gold theme)
    private float regHoverProgress = 0.0f;
    private float invHoverProgress = 0.0f;
    private float addHoverProgress = 0.0f;

    public SearchableItemList(Consumer<String> onSelect) {
        this.onSelect = onSelect;

        // Pre-populate all registered items with cached search names
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
        this.filter = "";
        this.searchFocused = true;
        this.inventoryMode = false;
        this.selectedInventorySlot = -1;
        filteredItems.clear();
        filteredItems.addAll(allItems);
        updateMaxScroll();
        recalcPanelSize();
    }

    private void recalcPanelSize() {
        if (inventoryMode) {
            // Inventory mode: larger panel to fit vanilla inventory layout
            // 9 cols for main grid + armor column on the left + offhand
            int invContentW = SLOT_SIZE * 9;
            int armorW = SLOT_SIZE + 6;
            panelW = PADDING + armorW + invContentW + PADDING + 4;
            // Toggle row + gap + armor row (4 slots) + gap + main inventory (3 rows) + gap + hotbar (1 row) + gap + add button
            int addButtonH = 20;
            panelH = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP
                    + Math.max(4 * SLOT_SIZE, 0) + TOGGLE_GAP
                    + 3 * SLOT_SIZE + TOGGLE_GAP
                    + SLOT_SIZE + TOGGLE_GAP
                    + addButtonH + PADDING;
        } else {
            // Registry mode: original size
            panelW = GRID_COLS * SLOT_SIZE + PADDING * 2 + 8;
            panelH = TOGGLE_HEIGHT + TOGGLE_GAP + SEARCH_HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4;
        }
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;

        // Clamp to screen
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (panelX + panelW > screenW - 4) panelX = screenW - panelW - 4;
        if (panelY + panelH > screenH - 4) panelY = screenH - panelH - 4;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setFilter(String filter) {
        this.filter = filter.toLowerCase();
        this.scrollRow = 0;
        filteredItems.clear();
        if (this.filter.isEmpty()) {
            filteredItems.addAll(allItems);
        } else if (this.filter.startsWith("@")) {
            // @mod filter: show only items from the specified mod
            String modFilter = this.filter.substring(1);
            for (ItemEntry entry : allItems) {
                String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
                if (modId.contains(modFilter)) {
                    filteredItems.add(entry);
                }
            }
        } else {
            for (ItemEntry entry : allItems) {
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

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        // Panel background (fully opaque)
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        // Toggle buttons
        renderToggleButtons(guiGraphics, font, mouseX, mouseY);

        if (inventoryMode) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderStyledButton(GuiGraphics guiGraphics, Font font, int x, int y, int w, int h,
                                     String text, boolean active, float hoverProgress) {
        // Background - lerp from white-tint to gold-tint (matching StyledButton)
        if (active) {
            // Active tab: gold accent look
            int bgAlpha = 0x50;
            guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | 0xFFCC00);
        } else {
            int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
            int bgR = 0xFF;
            int bgG = (int) (0xFF - hoverProgress * 0x33);
            int bgB = (int) (0xFF - hoverProgress * 0xFF);
            guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);
        }

        // Bottom accent line
        int accentAlpha = active ? 0xFF : (int) (0x60 + hoverProgress * 0x9F);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        // Subtle top/side borders
        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        // Text - smooth color transition
        int textGray = active ? 0xFF : (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private void renderToggleButtons(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int toggleY = panelY + PADDING;
        int toggleW = (panelW - PADDING * 2 - 4) / 2;
        int registryX = panelX + PADDING;
        int inventoryX = registryX + toggleW + 4;

        // Update hover animations
        boolean regHovered = !inventoryMode ? false : mouseX >= registryX && mouseX < registryX + toggleW
                && mouseY >= toggleY && mouseY < toggleY + TOGGLE_HEIGHT;
        boolean invHovered = inventoryMode ? false : mouseX >= inventoryX && mouseX < inventoryX + toggleW
                && mouseY >= toggleY && mouseY < toggleY + TOGGLE_HEIGHT;

        regHoverProgress = regHovered ? Math.min(1.0f, regHoverProgress + 0.1f) : Math.max(0.0f, regHoverProgress - 0.08f);
        invHoverProgress = invHovered ? Math.min(1.0f, invHoverProgress + 0.1f) : Math.max(0.0f, invHoverProgress - 0.08f);

        renderStyledButton(guiGraphics, font, registryX, toggleY, toggleW, TOGGLE_HEIGHT,
                "Registry", !inventoryMode, regHoverProgress);
        renderStyledButton(guiGraphics, font, inventoryX, toggleY, toggleW, TOGGLE_HEIGHT,
                "Inventory", inventoryMode, invHoverProgress);
    }

    private void renderRegistryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP;

        // Search bar background
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        // Search text (render above items z-level)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String displayFilter = filter.isEmpty() ? "\u00A77" + "Search items..." : filter;

        // Selection highlight
        if (allSelected && !filter.isEmpty()) {
            int textW = font.width(filter);
            guiGraphics.fill(searchX + 3, searchY + 3, searchX + 5 + textW, searchY + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }

        guiGraphics.drawString(font, displayFilter, searchX + 4, searchY + 6, filter.isEmpty() ? 0x666666 : 0xFFFFFF, false);

        // Cursor blink
        if (searchFocused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = searchX + 4 + (filter.isEmpty() ? 0 : font.width(filter));
            guiGraphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + SEARCH_HEIGHT - 4, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();

        // Grid area
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;

        // Draw grid slots
        int startIndex = scrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                // Slot background
                boolean slotHovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                        slotHovered ? 0xFF4A4A4A : 0xFF252525);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        slotHovered ? 0xFF353535 : 0xFF1A1A1A);

                if (index < filteredItems.size()) {
                    ItemEntry entry = filteredItems.get(index);
                    guiGraphics.renderItem(entry.stack, slotX + 1, slotY + 1);
                }
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            int scrollBarTop = gridY;
            int scrollBarBottom = gridY + GRID_ROWS * SLOT_SIZE;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;

            // Track
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);

            // Thumb
            int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / (maxScrollRow + GRID_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // Tooltip for hovered item (render above items z-level)
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
                    String name = entry.stack.getHoverName().getString();
                    String tooltipText = name + " \u00A77(" + entry.id + ")";

                    // Tooltip background
                    int tooltipW = font.width(tooltipText) + 8;
                    int tooltipH = 16;
                    int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                    int tooltipX = mouseX + 12;
                    int tooltipY = mouseY - 12;
                    if (tooltipX + tooltipW + 2 > screenW - 4) tooltipX = mouseX - tooltipW - 4;
                    if (tooltipY + tooltipH + 2 > screenH - 4) tooltipY = screenH - tooltipH - 6;
                    if (tooltipX < 4) tooltipX = 4;
                    if (tooltipY < 4) tooltipY = 4;
                    guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH, 0xFF1A1A1A);
                    guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1, 0xFF0D0D1A);
                    guiGraphics.drawString(font, tooltipText, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderInventoryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP;
        int armorW = SLOT_SIZE + 6;
        int armorX = panelX + PADDING;
        int mainGridX = armorX + armorW;
        int currentY = panelY + topOffset;

        // --- Armor slots (4 slots vertically on the left) + Crafting area label ---
        String armorLabel = "\u00A77Armor";
        guiGraphics.drawString(font, armorLabel, armorX, currentY, 0x888888, false);
        String mainLabel = "\u00A77Main Inventory";
        guiGraphics.drawString(font, mainLabel, mainGridX + 2, currentY, 0x888888, false);
        currentY += 10;

        // Armor slots: Head(39), Chest(38), Legs(37), Feet(36)
        int[] armorSlots = {39, 38, 37, 36};
        String[] armorNames = {"Head", "Chest", "Legs", "Feet"};
        int armorStartY = currentY;
        for (int i = 0; i < 4; i++) {
            int slotY = armorStartY + i * SLOT_SIZE;
            ItemStack stack = player.getInventory().getItem(armorSlots[i]);
            renderInventorySlot(guiGraphics, font, armorX, slotY, stack, armorSlots[i], mouseX, mouseY, armorNames[i]);
        }

        // Offhand slot below armor
        int offhandY = armorStartY + 4 * SLOT_SIZE + 4;
        String offhandLabel = "\u00A77Off";
        guiGraphics.drawString(font, offhandLabel, armorX + (SLOT_SIZE - font.width(offhandLabel)) / 2, offhandY - 9, 0x666666, false);
        ItemStack offhandStack = player.getInventory().getItem(40);
        renderInventorySlot(guiGraphics, font, armorX, offhandY, offhandStack, 40, mouseX, mouseY, "Offhand");

        // --- Main inventory (3 rows x 9 cols, slots 9-35) ---
        int mainStartY = currentY;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int slotX = mainGridX + col * SLOT_SIZE;
                int slotY = mainStartY + row * SLOT_SIZE;
                ItemStack stack = player.getInventory().getItem(slotIndex);
                renderInventorySlot(guiGraphics, font, slotX, slotY, stack, slotIndex, mouseX, mouseY, null);
            }
        }

        // --- Hotbar (1 row x 9 cols, slots 0-8) ---
        int hotbarY = mainStartY + 3 * SLOT_SIZE + TOGGLE_GAP + 2;
        String hotbarLabel = "\u00A77Hotbar";
        guiGraphics.drawString(font, hotbarLabel, mainGridX + 2, hotbarY - 9, 0x666666, false);
        hotbarY += 2;
        for (int col = 0; col < 9; col++) {
            int slotX = mainGridX + col * SLOT_SIZE;
            ItemStack stack = player.getInventory().getItem(col);
            renderInventorySlot(guiGraphics, font, slotX, hotbarY, stack, col, mouseX, mouseY, null);
        }

        // --- Add button (styled like StyledButton) ---
        int addBtnW = 80;
        int addBtnH = 20;
        int addBtnX = panelX + (panelW - addBtnW) / 2;
        int addBtnY = panelY + panelH - PADDING - addBtnH;

        boolean hasSelection = selectedInventorySlot >= 0;
        ItemStack selectedStack = hasSelection ? player.getInventory().getItem(selectedInventorySlot) : ItemStack.EMPTY;
        boolean canAdd = hasSelection && !selectedStack.isEmpty();

        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + addBtnW
                && mouseY >= addBtnY && mouseY < addBtnY + addBtnH;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f) : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, addBtnW, addBtnH,
                    "Add Item", false, addHoverProgress);
        } else {
            // Disabled state: dim background, no accent
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 1, 0x10FFFFFF);
            String addText = "Select an Item";
            guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2, addBtnY + (addBtnH - 8) / 2, 0xFF666666, false);
        }

        // --- Tooltip for hovered inventory slot ---
        renderInventoryTooltip(guiGraphics, font, mouseX, mouseY, player);
    }

    private void renderInventorySlot(GuiGraphics guiGraphics, Font font, int x, int y, ItemStack stack, int slotIndex, int mouseX, int mouseY, String label) {
        boolean isEmpty = stack.isEmpty();
        boolean isSelected = selectedInventorySlot == slotIndex;
        boolean isHovered = !isEmpty && mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;

        // Slot border and background
        int borderColor = isSelected ? 0xFF4A9A4A : 0xFF252525;
        int bgColor = isSelected ? 0xFF2A4A2A : (isHovered ? 0xFF353535 : 0xFF1A1A1A);

        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, bgColor);

        if (!isEmpty) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            // Render stack count
            if (stack.getCount() > 1) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);
                String count = String.valueOf(stack.getCount());
                guiGraphics.drawString(font, count, x + SLOT_SIZE - 1 - font.width(count), y + SLOT_SIZE - 9, 0xFFFFFF, true);
                guiGraphics.pose().popPose();
            }
        } else if (label != null) {
            // Draw placeholder label for empty armor/offhand slots
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            String shortLabel = label.length() > 1 ? label.substring(0, 1) : label;
            guiGraphics.drawString(font, shortLabel, x + (SLOT_SIZE - font.width(shortLabel)) / 2, y + 5, 0xFF444444, false);
            guiGraphics.pose().popPose();
        }

        // Green highlight overlay for selected slot
        if (isSelected && !isEmpty) {
            guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x4040FF40);
        }
    }

    private void renderInventoryTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, LocalPlayer player) {
        // Collect all slot rects and check hover
        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP + 10;
        int armorW = SLOT_SIZE + 6;
        int armorX = panelX + PADDING;
        int mainGridX = armorX + armorW;
        int armorStartY = panelY + topOffset;

        int hoveredSlot = getInventorySlotAt(mouseX, mouseY);
        if (hoveredSlot < 0) return;

        ItemStack stack = player.getInventory().getItem(hoveredSlot);
        if (stack.isEmpty()) return;

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return;

        String name = stack.getHoverName().getString();
        String tooltipText = name + " \u00A77(" + key + ")";

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);

        int tooltipW = font.width(tooltipText) + 8;
        int tooltipH = 16;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        if (tooltipX + tooltipW + 2 > screenW - 4) tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > screenH - 4) tooltipY = screenH - tooltipH - 6;
        if (tooltipX < 4) tooltipX = 4;
        if (tooltipY < 4) tooltipY = 4;

        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH, 0xFF1A1A1A);
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1, 0xFF0D0D1A);
        guiGraphics.drawString(font, tooltipText, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);

        guiGraphics.pose().popPose();
    }

    private int getInventorySlotAt(double mouseX, double mouseY) {
        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP + 10;
        int armorW = SLOT_SIZE + 6;
        int armorX = panelX + PADDING;
        int mainGridX = armorX + armorW;
        int armorStartY = panelY + topOffset;

        // Check armor slots (39, 38, 37, 36)
        int[] armorSlots = {39, 38, 37, 36};
        for (int i = 0; i < 4; i++) {
            int slotY = armorStartY + i * SLOT_SIZE;
            if (mouseX >= armorX && mouseX < armorX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return armorSlots[i];
            }
        }

        // Check offhand slot (40)
        int offhandY = armorStartY + 4 * SLOT_SIZE + 4;
        if (mouseX >= armorX && mouseX < armorX + SLOT_SIZE && mouseY >= offhandY && mouseY < offhandY + SLOT_SIZE) {
            return 40;
        }

        // Check main inventory (slots 9-35)
        int mainStartY = armorStartY;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int slotX = mainGridX + col * SLOT_SIZE;
                int slotY = mainStartY + row * SLOT_SIZE;
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return slotIndex;
                }
            }
        }

        // Check hotbar (slots 0-8)
        int hotbarY = mainStartY + 3 * SLOT_SIZE + TOGGLE_GAP + 2 + 2;
        for (int col = 0; col < 9; col++) {
            int slotX = mainGridX + col * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= hotbarY && mouseY < hotbarY + SLOT_SIZE) {
                return col;
            }
        }

        return -1;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        // Check if click is inside panel
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Check toggle button clicks
        int toggleY = panelY + PADDING;
        int toggleW = (panelW - PADDING * 2 - 4) / 2;
        int registryX = panelX + PADDING;
        int inventoryX = registryX + toggleW + 4;

        if (mouseY >= toggleY && mouseY < toggleY + TOGGLE_HEIGHT) {
            if (mouseX >= registryX && mouseX < registryX + toggleW && inventoryMode) {
                inventoryMode = false;
                selectedInventorySlot = -1;
                searchFocused = true;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                recalcPanelSize();
                return true;
            }
            if (mouseX >= inventoryX && mouseX < inventoryX + toggleW && !inventoryMode) {
                inventoryMode = true;
                selectedInventorySlot = -1;
                searchFocused = false;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                recalcPanelSize();
                return true;
            }
        }

        if (inventoryMode) {
            return handleInventoryClick(mouseX, mouseY);
        } else {
            return handleRegistryClick(mouseX, mouseY);
        }
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return true;

        // Check "Add" button click
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
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        onSelect.accept(key.toString());
                        hide();
                        return true;
                    }
                }
            }
            return true;
        }

        // Check inventory slot clicks
        int clickedSlot = getInventorySlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(clickedSlot);
            if (!stack.isEmpty()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectedInventorySlot = (selectedInventorySlot == clickedSlot) ? -1 : clickedSlot;
            }
            return true;
        }

        return true;
    }

    private boolean handleRegistryClick(double mouseX, double mouseY) {
        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP;

        // Check scrollbar click
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

        // Check grid clicks
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
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    onSelect.accept(filteredItems.get(index).id);
                    hide();
                    return true;
                }
            }
        }

        // Click on search bar area — keep focus
        searchFocused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar || inventoryMode) return false;
        int topOffset = PADDING + TOGGLE_HEIGHT + TOGGLE_GAP;
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
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - gridY) / (double) gridH));
        scrollRow = Math.round(ratio * maxScrollRow);
        scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || inventoryMode) return false;

        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        if (keyCode == 256) { // ESC
            hide();
            return true;
        }

        if (inventoryMode) {
            // Enter key confirms selection in inventory mode
            if (keyCode == 257 && selectedInventorySlot >= 0) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
                    if (!stack.isEmpty()) {
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (key != null) {
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            onSelect.accept(key.toString());
                            hide();
                            return true;
                        }
                    }
                }
            }
            return true;
        }

        if (!searchFocused) return false;

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
            if (!filter.isEmpty()) allSelected = true;
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
        if (!visible || !searchFocused || inventoryMode) return false;

        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' ' || c == '-' || c == '@') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }

    private record ItemEntry(String id, ItemStack stack, String searchName) {}
}
