package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Recipe cards are as tall as their contents — a furnace recipe is one row, a crafting recipe
 * three — so positions are a running sum rather than a multiplication. Everything that used to
 * be "index times row height" has an edge here instead, which is what these tests are for.
 */
class CardStackGeometryTest {

    /** Crafting, furnace, furnace, smithing — the mix a real item produces. */
    private static final List<Integer> HEIGHTS = List.of(62, 26, 26, 34);

    @Test
    void offsetsAreTheRunningSumOfWhatCameBefore() {
        assertEquals(0, CardStackGeometry.offsetOf(HEIGHTS, 0));
        assertEquals(62, CardStackGeometry.offsetOf(HEIGHTS, 1));
        assertEquals(88, CardStackGeometry.offsetOf(HEIGHTS, 2));
        assertEquals(114, CardStackGeometry.offsetOf(HEIGHTS, 3));
    }

    @Test
    void theTotalIsTheSumOfEveryCard() {
        assertEquals(148, CardStackGeometry.totalHeight(HEIGHTS));
        assertEquals(0, CardStackGeometry.totalHeight(List.of()));
    }

    @Test
    void aStackShorterThanItsWindowDoesNotScroll() {
        assertEquals(0, CardStackGeometry.maxScroll(HEIGHTS, 200));
        assertEquals(0, CardStackGeometry.maxScroll(List.of(), 120));
    }

    @Test
    void maxScrollLeavesTheLastCardFlushWithTheBottom() {
        // 148 of content in a 120 window leaves 28px to scroll.
        assertEquals(28, CardStackGeometry.maxScroll(HEIGHTS, 120));
    }

    @Test
    void oneCardTallerThanTheWindowIsStillFullyReachable() {
        // A modded recipe with many ingredients can out-grow the panel. Scrolling has to reach
        // its bottom edge, or the last ingredient row is unreachable.
        assertEquals(80, CardStackGeometry.maxScroll(List.of(200), 120));
    }

    @Test
    void theHitTestFindsTheCardUnderTheCursor() {
        assertEquals(0, CardStackGeometry.indexAt(HEIGHTS, 0, 0));
        assertEquals(0, CardStackGeometry.indexAt(HEIGHTS, 0, 61));
        assertEquals(1, CardStackGeometry.indexAt(HEIGHTS, 0, 62));
        assertEquals(3, CardStackGeometry.indexAt(HEIGHTS, 0, 147));
    }

    @Test
    void aBoundaryPixelBelongsToTheCardBelowIt() {
        // The seam between two cards. One pixel wrong here and every card is off by one at
        // its top edge, which is exactly where people click.
        assertEquals(1, CardStackGeometry.indexAt(HEIGHTS, 0, 62));
        assertEquals(2, CardStackGeometry.indexAt(HEIGHTS, 0, 88));
    }

    @Test
    void theHitTestAccountsForScroll() {
        // Scrolled down 62px, the top of the window now shows card 1.
        assertEquals(1, CardStackGeometry.indexAt(HEIGHTS, 62, 0));
        assertEquals(2, CardStackGeometry.indexAt(HEIGHTS, 62, 26));
    }

    @Test
    void thereIsNoCardPastTheEndOrBeforeTheStart() {
        assertEquals(-1, CardStackGeometry.indexAt(HEIGHTS, 0, 148));
        assertEquals(-1, CardStackGeometry.indexAt(HEIGHTS, 0, -1));
        assertEquals(-1, CardStackGeometry.indexAt(List.of(), 0, 0));
    }

    @Test
    void theVisibleWindowIncludesCardsOnlyPartlyOnScreen() {
        // Scrolled 40px into a 120px window: card 0 is half off the top and card 3 starts
        // 114 in, i.e. 74 into the window — both must still be drawn.
        assertEquals(0, CardStackGeometry.firstVisible(HEIGHTS, 40));
        assertEquals(4, CardStackGeometry.endVisible(HEIGHTS, 40, 120));
    }

    @Test
    void theWindowStartsAtTheCardTheScrollLandsIn() {
        assertEquals(1, CardStackGeometry.firstVisible(HEIGHTS, 62));
        assertEquals(2, CardStackGeometry.firstVisible(HEIGHTS, 90));
    }

    @Test
    void anEmptyStackHasAnEmptyWindow() {
        assertEquals(0, CardStackGeometry.firstVisible(List.of(), 0));
        assertEquals(0, CardStackGeometry.endVisible(List.of(), 0, 120));
    }

    @Test
    void scrollIsClampedToBothEnds() {
        assertEquals(0, CardStackGeometry.clampScroll(-10, HEIGHTS, 120));
        assertEquals(28, CardStackGeometry.clampScroll(500, HEIGHTS, 120));
        assertEquals(15, CardStackGeometry.clampScroll(15, HEIGHTS, 120));
    }
}
