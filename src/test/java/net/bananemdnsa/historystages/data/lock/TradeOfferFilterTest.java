package net.bananemdnsa.historystages.data.lock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision table behind the whole trade category, proven without a running game.
 *
 * <p>Everything here is deliberately reachable from a plain unit test: the offers arrive as
 * {@link TradeOfferFilter.Offer}, which names no Minecraft type, and no entry in these stages
 * carries a criterion. The criterion is the one part that needs a live stack and is pinned in
 * {@code gametest/TradeLockTests} instead.
 */
class TradeOfferFilterTest {

    private static final TradeOfferFilter.MerchantView LIBRARIAN_3 =
            new TradeOfferFilter.MerchantView("minecraft:librarian", 3, "minecraft:librarian");

    /** 20 paper → 1 emerald. The shape of nearly every villager trade. */
    private static final TradeOfferFilter.Offer PAPER_FOR_EMERALD = new TradeOfferFilter.Offer(
            new TradeOfferFilter.OfferItem("minecraft:emerald", null),
            new TradeOfferFilter.OfferItem("minecraft:paper", null),
            null);

    /** 1 emerald + 1 book → 1 enchanted book. Two prices, so cost B is real. */
    private static final TradeOfferFilter.Offer BOOK_FOR_EMERALD_AND_BOOK =
            new TradeOfferFilter.Offer(
                    new TradeOfferFilter.OfferItem("minecraft:enchanted_book", null),
                    new TradeOfferFilter.OfferItem("minecraft:emerald", null),
                    new TradeOfferFilter.OfferItem("minecraft:book", null));

    /** Nothing is gated by the item action {@code trade}; the stages do all the work. */
    private static final TradeOfferFilter.ItemActionGate NO_ITEM_ACTION_GATE = item -> List.of();

    /** The entry that names {@link #PAPER_FOR_EMERALD} as this librarian makes it. */
    private static TradeOfferEntry paperForEmeraldAt(String merchantKey, int level) {
        return new TradeOfferEntry(merchantKey, level,
                "minecraft:emerald", "minecraft:paper", null);
    }

