package net.bananemdnsa.historystages.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PedestalLayoutTest {

    @Test
    void tierIsClampedToTheFourSheetsThatExist() {
        assertEquals(1, PedestalLayout.clampTier(1));
        assertEquals(4, PedestalLayout.clampTier(4));
        // A pedestal reporting something outside 1-4 must still pick a real sheet
        // rather than crash the screen with a missing texture.
        assertEquals(1, PedestalLayout.clampTier(0));
        assertEquals(1, PedestalLayout.clampTier(-3));
        assertEquals(4, PedestalLayout.clampTier(9));
    }

    @Test
    void anEmptyBarDrawsNothing() {
        assertEquals(0, PedestalLayout.barFillWidth(0, 200));
    }

    @Test
    void aFullBarFillsTheWholeWindow() {
        assertEquals(PedestalLayout.BAR_W, PedestalLayout.barFillWidth(200, 200));
    }

    @Test
    void barFillScalesWithProgress() {
        assertEquals(PedestalLayout.BAR_W / 2, PedestalLayout.barFillWidth(100, 200));
    }

    @Test
    void barFillNeverOverflowsTheWindow() {
        // Progress can briefly exceed max while the finish delay runs out.
        assertEquals(PedestalLayout.BAR_W, PedestalLayout.barFillWidth(500, 200));
    }

    @Test
    void aZeroMaxDoesNotDivideByZero() {
        assertEquals(0, PedestalLayout.barFillWidth(50, 0));
    }
}
