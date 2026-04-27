package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Popup that shows all structures from a specific mod with checkboxes.
 * Appears after the entity-selection popup when adding a mod in the editor.
 * Mirrors the layout of ModEntitySelectionPopup.
 */
public class ModStructureSelectionPopup {
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 8;
    private static final int PADDING = 8;
    private static final int PANEL_WIDTH = 260;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 30;

    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    private record StructureRow(String id, boolean selected) {}

    private final List<StructureRow> structures = new ArrayList<>();
    private final Consumer<List<String>> onConfirm;

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;
    private String modDisplayName = "";
    private boolean allSelected = false;

    private float confirmHoverProgress = 0;
    private float skipHoverProgress = 0;

    private final Map<String, Float> checkboxHoverProgress = new HashMap<>();

    private int hoveredRowIndex = -1;
    private long rowHoverStartTime = 0;

    public ModStructureSelectionPopup(Consumer<List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    /** Returns false if this mod has no known structures (caller should skip showing it). */
    public boolean showForMod(String modId, String modDisplayName, int centerX, int centerY) {
        this.modDisplayName = modDisplayName;
        structures.clear();
        checkboxHoverProgress.clear();
        confirmHoverProgress = 0;
        skipHoverProgress = 0;
        hoveredRowIndex = -1;
        allSelected = false;

        String prefix = modId + ":";
        for (String id : ClientStructureRegistry.get()) {
            if (id.startsWith(prefix)) {
                structures.add(new StructureRow(id, false));
            }
        }

        if (structures.isEmpty()) return false;

        structures.sort((a, b) -> a.id.compareToIgnoreCase(b.id));

        int visibleRows = Math.min(VISIBLE_ROWS, structures.size());
        panelW = PANEL_WIDTH;
        panelH = HEADER_HEIGHT + PADDING + visibleRows * ROW_HEIGHT + PADDING + FOOTER_HEIGHT;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        updateMaxScroll();
        return true;
    }

    public void hide() { this.visible = false; }
    public boolean isVisible() { return visible; }

    private void updateMaxScroll() {
        int visibleRows = Math.min(VISIBLE_ROWS, structures.size());
        maxScrollRow = Math.max(0, structures.size() - visibleRows);
    }

    private int getCbX() {
        return panelX + panelW - PADDING - CHECKBOX_SIZE - 30;
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int visibleRows = Math.min(VISIBLE_ROWS, structures.size());

        // Dimmed background
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x88000000);

        // Panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        // Header: title on the left, "Add" label above the checkbox column
        String title = modDisplayName + " - Structures";
        if (font.width(title) > panelW - PADDING * 2 - 60) {
            title = font.plainSubstrByWidth(title, panelW - PADDING * 2 - 66) + "...";
        }
        guiGraphics.drawString(font, title, panelX + PADDING, panelY + 7, 0xFFFFFF, false);

        int cbX = getCbX();
        String addLabel = "Add";
        int addLabelX = cbX + (CHECKBOX_SIZE - font.width(addLabel)) / 2;
        guiGraphics.drawString(font, addLabel, addLabelX, panelY + 7, 0x999999, false);

        // Select-all checkbox below the label (same row as entity popup's select-all)
        int selectAllY = panelY + 20;
        renderCheckbox(guiGraphics, cbX, selectAllY, allSelected, mouseX, mouseY, "all");

        // List area
        int listY = panelY + HEADER_HEIGHT + PADDING;
        int listX = panelX + PADDING;
        int listW = panelW - PADDING * 2 - 8;
        int currentHoveredRow = -1;

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollRow + i;
            if (index >= structures.size()) break;
            int rowY = listY + i * ROW_HEIGHT;
            StructureRow row = structures.get(index);

            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHovered) currentHoveredRow = index;

            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            // Structure ID with marquee
            int textStartX = listX + 4;
            int textAvailW = cbX - textStartX - 8;
            String text = row.id;
            int textW = font.width(text);
            int textColor = rowHovered ? 0xFFFFFF : 0xBBBBBB;
            int textY = rowY + (ROW_HEIGHT - 8) / 2;

