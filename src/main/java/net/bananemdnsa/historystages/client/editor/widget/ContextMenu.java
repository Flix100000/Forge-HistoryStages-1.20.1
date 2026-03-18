package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A simple right-click context menu overlay rendered on top of a screen.
 */
public class ContextMenu {
    private static final int ITEM_HEIGHT = 16;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 80;

    private final List<Entry> entries = new ArrayList<>();
    private int x, y;
    private int menuWidth;
    private int menuHeight;
    private boolean visible = false;
    private long showTime = 0;
    private static final long FADE_DURATION_MS = 120;

    public void addEntry(String label, Runnable action) {
        entries.add(new Entry(label, action));
    }

    public void show(int x, int y, Font font) {
        this.x = x;
        this.y = y;
        this.visible = true;
        this.showTime = System.currentTimeMillis();

        // Calculate width based on longest entry
        menuWidth = MIN_WIDTH;
        for (Entry e : entries) {
            menuWidth = Math.max(menuWidth, font.width(e.label) + PADDING * 2 + 4);
        }
        menuHeight = entries.size() * ITEM_HEIGHT + PADDING * 2;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        // Fade-in animation
        float fadeProgress = Math.min(1.0f, (System.currentTimeMillis() - showTime) / (float) FADE_DURATION_MS);
        int baseAlpha = (int) (fadeProgress * 0xFF);
        if (baseAlpha < 4) return;

        // Background with fade
        int bgColor = (baseAlpha << 24) | 0x1A1A1A;
        guiGraphics.fill(x, y, x + menuWidth, y + menuHeight, bgColor);

        // Gold top accent line
        int accentAlpha = (int) (fadeProgress * 0xFF);
        guiGraphics.fill(x, y, x + menuWidth, y + 2, (accentAlpha << 24) | 0xFFCC00);

        // Borders
        int borderAlpha = (int) (fadeProgress * 0x4A);
        int borderColor = (borderAlpha << 24) | 0x4A4A4A;
        guiGraphics.fill(x, y + menuHeight - 1, x + menuWidth, y + menuHeight, borderColor);
        guiGraphics.fill(x, y, x + 1, y + menuHeight, borderColor);
        guiGraphics.fill(x + menuWidth - 1, y, x + menuWidth, y + menuHeight, borderColor);

        for (int i = 0; i < entries.size(); i++) {
            int entryY = y + PADDING + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + menuWidth
                    && mouseY >= entryY && mouseY < entryY + ITEM_HEIGHT;

            if (hovered) {
                guiGraphics.fill(x + 1, entryY, x + menuWidth - 1, entryY + ITEM_HEIGHT, 0x40FFFFFF);
            }

            int textAlpha = (int) (fadeProgress * (hovered ? 0xFF : 0xCC));
            int textColor = (0xFF << 24) | (textAlpha << 16) | (textAlpha << 8) | textAlpha;
            guiGraphics.drawString(font, entries.get(i).label, x + PADDING + 2, entryY + 4, textColor, false);
        }
    }

    /**
     * @return true if the click was consumed by the context menu
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (mouseX >= x && mouseX <= x + menuWidth && mouseY >= y && mouseY <= y + menuHeight) {
            int index = (int) ((mouseY - y - PADDING) / ITEM_HEIGHT);
            if (index >= 0 && index < entries.size()) {
                entries.get(index).action.run();
            }
            hide();
            return true;
        }

        // Clicked outside — close menu
        hide();
        return true;
    }

    private record Entry(String label, Runnable action) {}
}
