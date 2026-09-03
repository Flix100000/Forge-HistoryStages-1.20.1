package net.bananemdnsa.historystages.client.editor.widget;

/**
 * Where the slots of an item grid are, and how far it scrolls.
 *
 * <p>Deliberately free of any Minecraft type. This is the part that decides which slot a click
 * lands on, and that decision is worth a unit test — test sources on this project cannot load
 * {@code net.minecraft}, so it has to be reachable without one. Same split as
 * {@link net.bananemdnsa.historystages.api.editor.widget.ToggleGeometry} beside its widget.
 *
 * <p>It lives under {@code client} rather than {@code api} on purpose: nothing outside this mod
 * consumes it yet, and putting a type in {@code api} is a promise about its stability.
 */
public final class GridGeometry {

    /** Shortest thumb that is still worth aiming at. */
    private static final int MIN_THUMB_HEIGHT = 10;

    private GridGeometry() {
    }

    /**
     * How many whole rows fit in {@code availableHeight}, never fewer than one.
     *
     * <p>Rows follow from the panel height rather than the panel height following from a fixed
     * row count, so a panel clamped down on a small screen loses rows instead of overflowing.
     */
    public static int rowsThatFit(int availableHeight, int slotSize) {
        return Math.max(1, availableHeight / slotSize);
    }

    /** How many rows are off the bottom, i.e. the largest legal scroll position. */
    public static int maxScrollRow(int entryCount, int cols, int visibleRows) {
        int totalRows = (entryCount + cols - 1) / cols;
        return Math.max(0, totalRows - visibleRows);
    }

    /** Keeps a scroll position inside {@code [0, maxScrollRow]}. */
    public static int clampScroll(int scrollRow, int maxScrollRow) {
        return Math.max(0, Math.min(maxScrollRow, scrollRow));
    }

    /** Left edge of the slot in column {@code col}. */
    public static int slotX(int gridX, int slotSize, int col) {
        return gridX + col * slotSize;
    }

    /** Top edge of the slot in visible row {@code row}. */
    public static int slotY(int gridY, int slotSize, int row) {
        return gridY + row * slotSize;
    }

    /**
     * Index into the filtered entry list under the cursor, or {@code -1} when the cursor is not
     * over the grid at all. The result may still be past the end of the list — the caller knows
     * how long its list is and this class does not.
     */
    public static int indexAt(int gridX, int gridY, int slotSize, int cols, int visibleRows,
                              int scrollRow, double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + cols * slotSize) return -1;
        if (mouseY < gridY || mouseY >= gridY + visibleRows * slotSize) return -1;
        int col = (int) ((mouseX - gridX) / slotSize);
        int row = (int) ((mouseY - gridY) / slotSize);
        return (scrollRow + row) * cols + col;
    }

    /** Thumb height for a track of {@code trackHeight}, floored so it stays grabbable. */
    public static int thumbHeight(int trackHeight, int visibleRows, int maxScrollRow) {
        if (maxScrollRow <= 0) return trackHeight;
        int totalRows = maxScrollRow + visibleRows;
        int proportional = (int) ((float) visibleRows / totalRows * trackHeight);
        return Math.max(MIN_THUMB_HEIGHT, proportional);
    }

    /** How far down the track the thumb starts. */
    public static int thumbOffset(int trackHeight, int thumbHeight, int scrollRow, int maxScrollRow) {
        if (maxScrollRow <= 0) return 0;
        return (int) ((float) scrollRow / maxScrollRow * (trackHeight - thumbHeight));
    }

    /**
     * Scroll position for a thumb dragged to {@code mouseY}, where {@code grabOffset} is how far
     * below the thumb's top the cursor was when the drag began. Without that offset the thumb
     * jumps so its top meets the cursor on the first pixel of every drag.
     */
    public static int scrollFromThumbDrag(int trackTop, int trackHeight, int thumbHeight,
                                          int grabOffset, int maxScrollRow, double mouseY) {
        int usable = trackHeight - thumbHeight;
        if (usable <= 0) return 0;
        float ratio = (float) ((mouseY - trackTop - grabOffset) / usable);
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        return clampScroll(Math.round(ratio * maxScrollRow), maxScrollRow);
    }
}
