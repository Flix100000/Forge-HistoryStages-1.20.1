package net.bananemdnsa.historystages.client.editor.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The grid maths two pickers now share. Every one of these used to be inline arithmetic
 * repeated per widget, which is how the recipe picker and the item picker ended up scrolling
 * differently from each other.
 */
class GridGeometryTest {

    private static final int SLOT = 18;
    private static final int COLS = 9;

    @Test
    void rowsFillTheAvailableHeightAndRoundDown() {
        // 130px of room holds seven whole 18px rows with 4px left over.
        assertEquals(7, GridGeometry.rowsThatFit(130, SLOT));
    }

    @Test
    void thereIsAlwaysAtLeastOneRow() {
        // A panel squeezed below one row still has to draw something rather than divide to zero.
        assertEquals(1, GridGeometry.rowsThatFit(4, SLOT));
        assertEquals(1, GridGeometry.rowsThatFit(0, SLOT));
    }

    @Test
    void maxScrollIsTheRowsThatDoNotFit() {
        // 100 entries over 9 columns is 12 rows; showing 7 leaves 5 to scroll through.
        assertEquals(5, GridGeometry.maxScrollRow(100, COLS, 7));
    }

    @Test
    void aListThatFitsDoesNotScroll() {
        assertEquals(0, GridGeometry.maxScrollRow(63, COLS, 7));
        assertEquals(0, GridGeometry.maxScrollRow(0, COLS, 7));
    }

    @Test
    void theIndexUnderTheCursorCountsFromTheScrolledRow() {
        // Scrolled to row 2, cursor on the second visible row, fourth column:
        // (2 + 1) * 9 + 3 = 30.
        int index = GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 2,
                3 * SLOT + 5, 1 * SLOT + 5);
        assertEquals(30, index);
    }

    @Test
    void aCursorOutsideTheGridHasNoIndex() {
        assertEquals(-1, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, -1, 5));
        assertEquals(-1, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, 5, -1));
        assertEquals(-1, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, COLS * SLOT, 5));
        assertEquals(-1, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, 5, 7 * SLOT));
    }

    @Test
    void theLastPixelOfASlotStillBelongsToIt() {
        // Off-by-one here means the bottom row of every grid is one pixel dead.
        assertEquals(0, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, SLOT - 1, SLOT - 1));
        assertEquals(COLS, GridGeometry.indexAt(0, 0, SLOT, COLS, 7, 0, 0, SLOT));
    }

    @Test
    void scrollIsClampedToBothEnds() {
        assertEquals(0, GridGeometry.clampScroll(-3, 5));
        assertEquals(5, GridGeometry.clampScroll(9, 5));
        assertEquals(3, GridGeometry.clampScroll(3, 5));
    }

    @Test
    void theThumbNeverShrinksBelowItsMinimum() {
        // 500 rows in a 126px track would give a 1px thumb nobody can grab.
        assertEquals(10, GridGeometry.thumbHeight(126, 7, 500));
    }

    @Test
    void theThumbFillsTheTrackWhenNothingScrolls() {
        assertEquals(126, GridGeometry.thumbHeight(126, 7, 0));
    }

    @Test
    void theThumbSitsAtTheBottomOnTheLastRow() {
        int trackH = 126;
        int thumbH = GridGeometry.thumbHeight(trackH, 7, 5);
        assertEquals(trackH - thumbH, GridGeometry.thumbOffset(trackH, thumbH, 5, 5));
        assertEquals(0, GridGeometry.thumbOffset(trackH, thumbH, 0, 5));
    }

    @Test
    void draggingTheThumbKeepsTheGrabPointUnderTheCursor() {
        // Without the grab offset the thumb snaps to the cursor on the first pixel of every drag.
        // Note this is not what SearchableItemList does — that one centres the thumb on the
        // cursor and keeps doing so, because changing an existing panel's drag feel is not a
        // refactor. This is the behaviour the recipe picker's own scrollbar is built on.
        int trackTop = 100;
        int trackH = 126;
        int thumbH = GridGeometry.thumbHeight(trackH, 7, 5);
        // Grabbed 8px down the thumb, cursor has not moved: the scroll must not change.
        assertEquals(0, GridGeometry.scrollFromThumbDrag(trackTop, trackH, thumbH, 8, 5,
                trackTop + 8));
    }

    @Test
    void draggingPastEitherEndClampsRatherThanOverruns() {
        int trackH = 126;
        int thumbH = GridGeometry.thumbHeight(trackH, 7, 5);
        assertEquals(0, GridGeometry.scrollFromThumbDrag(100, trackH, thumbH, 0, 5, -500));
        assertEquals(5, GridGeometry.scrollFromThumbDrag(100, trackH, thumbH, 0, 5, 5000));
    }

    @Test
    void aTrackWithNothingToScrollDoesNotDivideByZero() {
        assertEquals(0, GridGeometry.scrollFromThumbDrag(100, 126, 126, 0, 0, 150));
    }
}
