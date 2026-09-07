package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import net.bananemdnsa.historystages.api.lock.LockActions;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.lock.MerchantSubject;
import net.bananemdnsa.historystages.data.lock.TradeOfferSubject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything about the three trade categories that can be pinned without a live registry.
 *
 * <p>The matching itself needs a real {@code ItemStack} — an offer's result carries components a
 * criterion has to be settled against — and is pinned in {@code gametest/TradeLockTests}.
 */
class TradeCategoryTest {

    private static LockCategory<?> byId(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        assertNotNull(found, "no built-in category registered under " + id);
        return found;
    }

    @SuppressWarnings("unchecked")
    private static LockCategory<TradeOfferEntry> offers() {
        return (LockCategory<TradeOfferEntry>) byId("historystages:trades");
    }

    /** The librarian's paper-for-emerald trade, which every offer test here is about. */
    private static TradeOfferEntry paperForEmerald() {
        return new TradeOfferEntry("minecraft:librarian", 1,
                "minecraft:emerald", "minecraft:paper", null);
    }

    private static TradeOfferSubject asSubject(TradeOfferEntry entry) {
        return new TradeOfferSubject(entry.merchantKey(), entry.level(), entry.givesId(),
                entry.takesAId(), entry.takesBId());
    }

    @SuppressWarnings("unchecked")
    private static LockCategory<TradeProfessionEntry> professions() {
        return (LockCategory<TradeProfessionEntry>) byId("historystages:trade_professions");
    }

    @SuppressWarnings("unchecked")
    private static LockCategory<String> levels() {
        return (LockCategory<String>) byId("historystages:trade_levels");
    }

    @Test
    void allThreeAreRegistered() {
        assertEquals("historystages:trades", offers().id());
        assertEquals("historystages:trade_professions", professions().id());
        assertEquals("historystages:trade_levels", levels().id());
    }

    @Test
    void theOfferListReadsAndWritesTheStageField() {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(paperForEmerald()));
        assertEquals(1, offers().read(stage).size());

