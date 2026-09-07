package net.bananemdnsa.historystages.api.editor.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic of a trade row.
 *
 * <p>What these pin down is that the zone is the same width on every row and that its parts sit
 * in a fixed order. A row whose arrow moves depending on whether the offer had a second price is
 * exactly the list that cannot be read down a column, which is what this layout replaces.
 */
class TradeRowGeometryTest {

    @Test
    @DisplayName("the arrow sits past both price slots, so a missing second price cannot move it")
    void arrowClearsBothPriceSlots() {
        assertTrue(TradeRowGeometry.arrowX()
                        >= TradeRowGeometry.priceCountX(1) + TradeRowGeometry.COUNT_W - 1,
                "the arrow overlaps the second price slot, so it would shift without one");
    }

    @Test
    @DisplayName("the two price slots do not overlap")
    void priceSlotsAreApart() {
        int endOfFirst = TradeRowGeometry.priceCountX(0) + TradeRowGeometry.COUNT_W;
        assertTrue(endOfFirst <= TradeRowGeometry.priceIconX(1) + 1,
                "first price runs into the second");
    }

    @Test
    @DisplayName("the ware icon comes after the arrow and stays inside the zone")
    void wareIconIsLast() {
        assertTrue(TradeRowGeometry.wareIconX() > TradeRowGeometry.arrowX());
        assertTrue(TradeRowGeometry.wareIconX() + TradeRowGeometry.ICON_W
                <= TradeRowGeometry.WIDTH);
    }

    @Test
    @DisplayName("a price slot's count is drawn beside its icon, not on top of it")
    void countSitsBesideIcon() {
        for (int slot = 0; slot <= 1; slot++) {
            assertTrue(TradeRowGeometry.priceCountX(slot)
                            >= TradeRowGeometry.priceIconX(slot) + TradeRowGeometry.ICON_W,
                    "count of slot " + slot + " is drawn over its icon");
        }
    }

    @Test
    @DisplayName("the zone has a positive width and both price slots fit inside it")
    void everythingFitsInTheZone() {
        assertTrue(TradeRowGeometry.WIDTH > 0);
        assertEquals(0, TradeRowGeometry.priceIconX(0), "the first price starts at the left edge");
        assertTrue(TradeRowGeometry.priceCountX(1) + TradeRowGeometry.COUNT_W
                <= TradeRowGeometry.WIDTH);
    }
}
