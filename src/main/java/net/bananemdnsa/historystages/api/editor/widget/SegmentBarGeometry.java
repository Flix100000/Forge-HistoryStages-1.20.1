package net.bananemdnsa.historystages.api.editor.widget;

/**
 * Where the halves — or thirds, or fifths — of a segmented control begin and end.
 *
 * <p>This is the arithmetic {@code ToggleGeometry} used to spell out for exactly two segments,
 * generalised to any number and nothing else: same padding, same chrome, same rule about which
 * segment a pixel on the divider belongs to. The two-segment switch now asks this, so there is
 * one place where a click turns into a segment index rather than two that are free to drift.
 *
 * <p>Free of any Minecraft type on purpose, like the geometry it grew out of. Deciding which
 * segment was clicked is worth a unit test, and test sources on this project cannot load
 * {@code net.minecraft}.
 *
 * <p>Callers pass measured label widths rather than a font, which is what keeps every segment the
 * same size: the width is computed from the widest label, so the control does not change shape
 * when the selection moves.
 */
public final class SegmentBarGeometry {

    /** Height of the control, matching the toggle rows that already exist in the editor. */
    public static final int HEIGHT = 14;

    /** Horizontal breathing room around a label inside its segment. */
    private static final int SEGMENT_PADDING = 10;

    private SegmentBarGeometry() {
    }

    /** Width of one segment, sized so the widest label fits in any of them. */
    public static int segmentWidth(int... labelWidths) {
        int widest = 0;
        for (int width : labelWidths) widest = Math.max(widest, width);
        return widest + SEGMENT_PADDING;
    }

    /**
     * Total width: every segment, plus the frame at each end and one divider between neighbours.
     */
    public static int width(int... labelWidths) {
        return labelWidths.length * segmentWidth(labelWidths) + labelWidths.length + 1;
    }

    /** X where segment {@code index}'s interior starts, just past the frame or divider before it. */
    public static int segmentX(int x, int index, int... labelWidths) {
        return x + 1 + index * (segmentWidth(labelWidths) + 1);
    }

    /**
     * Which segment the cursor is over, or {@code -1} when it is beside the control.
     *
     * <p>Answers clicking, where the caller is a row or a strip that has already established the
     * cursor is inside it vertically. Each divider falls to the segment on its left, which costs
     * that neighbour one pixel and keeps the ranges free of gaps and overlaps.
     */
    public static int indexAt(int x, double mouseX, int... labelWidths) {
        if (labelWidths.length == 0) return -1;
        if (mouseX < x || mouseX >= x + width(labelWidths)) return -1;
        int offset = (int) Math.floor(mouseX - x) - 1;
        if (offset < 0) return 0;
        return Math.min(labelWidths.length - 1, offset / (segmentWidth(labelWidths) + 1));
    }

    /**
     * Which segment the cursor is over, or {@code -1}. Checks both axes — this answers hovering,
     * where being near the box is not being on it.
     */
    public static int segmentAt(int x, int y, double mouseX, double mouseY, int... labelWidths) {
        if (mouseY < y || mouseY >= y + HEIGHT) return -1;
        return indexAt(x, mouseX, labelWidths);
    }
}
