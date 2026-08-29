package net.bananemdnsa.historystages.api.editor.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The switch is only scannable while both halves stay the same size, and it is only clickable
 * while every pixel of it belongs to exactly one half. Both are arithmetic, so both are checked
 * here rather than in front of a running game.
 */
class ToggleGeometryTest {

    /** Roughly what the vanilla font measures for "On" and "Off". */
    private static final int ON_W = 13;
    private static final int OFF_W = 15;

    @Test
    void widthComesFromTheWiderLabelAndNotFromTheCurrentValue() {
        int expected = 2 * (OFF_W + 10) + 3;
        assertEquals(expected, ToggleGeometry.width(ON_W, OFF_W));
        assertEquals(expected, ToggleGeometry.width(OFF_W, ON_W),
                "swapping the labels must not change the width, or the box jumps on every click");
    }

    @Test
    void everyPixelBelongsToExactlyOneHalf() {
        int x = 40;
        int y = 7;
        int w = ToggleGeometry.width(ON_W, OFF_W);

        int on = 0;
        int off = 0;
        for (int px = x; px < x + w; px++) {
            Boolean hit = ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, px, y + 3);
            if (Boolean.TRUE.equals(hit)) on++;
            else if (Boolean.FALSE.equals(hit)) off++;
        }

        assertEquals(w, on + off, "no pixel inside the box may be dead");
        assertTrue(Math.abs(on - off) <= 1,
                "the two halves must be the same size give or take the divider, was "
                        + on + " vs " + off);
    }

    @Test
    void theLeftHalfPicksOnAndTheRightHalfPicksOff() {
        int x = 40;
        int y = 7;
        int w = ToggleGeometry.width(ON_W, OFF_W);
        assertEquals(Boolean.TRUE, ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x + 2, y + 3));
        assertEquals(Boolean.FALSE, ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x + w - 2, y + 3));
    }

    @Test
    void outsideTheBoxIsNoSegment() {
        int x = 40;
        int y = 7;
        int w = ToggleGeometry.width(ON_W, OFF_W);
        assertNull(ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x - 1, y + 3));
        assertNull(ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x + w, y + 3));
        assertNull(ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x + 2, y - 1),
                "above the box");
        assertNull(ToggleGeometry.segmentAt(x, y, ON_W, OFF_W, x + 2, y + ToggleGeometry.HEIGHT),
                "below the box");
    }

    @Test
    void theClickTestIgnoresHeightBecauseTheRowAlreadyCheckedIt() {
        int x = 40;
        int w = ToggleGeometry.width(ON_W, OFF_W);
        assertEquals(Boolean.TRUE, ToggleGeometry.valueAt(x, ON_W, OFF_W, x + 2));
        assertEquals(Boolean.FALSE, ToggleGeometry.valueAt(x, ON_W, OFF_W, x + w - 2));
        assertNull(ToggleGeometry.valueAt(x, ON_W, OFF_W, x + w));
    }
}
