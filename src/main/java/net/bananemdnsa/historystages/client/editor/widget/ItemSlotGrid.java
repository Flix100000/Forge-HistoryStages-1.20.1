package net.bananemdnsa.historystages.client.editor.widget;

import java.util.List;
import java.util.function.IntPredicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * A scrollable grid of item slots: chrome, hover, selection highlight, the item itself, the
 * scrollbar and the hover tooltip.
 *
 * <p>Every layout question is answered by {@link GridGeometry}, which is unit tested; this class
 * only paints. Two grids share it: the item picker's Registry tab and the recipe picker's master
 * column.
 *
 * <p>The item picker's other two tabs keep their own painting, and should. Inventory animates its
 * slots; Selected has three colour states rather than two, three washes including a two-layer
 * dim-and-redden, a draw order in which a filled slot never gets the grey chrome pass, and empty
 * slots that deliberately do not light up under the cursor. Sharing those would mean a colour
 * scheme abstraction, not a shared painter.
 *
 * <p>Stateless with respect to scrolling and selection: the owner passes both in. A widget that
 * kept its own scroll position would have to be told about every filter change, and the pickers
 * already track that themselves.
 */
public final class ItemSlotGrid {

    public static final int SLOT_SIZE = 18;
    /** Gap between the last column and the scrollbar. */
    public static final int SCROLLBAR_GAP = 6;
    private static final int SCROLLBAR_WIDTH = 4;

    private static final int SLOT_BORDER = 0xFF252525;
    private static final int SLOT_BORDER_HOVER = 0xFF4A4A4A;
    private static final int SLOT_FILL = 0xFF1A1A1A;
    private static final int SLOT_FILL_HOVER = 0xFF353535;
    private static final int SELECTED_BORDER = 0xFFFFCC00;
    private static final int SELECTED_FILL = 0xFF2A2510;
    private static final int SELECTED_WASH = 0x40FFCC00;
    private static final int TRACK = 0xFF252525;
    private static final int THUMB = 0xFF888888;

    private ItemSlotGrid() {
    }

    /**
     * Paints the contents of one filled cell; the chrome around it is already drawn.
     *
     * <p>Exists because the recipe picker's master column holds two kinds of entry now — items
     * and fluids — and teaching this class about fluids would tie a general slot grid to one
     * caller's problem. The caller knows what it is holding.
     *
     * @param x left edge of the slot, not of its contents; a 16px icon in an 18px slot insets by
     *          one, and that is the painter's business
     */
    @FunctionalInterface
    public interface SlotPainter {
        void paint(GuiGraphics g, int index, int x, int y);
    }

    /**
     * Draws the grid.
     *
     * @param stacks        what to draw, already filtered; only the visible window is read
     * @param isSelected    answers whether the entry at an index is selected, for the highlight.
     *                      An {@link IntPredicate} rather than {@code Predicate<Integer>}: this is
     *                      called once per painted cell, every frame, and boxing an index that far
     *                      past Integers cache would allocate for nothing
     * @param suppressHover true while a filter popup covers the grid, so a slot under the popup
     *                      does not light up as if it were reachable
     */
    public static void render(GuiGraphics g, int gridX, int gridY, int cols, int visibleRows,
                              int scrollRow, List<ItemStack> stacks,
                              IntPredicate isSelected,
                              int mouseX, int mouseY, boolean suppressHover) {
        render(g, gridX, gridY, cols, visibleRows, scrollRow, stacks.size(),
                (gg, index, x, y) -> gg.renderItem(stacks.get(index), x + 1, y + 1),
                isSelected, mouseX, mouseY, suppressHover);
    }

    /**
     * As above, but the caller says how to paint a cell rather than handing over stacks.
     *
     * @param entryCount how many entries exist; cells past it draw chrome only
     */
    public static void render(GuiGraphics g, int gridX, int gridY, int cols, int visibleRows,
                              int scrollRow, int entryCount, SlotPainter painter,
                              IntPredicate isSelected,
                              int mouseX, int mouseY, boolean suppressHover) {
        int startIndex = scrollRow * cols;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = startIndex + row * cols + col;
                int x = GridGeometry.slotX(gridX, SLOT_SIZE, col);
                int y = GridGeometry.slotY(gridY, SLOT_SIZE, row);

                boolean hovered = !suppressHover
                        && mouseX >= x && mouseX < x + SLOT_SIZE
                        && mouseY >= y && mouseY < y + SLOT_SIZE;

                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hovered ? SLOT_BORDER_HOVER : SLOT_BORDER);
                g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                        hovered ? SLOT_FILL_HOVER : SLOT_FILL);

                if (index >= entryCount) continue;

                boolean selected = isSelected.test(index);
                if (selected) {
                    g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SELECTED_BORDER);
                    g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SELECTED_FILL);
                }
                painter.paint(g, index, x, y);
                if (selected) {
                    // Painted over the contents, not under them: the wash is what makes a
                    // selected slot readable at a glance without hiding what is in it.
                    g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SELECTED_WASH);
                }
            }
        }
    }

    /** Draws the scrollbar to the right of the grid. Call only when something scrolls. */
    public static void renderScrollbar(GuiGraphics g, int gridX, int gridY, int cols,
                                       int visibleRows, int scrollRow, int maxScrollRow) {
        int x = gridX + cols * SLOT_SIZE + 2;
        int trackHeight = visibleRows * SLOT_SIZE;
        g.fill(x, gridY, x + SCROLLBAR_WIDTH, gridY + trackHeight, TRACK);
        int thumbH = GridGeometry.thumbHeight(trackHeight, visibleRows, maxScrollRow);
        int thumbY = gridY + GridGeometry.thumbOffset(trackHeight, thumbH, scrollRow, maxScrollRow);
        g.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbH, THUMB);
    }

    /** Whether the cursor is on the scrollbar, with a couple of pixels of grace either side. */
    public static boolean isOverScrollbar(int gridX, int gridY, int cols, int visibleRows,
                                          double mouseX, double mouseY) {
        int x = gridX + cols * SLOT_SIZE + 2;
        return mouseX >= x - 2 && mouseX <= x + SCROLLBAR_WIDTH + 2
                && mouseY >= gridY && mouseY < gridY + visibleRows * SLOT_SIZE;
    }

    /**
     * Draws a one-line tooltip at the cursor, kept inside the window.
     *
     * <p>Lifted in Z by the caller, not here — the caller knows what else is on screen.
     */
    public static void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY, String text,
                                     int screenW, int screenH) {
        int w = font.width(text) + 8;
        int h = 16;
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w + 2 > screenW - 4) x = mouseX - w - 4;
        if (y + h + 2 > screenH - 4) y = screenH - h - 6;
        if (x < 4) x = 4;
        if (y < 4) y = 4;
        g.fill(x - 2, y - 2, x + w + 2, y + h, 0xFF1A1A1A);
        g.fill(x - 1, y - 1, x + w + 1, y + h - 1, 0xFF0D0D1A);
        g.drawString(font, text, x + 2, y + 2, 0xFFFFFF, false);
    }
}