    private static Map<String, StageEntry> stages(String id, StageEntry stage) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        map.put(id, stage);
        return map;
    }

    private static StageEntry gatingOffers(TradeOfferEntry... entries) {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(entries));
        return stage;
    }

    private static TradeOfferFilter.Result filter(List<TradeOfferFilter.Offer> offers,
                                                  Map<String, StageEntry> stages) {
        return TradeOfferFilter.filter(offers, LIBRARIAN_3, stages,
                StageStateView.NONE_UNLOCKED, NO_ITEM_ACTION_GATE);
    }

    @Test
    void withNoStagesNothingIsFiltered() {
        TradeOfferFilter.Result result =
                filter(List.of(PAPER_FOR_EMERALD), new LinkedHashMap<>());
        assertEquals(List.of(0), result.keptIndices());
        assertFalse(result.removedAnything());
        assertTrue(result.gatingStages().isEmpty());
    }

    // -----------------------------------------------------------------------------------------
    // The merchant-wide gates: a profession or a level takes everything out at once
    // -----------------------------------------------------------------------------------------

    @Test
    void aGatedProfessionHidesEveryOffer() {
        StageEntry stage = new StageEntry();
        stage.setTradeProfessions(List.of("minecraft:librarian"));

        TradeOfferFilter.Result result = filter(
                List.of(PAPER_FOR_EMERALD, BOOK_FOR_EMERALD_AND_BOOK), stages("bronze", stage));

        assertEquals(List.of(), result.keptIndices());
        assertTrue(result.removedAnything());
        assertEquals(List.of("bronze"), result.gatingStages());
    }

    @Test
    void anotherProfessionIsLeftAlone() {
        StageEntry stage = new StageEntry();
        stage.setTradeProfessions(List.of("minecraft:cleric"));

        assertEquals(List.of(0), filter(List.of(PAPER_FOR_EMERALD),
                stages("bronze", stage)).keptIndices());
    }

    @Test
    void aGatedLevelHidesEveryOffer() {
        StageEntry stage = new StageEntry();
        stage.setTradeLevels(List.of("3"));

        TradeOfferFilter.Result result =
                filter(List.of(PAPER_FOR_EMERALD), stages("bronze", stage));
        assertEquals(List.of(), result.keptIndices());
        assertEquals(List.of("bronze"), result.gatingStages());
    }

    @Test
    void anotherLevelIsLeftAlone() {
        StageEntry stage = new StageEntry();
        stage.setTradeLevels(List.of("4", "5"));

        assertEquals(List.of(0), filter(List.of(PAPER_FOR_EMERALD),
                stages("bronze", stage)).keptIndices());
    }

    /**
     * A merchant with no profession — the wandering trader, and anything from another mod — can
     * never be caught by a profession entry. Gating "the profession of a thing that has none"
     * would mean nothing.
     */
    @Test
    void aMerchantWithoutAProfessionIsNeverCaughtByOne() {
        StageEntry stage = new StageEntry();
        stage.setTradeProfessions(List.of("minecraft:librarian"));

        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD),
                new TradeOfferFilter.MerchantView(null, 1, "minecraft:wandering_trader"),
                stages("bronze", stage), StageStateView.NONE_UNLOCKED, NO_ITEM_ACTION_GATE);

        assertEquals(List.of(0), result.keptIndices());
    }

    // -----------------------------------------------------------------------------------------
    // Naming one trade
    // -----------------------------------------------------------------------------------------

    @Test
    void aNamedOfferIsRemoved() {
        TradeOfferFilter.Result result = filter(List.of(PAPER_FOR_EMERALD), stages("bronze",
                gatingOffers(paperForEmeraldAt("minecraft:librarian", 3))));

        assertEquals(List.of(), result.keptIndices());
        assertEquals(List.of("bronze"), result.gatingStages());
    }

    /**
     * The whole reason offers replaced items here. Gating one merchant's trade must not reach
     * another merchant making exactly the same one, or naming a trade would be no more precise
     * than naming the item it hands over.
     */
    @Test
    void theSameTradeFromAnotherMerchantSurvives() {
        TradeOfferFilter.Result result = filter(List.of(PAPER_FOR_EMERALD), stages("bronze",
                gatingOffers(paperForEmeraldAt("minecraft:cartographer", 3))));

        assertEquals(List.of(0), result.keptIndices());
        assertTrue(result.gatingStages().isEmpty());
    }

    @Test
    void theSameTradeAtAnotherLevelSurvives() {
        TradeOfferFilter.Result result = filter(List.of(PAPER_FOR_EMERALD), stages("bronze",
                gatingOffers(paperForEmeraldAt("minecraft:librarian", 1))));

        assertEquals(List.of(0), result.keptIndices());
    }

    /**
     * A farmer at level one has four recipes that all hand over an emerald. Telling them apart is
     * what the price is for, so an entry naming another one must leave this offer alone.
     */
    @Test
    void aTradeForTheSameGoodsAtAnotherPriceSurvives() {
        TradeOfferFilter.Result result = filter(List.of(PAPER_FOR_EMERALD), stages("bronze",
                gatingOffers(new TradeOfferEntry("minecraft:librarian", 3,
                        "minecraft:emerald", "minecraft:wheat", null))));

        assertEquals(List.of(0), result.keptIndices());
    }

    @Test
    void bothPricesArePartOfWhatNamesATrade() {
        TradeOfferFilter.Result gated = filter(List.of(BOOK_FOR_EMERALD_AND_BOOK),
                stages("bronze", gatingOffers(new TradeOfferEntry("minecraft:librarian", 3,
                        "minecraft:enchanted_book", "minecraft:emerald", "minecraft:book"))));
        assertEquals(List.of(), gated.keptIndices());

        TradeOfferFilter.Result missingSecondPrice = filter(List.of(BOOK_FOR_EMERALD_AND_BOOK),
                stages("bronze", gatingOffers(new TradeOfferEntry("minecraft:librarian", 3,
                        "minecraft:enchanted_book", "minecraft:emerald", null))));
        assertEquals(List.of(0), missingSecondPrice.keptIndices(),
                "an entry naming a one-price trade must not match a two-price one");
    }

    /**
     * A wandering trader has no profession, and its offers still have to be nameable — that is
     * what the stand-in key is for.
     */
    @Test
    void aMerchantWithoutAProfessionStillHasNameableOffers() {
        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD),
                new TradeOfferFilter.MerchantView(null, 1, "minecraft:wandering_trader"),
                stages("bronze", gatingOffers(
                        paperForEmeraldAt("minecraft:wandering_trader", 1))),
                StageStateView.NONE_UNLOCKED, NO_ITEM_ACTION_GATE);

        assertEquals(List.of(), result.keptIndices());
        assertEquals(List.of("bronze"), result.gatingStages());
    }

    @Test
    void theOffersThatSurviveKeepTheirOrder() {
        TradeOfferFilter.Result result = filter(
                List.of(PAPER_FOR_EMERALD, BOOK_FOR_EMERALD_AND_BOOK, PAPER_FOR_EMERALD),
                stages("bronze", gatingOffers(new TradeOfferEntry("minecraft:librarian", 3,
                        "minecraft:enchanted_book", "minecraft:emerald", "minecraft:book"))));

        assertEquals(List.of(0, 2), result.keptIndices());
        assertTrue(result.removedAnything());
    }

    @Test
    void anUnlockedStageGatesNothing() {
        Map<String, StageEntry> stages = stages("bronze",
                gatingOffers(paperForEmeraldAt("minecraft:librarian", 3)));

        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD), LIBRARIAN_3, stages,
                StageStateView.of(Set.of("bronze")), NO_ITEM_ACTION_GATE);

        assertEquals(List.of(0), result.keptIndices());
    }

    @Test
    void everyGatingStageIsReportedOnceEvenWhenSeveralOffersHitIt() {
        TradeOfferFilter.Result result = filter(
                List.of(PAPER_FOR_EMERALD, PAPER_FOR_EMERALD),
                stages("bronze", gatingOffers(paperForEmeraldAt("minecraft:librarian", 3))));

        assertEquals(List.of("bronze"), result.gatingStages());
    }

    // -----------------------------------------------------------------------------------------
    // The broad rule, which lives on ordinary item entries and reaches trades through this seam
    // -----------------------------------------------------------------------------------------

    /**
     * The item action {@code trade} rides on ordinary item, tag and mod entries, which need the
     * registry to answer for a tag. That question comes in from outside; what is pinned here is
     * that its answer removes the offer and reaches the reported stage list.
     */
    @Test
    void theItemActionGateCanRemoveAnOfferOnItsOwn() {
        TradeOfferFilter.ItemActionGate gate = item ->
                "minecraft:emerald".equals(item.id()) ? List.of("iron") : List.of();

        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD), LIBRARIAN_3, new LinkedHashMap<>(),
                StageStateView.NONE_UNLOCKED, gate);

        assertEquals(List.of(), result.keptIndices());
        assertEquals(List.of("iron"), result.gatingStages());
    }

    /**
     * No direction: an item entry narrowed to the action {@code trade} says "not traded at all",
     * so it has to reach the prices as well as the goods.
     */
    @Test
    void theItemActionGateLooksAtPricesTooNotJustTheResult() {
        TradeOfferFilter.ItemActionGate gate = item ->
                "minecraft:paper".equals(item.id()) ? List.of("iron") : List.of();

        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD), LIBRARIAN_3, new LinkedHashMap<>(),
                StageStateView.NONE_UNLOCKED, gate);

        assertEquals(List.of(), result.keptIndices());
    }

    /**
     * The two are independent. A named offer and the broad item rule can each remove a trade on
     * their own, and both stages have to be reported when both apply.
     */
    @Test
    void bothKindsOfRuleAreReported() {
        TradeOfferFilter.ItemActionGate gate = item ->
                "minecraft:paper".equals(item.id()) ? List.of("iron") : List.of();

        TradeOfferFilter.Result result = TradeOfferFilter.filter(
                List.of(PAPER_FOR_EMERALD), LIBRARIAN_3,
                stages("bronze", gatingOffers(paperForEmeraldAt("minecraft:librarian", 3))),
                StageStateView.NONE_UNLOCKED, gate);

        assertEquals(List.of(), result.keptIndices());
        assertEquals(Set.of("bronze", "iron"), Set.copyOf(result.gatingStages()));
    }
}
