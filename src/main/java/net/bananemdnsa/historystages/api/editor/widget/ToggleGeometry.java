package net.bananemdnsa.historystages.api.editor.widget;

/**
 * Where the two halves of a {@link ToggleControl} begin and end.
 *
 * <p>Separate from the widget itself, and deliberately free of any Minecraft type: this is the
 * part that decides whether a click lands on "on" or on "off", and that decision is worth a unit
 * test. Test sources on this project cannot load {@code net.minecraft}, so anything they need to
 * check has to be reachable without it.
 *
 * <p>Callers pass measured text widths rather than a font, which is also what keeps the width
 * honest: it is computed from <em>both</em> labels, so the box is the same size whichever value
 * it currently shows. The older toggles sized themselves from the current word and visibly
 * changed width on every click.
 *
 * <p>The arithmetic itself lives in {@code SegmentBarGeometry} now, which does the same thing for
 * any number of segments. What is left here is the naming: two segments that mean a value, called
 * on and off. A control that switches which section you are looking at is a different gesture and
 * gets its own front door, but there is no reason for it to lay itself out differently.
 */
public final class ToggleGeometry {

    /** Height of the control, matching the toggle rows that already exist in the editor. */
    public static final int HEIGHT = SegmentBarGeometry.HEIGHT;

    private ToggleGeometry() {
    }

    /** Width of one half, sized so both labels fit in either of them. */
    public static int segmentWidth(int onLabelWidth, int offLabelWidth) {
        return SegmentBarGeometry.segmentWidth(onLabelWidth, offLabelWidth);
    }

    /** Total width of the control. */
    public static int width(int onLabelWidth, int offLabelWidth) {
        return SegmentBarGeometry.width(onLabelWidth, offLabelWidth);
    }

    /** X where the on half's interior starts, just inside the left frame. */
    public static int onSegmentX(int x) {
        return x + 1;
    }

    /** X where the off half's interior starts, just past the divider. */
    public static int offSegmentX(int x, int onLabelWidth, int offLabelWidth) {
        return SegmentBarGeometry.segmentX(x, 1, onLabelWidth, offLabelWidth);
    }

    /**
     * The dividing line for hit testing: everything left of it means on, everything from it
     * rightwards means off. The frame and the divider fall to the on side, which costs the off
     * half one pixel and keeps the two ranges free of gaps and overlaps.
     */
    public static int splitX(int x, int onLabelWidth, int offLabelWidth) {
        return offSegmentX(x, onLabelWidth, offLabelWidth);
    }

    /**
     * Which half the cursor is over, or {@code null} when it is not over the control at all.
     * Checks both axes — this answers hovering, where being near the box is not being on it.
     */
    public static Boolean segmentAt(int x, int y, int onLabelWidth, int offLabelWidth,
                                    double mouseX, double mouseY) {
        return asValue(SegmentBarGeometry.segmentAt(x, y, mouseX, mouseY,
                onLabelWidth, offLabelWidth));
    }

    /**
     * Which half the cursor is over horizontally, or {@code null} when it is beside the control.
     * Answers clicking, where the caller is a row that has already established the cursor is
     * inside it — a 14px tall target inside a 24px row would only be fiddly.
     */
    public static Boolean valueAt(int x, int onLabelWidth, int offLabelWidth, double mouseX) {
        return asValue(SegmentBarGeometry.indexAt(x, mouseX, onLabelWidth, offLabelWidth));
    }

    /** Segment 0 is on, segment 1 is off, and no segment at all is no answer. */
    private static Boolean asValue(int index) {
        if (index < 0) return null;
        return index == 0 ? Boolean.TRUE : Boolean.FALSE;
    }
}