            if (textW > textAvailW && rowHovered && index == hoveredRowIndex) {
                long elapsed = System.currentTimeMillis() - rowHoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = textW - textAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    guiGraphics.enableScissor(textStartX, rowY, textStartX + textAvailW, rowY + ROW_HEIGHT);
                    guiGraphics.drawString(font, text, textStartX - scrollOff, textY, textColor, false);
                    guiGraphics.disableScissor();
                } else {
                    guiGraphics.enableScissor(textStartX, rowY, textStartX + textAvailW, rowY + ROW_HEIGHT);
                    guiGraphics.drawString(font, text, textStartX, textY, textColor, false);
                    guiGraphics.disableScissor();
                }
            } else if (textW > textAvailW) {
                String truncated = font.plainSubstrByWidth(text, textAvailW - 8) + "...";
                guiGraphics.drawString(font, truncated, textStartX, textY, textColor, false);
            } else {
                guiGraphics.drawString(font, text, textStartX, textY, textColor, false);
            }

            // Checkbox
            int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
            renderCheckbox(guiGraphics, cbX, cbY, row.selected, mouseX, mouseY, String.valueOf(index));
        }

        if (currentHoveredRow != hoveredRowIndex) {
            hoveredRowIndex = currentHoveredRow;
            rowHoverStartTime = System.currentTimeMillis();
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + visibleRows * ROW_HEIGHT;
            int scrollBarH = scrollBarBottom - scrollBarTop;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbH = Math.max(10, (int) ((float) visibleRows / (maxScrollRow + visibleRows) * scrollBarH));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarH - thumbH));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF888888);
        }

        // Footer buttons
        int footerY = panelY + panelH - FOOTER_HEIGHT;
        int btnW = 80;
        int btnH = 20;
        int confirmX = panelX + panelW / 2 - btnW - 5;
        int skipX = panelX + panelW / 2 + 5;

        boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + btnW
                && mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH;
        boolean skipHovered = mouseX >= skipX && mouseX < skipX + btnW
                && mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH;

        confirmHoverProgress = confirmHovered
                ? Math.min(1.0f, confirmHoverProgress + 0.1f)
                : Math.max(0.0f, confirmHoverProgress - 0.08f);
        skipHoverProgress = skipHovered
                ? Math.min(1.0f, skipHoverProgress + 0.1f)
                : Math.max(0.0f, skipHoverProgress - 0.08f);

        renderStyledButton(guiGraphics, font, "Confirm", confirmX, footerY + 5, btnW, btnH, confirmHoverProgress);
        renderStyledButton(guiGraphics, font, "Skip", skipX, footerY + 5, btnW, btnH, skipHoverProgress);
    }

    private void renderCheckbox(GuiGraphics g, int x, int y, boolean checked, int mx, int my, String hoverKey) {
        boolean hovered = mx >= x && mx < x + CHECKBOX_SIZE && my >= y && my < y + CHECKBOX_SIZE;
        float hp = checkboxHoverProgress.getOrDefault(hoverKey, 0f);
        hp = hovered ? Math.min(1.0f, hp + 0.1f) : Math.max(0.0f, hp - 0.08f);
        checkboxHoverProgress.put(hoverKey, hp);
        float progress = checked ? 1.0f : hp;

        int bgAlpha = (int) (0x30 + progress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - progress * 0x33);
        int bgB = (int) (0xFF - progress * 0xFF);
        g.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);
        int accentAlpha = (int) (0x60 + progress * 0x9F);
        g.fill(x, y + CHECKBOX_SIZE - 1, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (accentAlpha << 24) | 0xFFCC00);
        g.fill(x, y, x + CHECKBOX_SIZE, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + CHECKBOX_SIZE, 0x15FFFFFF);
        g.fill(x + CHECKBOX_SIZE - 1, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, 0x15FFFFFF);
        if (checked) {
            int color = 0xFFDDDDDD;
            g.fill(x + 2, y + 5, x + 4, y + 7, color);
            g.fill(x + 3, y + 6, x + 5, y + 8, color);
            g.fill(x + 4, y + 7, x + 6, y + 9, color);
            g.fill(x + 5, y + 6, x + 7, y + 8, color);
            g.fill(x + 6, y + 5, x + 8, y + 7, color);
            g.fill(x + 7, y + 4, x + 9, y + 6, color);
            g.fill(x + 8, y + 3, x + 10, y + 5, color);
        }
    }

    private void renderStyledButton(GuiGraphics g, Font font, String text, int x, int y, int w, int h, float hp) {
        int bgAlpha = (int) (0x30 + hp * 0x20);
        int bgG = (int) (0xFF - hp * 0x33);
        int bgB = (int) (0xFF - hp * 0xFF);
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB);
        int accentAlpha = (int) (0x60 + hp * 0x9F);
        g.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);
        g.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);
        int textGray = (int) (0xCC + hp * 0x33);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        int visibleRows = Math.min(VISIBLE_ROWS, structures.size());
        int footerY = panelY + panelH - FOOTER_HEIGHT;
        int btnW = 80;
        int btnH = 20;
        int confirmX = panelX + panelW / 2 - btnW - 5;
        int skipX = panelX + panelW / 2 + 5;

        // Footer buttons
        if (mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH) {
            if (mouseX >= confirmX && mouseX < confirmX + btnW) {
                List<String> selected = new ArrayList<>();
                for (StructureRow row : structures) {
                    if (row.selected) selected.add(row.id);
                }
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onConfirm.accept(selected);
                hide();
                return true;
            }
            if (mouseX >= skipX && mouseX < skipX + btnW) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hide();
                return true;
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int listY = panelY + HEADER_HEIGHT + PADDING;
            int listW = panelW - PADDING * 2 - 8;
            int scrollBarX = panelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + visibleRows * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
        }

        // Select-all checkbox in header
        int cbX = getCbX();
        int selectAllY = panelY + 20;
        if (mouseX >= cbX && mouseX < cbX + CHECKBOX_SIZE
                && mouseY >= selectAllY && mouseY < selectAllY + CHECKBOX_SIZE) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            allSelected = !allSelected;
            for (int idx = 0; idx < structures.size(); idx++) {
                structures.set(idx, new StructureRow(structures.get(idx).id, allSelected));
            }
            return true;
        }

        // Per-row checkbox
        int listY = panelY + HEADER_HEIGHT + PADDING;
        int listX = panelX + PADDING;
        int listW = panelW - PADDING * 2 - 8;
        for (int i = 0; i < visibleRows; i++) {
            int index = scrollRow + i;
            if (index >= structures.size()) break;
            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                StructureRow row = structures.get(index);
                structures.set(index, new StructureRow(row.id, !row.selected));
                allSelected = false;
                return true;
            }
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        updateScrollFromMouse(mouseY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return false;
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
        if (!visible) return false;
        if (keyCode == 256) { hide(); return true; } // ESC
        return false;
    }

    private void updateScrollFromMouse(double mouseY) {
        int visibleRows = Math.min(VISIBLE_ROWS, structures.size());
        int listY = panelY + HEADER_HEIGHT + PADDING;
        int listH = visibleRows * ROW_HEIGHT;
        int totalRows = maxScrollRow + visibleRows;
        int thumbH = Math.max(10, (int) ((float) visibleRows / totalRows * listH));
        float usableH = listH - thumbH;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listY - thumbH / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }
}
