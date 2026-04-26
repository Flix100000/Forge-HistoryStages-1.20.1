package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searchable overlay list of all structures known to the server, with an
 * internal tab switcher between plain Structures and Structure Tags.
 * <p>
 * The single {@code onSelect} callback receives either a plain structure ID
 * (e.g. {@code minecraft:stronghold}) when a structure was picked, or the same
 * string prefixed with {@code #} (e.g. {@code #minecraft:village}) when a tag
 * was picked — the parent screen stores both in one list and the prefix tells
 * the lock handler which check to apply.
 * <p>
 * Data source: {@link ClientStructureRegistry} (synced on login).
 */
public class SearchableStructureList {
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 10;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int PANEL_WIDTH = 260;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;

    private final List<String> allStructures = new ArrayList<>();
    private final List<String> allTags = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private final Consumer<String> onSelect;

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;

    /** 0 = Structures tab, 1 = Tags tab */
    private int activeTab = 0;

    // Tab indicator animation
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    // Marquee state for long row entries
    private int hoveredRowIndex = -1;
    private long rowHoverStartTime = 0;
    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    public SearchableStructureList(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        reloadData();
    }

    private void reloadData() {
        allStructures.clear();
        allStructures.addAll(ClientStructureRegistry.get());
        allTags.clear();
        allTags.addAll(ClientStructureRegistry.getTags());
    }

    public void show(int centerX, int centerY, int parentWidth) {
        panelW = PANEL_WIDTH;
        panelH = TAB_HEIGHT + 4 + SEARCH_HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        this.filter = "";
        this.searchFocused = true;
        this.activeTab = 0;
        this.tabIndicatorInit = false;

        // Re-pull from registry each time it's opened (may have been synced late)
        reloadData();
        applyFilter();
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
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        List<String> source = activeTab == 0 ? allStructures : allTags;
        if (filter.isEmpty()) {
            filtered.addAll(source);
        } else {
            for (String s : source) {
                if (s.toLowerCase().contains(filter)) filtered.add(s);
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        maxScrollRow = Math.max(0, filtered.size() - VISIBLE_ROWS);
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String placeholder = activeTab == 0 ? "\u00A77Search structures..." : "\u00A77Search structure tags...";
        String displayFilter = filter.isEmpty() ? placeholder : filter;

        if (allSelected && !filter.isEmpty()) {
            int textW = font.width(filter);
            guiGraphics.fill(searchX + 3, searchY + 3, searchX + 5 + textW, searchY + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }

        guiGraphics.drawString(font, displayFilter, searchX + 4, searchY + 6,
                filter.isEmpty() ? 0x666666 : 0xFFFFFF, false);

        if (searchFocused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = searchX + 4 + (filter.isEmpty() ? 0 : font.width(filter));
            guiGraphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + SEARCH_HEIGHT - 4, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();

        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        int currentHoveredRow = -1;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && index < filtered.size();
            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filtered.size()) {
                if (rowHovered) currentHoveredRow = index;

                String raw = filtered.get(index);
                String text = activeTab == 1 ? "#" + raw : raw;
                int textColor = rowHovered ? 0xFFFFFF : 0xBBBBBB;
                int textX = listX + 3;
                int textY = rowY + 4;
                int textAvailW = listW - 6;
                int textW = font.width(text);

                if (textW > textAvailW) {
                    if (rowHovered && index == hoveredRowIndex) {
                        long elapsed = System.currentTimeMillis() - rowHoverStartTime;
                        if (elapsed > MARQUEE_DELAY_MS) {
                            float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                            int maxMarquee = textW - textAvailW + 10;
                            float cycle = (float) maxMarquee * 2;
                            float pos = scrollProg % cycle;
                            int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                            guiGraphics.enableScissor(textX, rowY, textX + textAvailW, rowY + ROW_HEIGHT);
                            guiGraphics.drawString(font, text, textX - scrollOff, textY, textColor, false);
                            guiGraphics.disableScissor();
                        } else {
                            guiGraphics.enableScissor(textX, rowY, textX + textAvailW, rowY + ROW_HEIGHT);
                            guiGraphics.drawString(font, text, textX, textY, textColor, false);
                            guiGraphics.disableScissor();
                        }
                    } else {
                        String truncated = font.plainSubstrByWidth(text, textAvailW - 6) + "...";
                        guiGraphics.drawString(font, truncated, textX, textY, textColor, false);
                    }
                } else {
                    guiGraphics.drawString(font, text, textX, textY, textColor, false);
                }
            }
        }

        if (currentHoveredRow != hoveredRowIndex) {
            hoveredRowIndex = currentHoveredRow;
            rowHoverStartTime = System.currentTimeMillis();
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

    private void renderTabs(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        String[] labels = { "Structures", "Tags" };
        int[] tabXs = new int[2];
        int[] tabWs = new int[2];

        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            tabWs[i] = font.width(labels[i]) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        if (!tabIndicatorInit) {
            tabIndicatorX = tabXs[activeTab];
            tabIndicatorW = tabWs[activeTab];
            tabIndicatorInit = true;
        }

        float targetX = tabXs[activeTab];
        float targetW = tabWs[activeTab];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f) tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f) tabIndicatorW = targetW;

        for (int i = 0; i < 2; i++) {
            boolean active = (i == activeTab);
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
        guiGraphics.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING,
                tabY + TAB_HEIGHT + 1, 0xFF555555);
    }

    private int getTabAt(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int tabY = panelY + PADDING;
        String[] labels = { "Structures", "Tags" };
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

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Tab clicks first
        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab >= 0 && clickedTab != activeTab) {
            activeTab = clickedTab;
            scrollRow = 0;
            searchFocused = true;
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            applyFilter();
            return true;
        }

        int topOffset = PADDING + TAB_HEIGHT + 4;

        if (maxScrollRow > 0) {
            int searchY = panelY + topOffset;
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

        int searchY = panelY + topOffset;
        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filtered.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                String picked = filtered.get(index);
                onSelect.accept(activeTab == 1 ? "#" + picked : picked);
                hide();
                return true;
            }
        }
        searchFocused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) scrollY));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible || !searchFocused) return false;
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
            if (!filter.isEmpty()) allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) {
            if (!filter.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(filter);
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
        if (!visible || !searchFocused) return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ' || c == '.' || c == ':' || c == '/') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }
}
