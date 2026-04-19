package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Searchable overlay list of all known dimensions.
 */
public class SearchableDimensionList {
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 10;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int PANEL_WIDTH = 260;

    private final List<String> allDimensions = new ArrayList<>();
    private final List<String> filteredDimensions = new ArrayList<>();
    private final Consumer<String> onSelect;

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;

    public SearchableDimensionList(Consumer<String> onSelect) {
        this.onSelect = onSelect;

        // Collect all known dimensions from the connection
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            Set<ResourceKey<Level>> levels = mc.getConnection().levels();
            for (ResourceKey<Level> level : levels) {
                allDimensions.add(level.location().toString());
            }
        }
        // Always ensure vanilla dimensions are present
        addIfMissing("minecraft:overworld");
        addIfMissing("minecraft:the_nether");
        addIfMissing("minecraft:the_end");

        allDimensions.sort(String::compareToIgnoreCase);
        filteredDimensions.addAll(allDimensions);
    }

    private void addIfMissing(String dim) {
        if (!allDimensions.contains(dim))
            allDimensions.add(dim);
    }

    public void show(int centerX, int centerY, int parentWidth) {
        panelW = PANEL_WIDTH;
        panelH = SEARCH_HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        this.filter = "";
        this.searchFocused = true;
        filteredDimensions.clear();
        filteredDimensions.addAll(allDimensions);
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
        filteredDimensions.clear();
        if (this.filter.isEmpty()) {
            filteredDimensions.addAll(allDimensions);
        } else {
            for (String dim : allDimensions) {
                if (dim.toLowerCase().contains(this.filter)) {
                    filteredDimensions.add(dim);
                }
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        maxScrollRow = Math.max(0, filteredDimensions.size() - VISIBLE_ROWS);
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String displayFilter = filter.isEmpty() ? "\u00A77Search dimensions..." : filter;

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

        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filteredDimensions.size()) {
                String text = filteredDimensions.get(index);
                if (font.width(text) > listW - 4) {
                    text = font.plainSubstrByWidth(text, listW - 10) + "...";
                }
                guiGraphics.drawString(font, text, listX + 3, rowY + 4, rowHovered ? 0xFFFFFF : 0xBBBBBB, false);
            }
        }

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + VISIBLE_ROWS * ROW_HEIGHT;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbHeight = Math.max(10,
                    (int) ((float) VISIBLE_ROWS / (maxScrollRow + VISIBLE_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible)
            return false;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
            int listY = searchY + SEARCH_HEIGHT + PADDING;
            int listW = panelW - PADDING * 2 - 8;
            int scrollBarX = panelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        int searchY = panelY + PADDING;
        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredDimensions.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onSelect.accept(filteredDimensions.get(index));
                hide();
                return true;
            }
        }
        searchFocused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar)
            return false;
        int searchY = panelY + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
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

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible)
            return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible || !searchFocused)
            return false;
        if (keyCode == 256) {
            hide();
            return true;
        }
        if (keyCode == 259) {
            if (allSelected) {
                allSelected = false;
                setFilter("");
            } else if (!filter.isEmpty()) {
                setFilter(filter.substring(0, filter.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 65) {
            if (!filter.isEmpty())
                allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) {
            if (!filter.isEmpty())
                Minecraft.getInstance().keyboardHandler.setClipboard(filter);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 86) {
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
        if (!visible || !searchFocused)
            return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ' || c == '.' || c == ':') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }
}
