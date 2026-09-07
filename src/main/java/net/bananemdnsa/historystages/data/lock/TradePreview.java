package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * One merchant offer as the editor shows it: who offers it, what it hands over, what it costs.
 *
 * <p>Exists so a pack author can point at a trade instead of guessing at an item. "Gate the
 * librarian's book trade" is the thought; "gate minecraft:enchanted_book on the buy side" is the
 * lock that results, and the second is much harder to arrive at from a list of every item in the
 * game.
 *
 * <p><strong>A sample, not a promise.</strong> Merchant recipes are rolled, and several of them
 * roll their own numbers — a price within a range, which enchantment lands on a book. So the
 * counts here are one plausible outcome rather than the one a villager in the world will show.
 * They are worth displaying anyway, because "16 emeralds" tells you which trade you are looking
 * at even when the villager in front of you says 18.
 *
 * <p>Free of Minecraft: it holds ids and numbers, so both the scan that fills it and the sorting
 * and grouping that read it can be checked by a plain unit test.
 *
 * @param professionId the profession whose recipe this is, or {@link #WANDERING_TRADER}
 * @param level        the merchant level the recipe belongs to, 1 to 5
 * @param resultId     what the offer hands over
 * @param costAId      the first price
 * @param costBId      the second price, or null — most offers have one
 */
public record TradePreview(String professionId, int level,
                           String resultId, int resultCount,
                           String costAId, int costACount,
                           String costBId, int costBCount) {

    /**
     * Stands in for the wandering trader, which has no profession.
     *
     * <p>Not a real registry id, and deliberately not one: nothing looks it up, it only groups
     * rows in the picker. A profession lock cannot catch a wandering trader in the first place.
     */
    public static final String WANDERING_TRADER = "minecraft:wandering_trader";

    /** Every item this offer mentions, both halves, without repeats. */
    public List<String> itemIds() {
        List<String> ids = new ArrayList<>(3);
        if (resultId != null) ids.add(resultId);
        if (costAId != null && !ids.contains(costAId)) ids.add(costAId);
        if (costBId != null && !ids.contains(costBId)) ids.add(costBId);
        return ids;
    }

    /**
     * What makes two rolls of the same recipe the same trade.
     *
     * <p>The counts are left out on purpose. A recipe that rolls its price would otherwise fill
     * the picker with eight rows of the same trade at eight prices, and a maintainer looking for
     * "the one that sells bookshelves" would have to read all eight to find out they are one.
     */
    public String identity() {
        return professionId + "@" + level + ":" + resultId + "<-" + costAId + "+" + costBId;
    }

    /** The lock entry that would name this trade, with no criterion on it yet. */
    public net.bananemdnsa.historystages.data.TradeOfferEntry asLockEntry() {
        return new net.bananemdnsa.historystages.data.TradeOfferEntry(
                professionId, level, resultId, costAId, costBId);
    }

    /** Whether either half of this offer is the named item. */
    public boolean mentions(String itemId) {
        return itemId != null
                && (itemId.equals(resultId) || itemId.equals(costAId) || itemId.equals(costBId));
    }
}
