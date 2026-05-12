package net.bananemdnsa.historystages.client.editor.widget;

import net.astr0.historystages.api.StageDefinition;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all registered stages (global or individual).
 * Allows selecting a stage by clicking on it.
 */
public class SearchableStageList {
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 10;
    private static final int PADDING = 6;
    private static final int PANEL_WIDTH = 260;

    private final List<StageListEntry> allStages = new ArrayList<>();
    private final List<StageListEntry> filteredStages = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final Supplier<Collection<String>> alreadyAddedSupplier;
    private final SearchBar searchBar;

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

    // Marquee
    private int hoveredRow = -1;
    private long hoverStartTime = 0;
    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    // If set, this stage ID is excluded from the list (to prevent self-dependency)
    private String excludeStageId = null;

    // Mode: true = show individual stages, false = show global stages
    private boolean showIndividual;

    public SearchableStageList(Consumer<String> onSelect, boolean showIndividual) {
        this(onSelect, showIndividual, null);
    }

    public SearchableStageList(Consumer<String> onSelect, boolean showIndividual,
            Supplier<Collection<String>> alreadyAddedSupplier) {
        this.onSelect = onSelect;
        this.showIndividual = showIndividual;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.searchBar = new SearchBar("Search stages...").onChange(this::applyFilter);
        // Stages are user-defined — namespaces are not meaningful here, so only
        // the "hide already added" toggle is registered (when a supplier exists).
        if (alreadyAddedSupplier != null) {
            searchBar.filters().addOption("hide_added", "Hide already added", null);
        }
        rebuildList();
    }

    public void setExcludeStageId(String stageId) {
        this.excludeStageId = stageId;
    }

    private void rebuildList() {
        allStages.clear();
        Map<String, StageDefinition> stages = showIndividual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        for (Map.Entry<String, StageDefinition> entry : stages.entrySet()) {
            allStages.add(new StageListEntry(entry.getKey(), entry.getValue().getDisplayName()));
        }
        allStages.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
    }

    public void show(int centerX, int centerY, int parentWidth) {
        rebuildList();
        panelW = PANEL_WIDTH;
        panelH = SearchBar.HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        searchBar.setText("");
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
        filteredStages.clear();
        for (StageListEntry entry : allStages) {
            if (excludeStageId != null && entry.id.equals(excludeStageId))
                continue;
            if (!matchesDropdownFilters(entry.id))
                continue;
            if (filter.isEmpty()
                    || entry.id.toLowerCase().contains(filter)
                    || entry.displayName.toLowerCase().contains(filter)) {
                filteredStages.add(entry);
            }
        }
        updateMaxScroll();
    }

    private boolean matchesDropdownFilters(String id) {
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            Collection<String> added = alreadyAddedSupplier.get();
            if (added != null && added.contains(id))
                return false;
        }
        return true;
    }

    private void updateMaxScroll() {
        maxScrollRow = Math.max(0, filteredStages.size() - VISIBLE_ROWS);
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);

        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        boolean anyRowHovered = false;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = !filterUiHovered && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHovered)
                anyRowHovered = true;
            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filteredStages.size()) {
                StageListEntry entry = filteredStages.get(index);
                String text = entry.displayName + " §7(" + entry.id + ")";
                int textW = font.width(text);
                int textAvailW = listW - 6;
                int textColor = rowHovered ? 0xFFFFFF : 0xBBBBBB;

                if (rowHovered) {
                    if (hoveredRow != index) {
                        hoveredRow = index;
                        hoverStartTime = System.currentTimeMillis();
                    }
                }

                if (textW > textAvailW && rowHovered && hoveredRow == index) {
                    long elapsed = System.currentTimeMillis() - hoverStartTime;
                    if (elapsed > MARQUEE_DELAY_MS) {
                        float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                        int maxMarquee = textW - textAvailW + 10;
                        float cycle = (float) maxMarquee * 2;
                        float pos = scrollProg % cycle;
                        int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                        guiGraphics.enableScissor(listX, rowY, listX + listW, rowY + ROW_HEIGHT);
                        guiGraphics.drawString(font, text, listX + 3 - scrollOff, rowY + 4, textColor, false);
                        guiGraphics.disableScissor();
                    } else {
                        guiGraphics.drawString(font, font.plainSubstrByWidth(text, textAvailW - 6) + "...", listX + 3,
                                rowY + 4, textColor, false);
                    }
                } else if (textW > textAvailW) {
                    guiGraphics.drawString(font, font.plainSubstrByWidth(text, textAvailW - 6) + "...", listX + 3,
                            rowY + 4, textColor, false);
                } else {
                    guiGraphics.drawString(font, text, listX + 3, rowY + 4, textColor, false);
                }
            }
        }
        if (!anyRowHovered)
            hoveredRow = -1;

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
        if (searchBar.mouseClicked(mouseX, mouseY))
            return true;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
            int listY = searchY + SearchBar.HEIGHT + PADDING;
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
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredStages.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onSelect.accept(filteredStages.get(index).id);
                hide();
                return true;
            }
        }
        searchBar.setFocused(true);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar)
            return false;
        int searchY = panelY + PADDING;
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
        if (!visible)
            return false;
        if (searchBar.keyPressed(keyCode))
            return true;
        if (keyCode == 256) {
            hide();
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible)
            return false;
        return searchBar.charTyped(c);
    }

    private record StageListEntry(String id, String displayName) {
    }
}
