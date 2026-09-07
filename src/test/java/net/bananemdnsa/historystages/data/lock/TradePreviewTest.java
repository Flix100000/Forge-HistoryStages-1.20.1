package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merchant recipes are run several times over, because some of them roll their own numbers. What
 * decides whether two runs are the same trade is therefore the difference between a readable list
 * and eight rows of "16 emeralds → book", "18 emeralds → book", and so on.
 */
class TradePreviewTest {

    private static TradePreview offer(String profession, int level, String result,
                                      int resultCount, String costA, int costACount) {
        return new TradePreview(profession, level, result, resultCount, costA, costACount, null, 0);
    }

    @Test
    void twoRunsOfOneRecipeAreTheSameTradeAtDifferentPrices() {
        TradePreview cheap = offer("minecraft:librarian", 2, "minecraft:bookshelf", 1,
                "minecraft:emerald", 9);
        TradePreview dear = offer("minecraft:librarian", 2, "minecraft:bookshelf", 1,
                "minecraft:emerald", 12);

        assertEquals(cheap.identity(), dear.identity(),
                "a recipe that rolls its price would otherwise fill the picker with the same"
                        + " trade over and over, and nobody could tell they were one");
    }

    @Test
    void thesameGoodsFromAnotherMerchantAreAnotherTrade() {
        TradePreview librarian = offer("minecraft:librarian", 1, "minecraft:emerald", 1,
                "minecraft:paper", 24);
        TradePreview cartographer = offer("minecraft:cartographer", 1, "minecraft:emerald", 1,
                "minecraft:paper", 24);

        assertNotEquals(librarian.identity(), cartographer.identity(),
                "who offers it is half of what the maintainer is looking for");
    }

    @Test
    void theSameGoodsAtAnotherLevelAreAnotherTrade() {
        assertNotEquals(
                offer("minecraft:librarian", 1, "minecraft:emerald", 1, "minecraft:paper", 24).identity(),
                offer("minecraft:librarian", 3, "minecraft:emerald", 1, "minecraft:paper", 24).identity(),
                "gating one level of a profession is a thing this editor can express, so the"
                        + " levels must not collapse into one row");
    }

    @Test
    void bothHalvesAreListedAndNeitherIsRepeated() {
        TradePreview trade = new TradePreview("minecraft:armorer", 1, "minecraft:shield", 1,
                "minecraft:emerald", 5, "minecraft:iron_ingot", 3);

        assertEquals(List.of("minecraft:shield", "minecraft:emerald", "minecraft:iron_ingot"),
                trade.itemIds());
    }

    @Test
    void anItemOnBothSidesIsListedOnce() {
        TradePreview trade = new TradePreview("minecraft:farmer", 1, "minecraft:emerald", 1,
                "minecraft:emerald", 3, null, 0);

        assertEquals(List.of("minecraft:emerald"), trade.itemIds(),
                "the picker's goods filter reads this, and a repeat would count nothing twice"
                        + " but still look like a bug to whoever next reads it");
    }

    @Test
    void anOfferWithOnePriceListsTwoItems() {
        assertEquals(List.of("minecraft:emerald", "minecraft:paper"),
                offer("minecraft:librarian", 1, "minecraft:emerald", 1, "minecraft:paper", 24)
                        .itemIds());
    }

    @Test
    void aTradeKnowsWhetherItTouchesAnItem() {
        TradePreview trade = new TradePreview("minecraft:armorer", 1, "minecraft:shield", 1,
                "minecraft:emerald", 5, "minecraft:iron_ingot", 3);

        assertTrue(trade.mentions("minecraft:shield"));
        assertTrue(trade.mentions("minecraft:iron_ingot"), "the second price counts too");
        assertFalse(trade.mentions("minecraft:diamond"));
        assertFalse(trade.mentions(null));
    }
}