        offers().write(stage, List.of(new TradeOfferEntry("minecraft:cleric", 2,
                "minecraft:redstone", "minecraft:emerald", null)));
        assertEquals("minecraft:redstone", stage.getTradeOffers().get(0).givesId());
    }

    /**
     * The whole reason offers replaced items here: everything about the merchant is part of the
     * match, so naming one trade cannot reach another that happens to move the same goods.
     */
    @Test
    void anOfferEntryNamesExactlyOneTrade() {
        TradeOfferEntry entry = paperForEmerald();

        assertTrue(offers().matches(entry, asSubject(entry)));
        assertFalse(offers().matches(entry, new TradeOfferSubject("minecraft:cartographer", 1,
                "minecraft:emerald", "minecraft:paper", null)), "another merchant");
        assertFalse(offers().matches(entry, new TradeOfferSubject("minecraft:librarian", 3,
                "minecraft:emerald", "minecraft:paper", null)), "another level");
        assertFalse(offers().matches(entry, new TradeOfferSubject("minecraft:librarian", 1,
                "minecraft:emerald", "minecraft:wheat", null)), "another price");
        assertFalse(offers().matches(entry, new TradeOfferSubject("minecraft:librarian", 1,
                "minecraft:book", "minecraft:paper", null)), "other goods");
    }

    /** The category answers about a trade now, so an item id on its own is not a question. */
    @Test
    void aBareItemIdIsNoLongerAnOffer() {
        assertFalse(offers().matches(paperForEmerald(), "minecraft:emerald"));
    }

    @Test
    void theProfessionListReadsAndWritesTheStageField() {
        StageEntry stage = new StageEntry();
        professions().write(stage, List.of(new TradeProfessionEntry("minecraft:librarian")));
        assertEquals(List.of("minecraft:librarian"), stage.getTradeProfessions());
        assertEquals("minecraft:librarian", professions().read(stage).get(0).getId());
    }

    @Test
    void theLevelListReadsAndWritesTheStageField() {
        StageEntry stage = new StageEntry();
        levels().write(stage, List.of("4", "5"));
        assertEquals(List.of("4", "5"), stage.getTradeLevels());
        assertEquals(List.of("4", "5"), levels().read(stage));
    }

    /**
     * None of the three narrows by action. An offer entry names one trade and a player may either
     * make it or not; a profession or level entry hides the whole merchant. There is no half of
     * any of that to narrow to, so all three keep the default vocabulary and are never asked.
     */
    @Test
    void allThreeAreAllOrNothing() {
        assertEquals(LockActions.ITEM, offers().lockActions());
        assertEquals(LockActions.ITEM, professions().lockActions());
        assertEquals(LockActions.ITEM, levels().lockActions());
    }

    /**
     * The whole reason the filter sits where the trade window opens rather than where the
     * merchant draws its offers: a player is standing there, so an individual stage has something
     * to answer for. Spawn locks are the counter-example and are global-only.
     */
    @Test
    void allThreeWorkPerPlayerAsWellAsGlobally() {
        for (LockCategory<?> category : List.of(offers(), professions(), levels())) {
            assertTrue(category.supportedScopes().contains(StageScope.GLOBAL), category.id());
            assertTrue(category.supportedScopes().contains(StageScope.INDIVIDUAL), category.id());
        }
    }

    @Test
    void dualPhaseSeesTheSameIdsFromBothSides() {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(new TradeOfferEntry("minecraft:librarian", 1,
                "minecraft:diamond", "minecraft:emerald", null)));
        stage.setTradeProfessions(List.of("minecraft:librarian"));
        stage.setTradeLevels(List.of("4"));

        assertEquals(List.of("minecraft:diamond"), offers().globalDualPhaseIds(stage));
        assertEquals(List.of("minecraft:diamond"), offers().individualDualPhaseIds(stage));
        assertEquals(List.of("minecraft:librarian"), professions().globalDualPhaseIds(stage));
        assertEquals(List.of("minecraft:librarian"), professions().individualDualPhaseIds(stage));
        assertEquals(List.of("4"), levels().globalDualPhaseIds(stage));
        assertEquals(List.of("4"), levels().individualDualPhaseIds(stage));
    }

    @Test
    void eachOneNamesItselfInTheDualPhaseMessage() {
        assertEquals("trade", offers().dualPhaseLabel());
        assertEquals("trade profession", professions().dualPhaseLabel());
        assertEquals("trade level", levels().dualPhaseLabel());
    }

    /**
     * {@code LockRelevanceIndex} narrows {@code isItemActionLocked}. A trade question does not go
     * through that path at all — it is asked through {@code CategoryLockResolver} directly — so
     * filing keys here would build an index nothing ever reads.
     *
     * <p>Stated as a test rather than a comment because the fluid category needed the opposite
     * and someone will reasonably wonder why this one does not.
     */
    @Test
    void noneOfThemFileIntoTheItemRelevanceIndex() {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(new TradeOfferEntry("minecraft:librarian", 1,
                "minecraft:diamond", "minecraft:emerald", null)));
        stage.setTradeProfessions(List.of("minecraft:librarian"));
        stage.setTradeLevels(List.of("4"));

        for (LockCategory<?> category : List.of(offers(), professions(), levels())) {
            assertEquals(List.of(), category.indexKeys(stage), category.id());
        }
    }

    /** A level entry is its own id, the way dimensions and biomes are. */
    @Test
    void aLevelEntryGatesTheNumberItNames() {
        assertTrue(levels().matches("4", "4"));
        assertFalse(levels().matches("4", "5"));
    }

    /** A bare profession, which is what a stage file has always been able to say. */
    @Test
    void aProfessionWithNoLevelsGatesEveryLevel() {
        TradeProfessionEntry entry = new TradeProfessionEntry("minecraft:librarian");
        for (int level = 1; level <= 5; level++) {
            assertTrue(professions().matches(entry, new MerchantSubject("minecraft:librarian", level)),
                    "level " + level);
        }
    }

    @Test
    void aProfessionEntryDoesNotGateAnotherProfession() {
        TradeProfessionEntry entry = new TradeProfessionEntry("minecraft:librarian");
        assertFalse(professions().matches(entry, new MerchantSubject("minecraft:cleric", 1)));
    }

    /**
     * The whole point of the level narrowing: "librarians, but only from apprentice up" without
     * taking every other profession's apprentices with it.
     */
    @Test
    void aNarrowedProfessionGatesOnlyTheLevelsItNames() {
        TradeProfessionEntry entry =
                new TradeProfessionEntry("minecraft:librarian", List.of("4", "5"));

        assertFalse(professions().matches(entry, new MerchantSubject("minecraft:librarian", 3)));
        assertTrue(professions().matches(entry, new MerchantSubject("minecraft:librarian", 4)));
        assertTrue(professions().matches(entry, new MerchantSubject("minecraft:librarian", 5)));
        assertFalse(professions().matches(entry, new MerchantSubject("minecraft:cleric", 4)),
                "narrowing by level must not widen the entry to other professions");
    }

    /**
     * A narrowing that names every level is the same as no narrowing. Worth pinning because the
     * editor writes exactly that when somebody switches all five on and then off again.
     */
    @Test
    void namingEveryLevelIsTheSameAsNamingNone() {
        TradeProfessionEntry all =
                new TradeProfessionEntry("minecraft:librarian", TradeProfessionEntry.ALL_LEVELS);
        for (int level = 1; level <= 5; level++) {
            assertTrue(professions().matches(all, new MerchantSubject("minecraft:librarian", level)));
        }
    }

    /** The category answers about a merchant now, so an id on its own is not a question. */
    @Test
    void aBareStringIsNoLongerAMerchant() {
        assertFalse(professions().matches(new TradeProfessionEntry("minecraft:librarian"),
                "minecraft:librarian"));
    }
}
