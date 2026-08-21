package net.bananemdnsa.historystages.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollGeometryTest {

    @Test
    void theParchmentIsUnchanged() {
        assertEquals(23, OpenScrollGeometry.PARCHMENT_X);
        assertEquals(36, OpenScrollGeometry.PARCHMENT_Y);
        assertEquals(140, OpenScrollGeometry.PARCHMENT_WIDTH);
        assertEquals(183, OpenScrollGeometry.parchmentBottom());
    }

    @Test
    void theTextBlockIsInsetFromThePaperOnBothSides() {
        assertEquals(27, OpenScrollGeometry.CONTENT_X);
        assertEquals(132, OpenScrollGeometry.CONTENT_WIDTH);
        // Symmetry is the point: the right margin has to equal the left one.
        assertEquals(OpenScrollGeometry.PARCHMENT_X + OpenScrollGeometry.PARCHMENT_WIDTH
                        - (OpenScrollGeometry.CONTENT_X + OpenScrollGeometry.CONTENT_WIDTH),
                OpenScrollGeometry.CONTENT_X - OpenScrollGeometry.PARCHMENT_X);
    }

    @Test
    void nothingIsDrawnOnTheVeryEdgeOfThePaper() {
        assertTrue(OpenScrollGeometry.TABS_Y > OpenScrollGeometry.PARCHMENT_Y);
        assertTrue(OpenScrollGeometry.contentBottom(false) < OpenScrollGeometry.parchmentBottom());
        // The arrows are the lowest thing on the paper, so they are what the bottom margin binds.
        assertTrue(OpenScrollGeometry.PAGE_BUTTON_Y + OpenScrollGeometry.PAGE_BUTTON_HEIGHT
                <= OpenScrollGeometry.parchmentBottom() - OpenScrollGeometry.PAD);
        assertTrue(OpenScrollGeometry.PAGE_BUTTON_Y > OpenScrollGeometry.RULE_BOTTOM_Y);
    }

    @Test
    void thePageArrowsFlankTheSheetCounterWithoutLeavingTheTextBlock() {
        assertEquals(OpenScrollGeometry.CONTENT_X, OpenScrollGeometry.pageBackwardX());
        assertEquals(OpenScrollGeometry.CONTENT_X + OpenScrollGeometry.CONTENT_WIDTH,
                OpenScrollGeometry.pageForwardX() + OpenScrollGeometry.PAGE_BUTTON_WIDTH);
        // The counter has to survive between them, so the two arrows cannot eat the whole width.
        assertTrue(OpenScrollGeometry.pageForwardX()
                > OpenScrollGeometry.pageBackwardX() + OpenScrollGeometry.PAGE_BUTTON_WIDTH);
    }

    @Test
    void theDoneButtonSitsUnderTheSheet() {
        assertEquals(OpenScrollGeometry.HEIGHT + OpenScrollGeometry.DONE_GAP
                + OpenScrollGeometry.DONE_HEIGHT, OpenScrollGeometry.totalHeight());
        // Minecraft's auto GUI scale never gives less than 240 virtual pixels of height.
        assertTrue(OpenScrollGeometry.totalHeight() <= 240);
    }

    @Test
    void sixColumnsFillTheTextBlockExactly() {
        assertEquals(OpenScrollGeometry.CONTENT_WIDTH,
                OpenScrollGeometry.COLUMNS * OpenScrollGeometry.CELL_PITCH);
    }

    @Test
    void theSearchLineCostsTwelvePixelsOfContent() {
        assertEquals(66, OpenScrollGeometry.contentY(true));
        assertEquals(54, OpenScrollGeometry.contentY(false));
    }

    @Test
    void theFooterEndsTheContentEarly() {
        assertEquals(161, OpenScrollGeometry.contentBottom(true));
        assertEquals(179, OpenScrollGeometry.contentBottom(false));
    }

    @Test
    void theFourContentHeightsMatchTheSpec() {
        assertEquals(95, OpenScrollGeometry.contentHeight(true, true));
        assertEquals(107, OpenScrollGeometry.contentHeight(false, true));
        assertEquals(113, OpenScrollGeometry.contentHeight(true, false));
        assertEquals(125, OpenScrollGeometry.contentHeight(false, false));
    }

    @Test
    void aFullSheetHoldsTwentyFourIconsAndNineTextRows() {
        assertEquals(4, OpenScrollGeometry.gridRowsPerSheet(true, true));
        assertEquals(24, OpenScrollGeometry.cellsPerSheet(true, true));
        assertEquals(9, OpenScrollGeometry.textRowsPerSheet(true, true));
    }

    @Test
    void hidingTheSearchLineBuysATextRowButNotAnIconRow() {
        assertEquals(4, OpenScrollGeometry.gridRowsPerSheet(false, true));
        assertEquals(10, OpenScrollGeometry.textRowsPerSheet(false, true));
    }

    @Test
    void aSingleSheetWithoutAFooterGetsAFifthIconRow() {
        assertEquals(5, OpenScrollGeometry.gridRowsPerSheet(true, false));
        assertEquals(30, OpenScrollGeometry.cellsPerSheet(true, false));
    }

    @Test
    void cellsAreSpacedByThePitchNotTheirSize() {
        assertEquals(27, OpenScrollGeometry.cellX(0));
        assertEquals(49, OpenScrollGeometry.cellX(1));
        // One past the last column lands exactly on the text block's right edge.
        assertEquals(OpenScrollGeometry.CONTENT_X + OpenScrollGeometry.CONTENT_WIDTH,
                OpenScrollGeometry.cellX(OpenScrollGeometry.COLUMNS));
        assertEquals(88, OpenScrollGeometry.cellY(66, 1));
    }

    @Test
    void aLoneDoneButtonKeepsItsFullWidth() {
        assertEquals(OpenScrollGeometry.DONE_WIDTH, OpenScrollGeometry.buttonWidth(false));
    }

    @Test
    void theButtonRowIsCentredOnTheScreen() {
        assertEquals(100, OpenScrollGeometry.buttonRowX(400));
    }

    @Test
    void aPairedRowIsExactlyAsWideAsTheLoneDoneButton() {
        int half = OpenScrollGeometry.buttonWidth(true);
        assertEquals(98, half);
        // Right edge of the second button has to land on the right edge of the lone Done button,
        // so nothing shifts when the take button appears.
        assertEquals(OpenScrollGeometry.buttonRowX(400) + OpenScrollGeometry.DONE_WIDTH,
                OpenScrollGeometry.secondButtonX(400) + half);
    }
}
