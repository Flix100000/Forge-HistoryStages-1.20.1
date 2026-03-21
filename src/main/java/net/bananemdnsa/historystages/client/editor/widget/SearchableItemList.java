package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creative menu-style item grid with search bar.
 * Rendered as an overlay panel within the parent screen.
 */
public class SearchableItemList {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private final Consumer<String> onSelect;

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;

    public SearchableItemList(Consumer<String> onSelect) {
        this.onSelect = onSelect;

        // Pre-populate all registered items
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                allItems.add(new ItemEntry(key.toString(), new ItemStack(item)));
            }
        }
        filteredItems.addAll(allItems);
    }

    public void show(int centerX, int centerY, int parentWidth) {
        panelW = GRID_COLS * SLOT_SIZE + PADDING * 2 + 8;
        panelH = SEARCH_HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;

        // Clamp to screen
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        this.filter = "";
        this.searchFocused = true;
        filteredItems.clear();
        filteredItems.addAll(allItems);
        updateMaxScroll();
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
                if (entry.id.contains(this.filter) || entry.stack.getHoverName().getString().toLowerCase().contains(this.filter)) {
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

        // Search bar background
        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
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
                    int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
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

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        // Check if click is inside panel
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Check scrollbar click
        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
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
        int searchY = panelY + PADDING;
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
        if (!visible || !draggingScrollbar) return false;
        int searchY = panelY + PADDING;
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
        if (!visible) return false;

        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible || !searchFocused) return false;

        if (keyCode == 256) { // ESC
            hide();
            return true;
        }
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
        if (!visible || !searchFocused) return false;

        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' ' || c == '-' || c == '@') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }

    private record ItemEntry(String id, ItemStack stack) {}
}
