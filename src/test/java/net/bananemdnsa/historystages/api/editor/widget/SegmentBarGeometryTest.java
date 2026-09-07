package net.bananemdnsa.historystages.api.editor.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A section bar is only usable while every pixel of it picks exactly one section, and only
 * readable while the segments stay the same size as the selection moves. Both are arithmetic, so
 * both are checked here rather than in front of a running game.
 *
 * <p>The last test is the reason this class exists at all: the two-segment switch is defined as
 * the two-segment case of this, and {@code ToggleGeometryTest} would not notice if the general
 * form quietly stopped agreeing with it at three.
 */
class SegmentBarGeometryTest {

    /** Roughly what the vanilla font measures for the three section names. */
    private static final int[] THREE = {26, 43, 30};

    @Test
    void everySegmentIsTheSameWidthWhicheverLabelIsWidest() {
        assertEquals(SegmentBarGeometry.segmentWidth(THREE),
                SegmentBarGeometry.segmentWidth(43, 26, 30),
                "reordering the labels must not change the segment width, or the bar changes"
                        + " shape when the sections are renamed");
    }

    @Test
    void everyPixelBelongsToExactlyOneSegment() {
        int x = 40;
        int w = SegmentBarGeometry.width(THREE);

        int[] hits = new int[THREE.length];
        for (int px = x; px < x + w; px++) {
            int index = SegmentBarGeometry.indexAt(x, px, THREE);
            assertTrue(index >= 0 && index < THREE.length,
                    "no pixel inside the bar may be dead, but " + (px - x) + " gave " + index);
            hits[index]++;
        }

        assertEquals(w, hits[0] + hits[1] + hits[2]);
        for (int i = 1; i < hits.length; i++) {
            assertTrue(Math.abs(hits[i] - hits[0]) <= 1,
                    "segments must be the same size give or take a divider, was "
                            + hits[0] + " vs " + hits[i]);
        }
    }

    @Test
    void theSegmentsRunLeftToRight() {
        int x = 40;
        int w = SegmentBarGeometry.width(THREE);
        assertEquals(0, SegmentBarGeometry.indexAt(x, x + 2, THREE));
        assertEquals(THREE.length - 1, SegmentBarGeometry.indexAt(x, x + w - 2, THREE));
    }

    @Test
    void outsideTheBarIsNoSegment() {
        int x = 40;
        int y = 7;
        int w = SegmentBarGeometry.width(THREE);
        assertEquals(-1, SegmentBarGeometry.indexAt(x, x - 1, THREE));
        assertEquals(-1, SegmentBarGeometry.indexAt(x, x + w, THREE));
        assertEquals(-1, SegmentBarGeometry.segmentAt(x, y, x + 2, y - 1, THREE), "above the bar");
        assertEquals(-1, SegmentBarGeometry.segmentAt(x, y, x + 2,
                y + SegmentBarGeometry.HEIGHT, THREE), "below the bar");
    }

    @Test
    void aSegmentStartsWhereTheOneBeforeItEnds() {
        int x = 40;
        for (int i = 1; i < THREE.length; i++) {
            int start = SegmentBarGeometry.segmentX(x, i, THREE);
            assertEquals(i, SegmentBarGeometry.indexAt(x, start, THREE));
            assertEquals(i - 1, SegmentBarGeometry.indexAt(x, start - 1, THREE),
                    "the divider belongs to the segment on its left");
        }
    }

    @Test
    void theTwoSegmentCaseIsStillTheEditorsOnOffSwitch() {
        int onW = 13;
        int offW = 15;
        int x = 40;

        assertEquals(2 * (offW + 10) + 3, SegmentBarGeometry.width(onW, offW),
                "the on/off switch is defined as the two-segment case of this; if the width"
                        + " formula moves, every toggle in the editor changes size");
        assertEquals(x + 1, SegmentBarGeometry.segmentX(x, 0, onW, offW));
        assertEquals(x + 2 + (offW + 10), SegmentBarGeometry.segmentX(x, 1, onW, offW));
    }
}
