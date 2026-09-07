package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;

/**
 * Decides which of a merchant's offers one viewer may see.
 *
 * <p>This is the whole trade category. Both seams ask it: the one where the trade window opens,
 * which shows the player a shortened list, and the one where payment is placed, which refuses to
 * produce a result the player was never shown. Two seams, one answer, so they cannot disagree.
 *
 * <p><strong>Free of Minecraft on purpose.</strong> The offers arrive as {@link Offer}, a plain
 * record of ids, and everything interesting — which side of a trade an entry means, whether a
 * profession or level takes the whole merchant out, which stages to name in the message — is
 * decided here, where a unit test can reach it. Two things are handed in from outside because
 * they genuinely need a running game: the stack behind an entry's criterion, carried opaquely as
 * {@code Object}, and the {@link ItemActionGate}, which answers the item action {@code trade} and
 * needs the registry to resolve a tag.
 *
 * <p>Three questions, not one. A profession or a level takes the whole merchant out; a named
 * offer takes one trade out; the item action {@code trade} takes an item out of trading wherever
 * it appears. They are asked in that order because the first is the cheapest and the most likely
 * to end the matter.
 *
 * <p>One scope per call. The caller asks once with the global stages and once with the viewer's
 * individual ones, and joins — the same shape every other lock question in this mod has.
 */
public final class TradeOfferFilter {

    private TradeOfferFilter() {}

    /** One half of an offer: what it is, and the live stack when there is one. */
    public record OfferItem(String id, Object stack) {}

    /**
     * One merchant offer, reduced to what a lock question needs.
     *
     * @param costB the second price, or null. Most trades have one price; a few have two.
     */
    public record Offer(OfferItem result, OfferItem costA, OfferItem costB) {}

    /**
     * The merchant being asked about.
     *
     * @param professionId null for anything that has no profession — the wandering trader, and
     *                     every merchant from another mod. Such a merchant can never be caught by
     *                     a profession entry, only by an item one.
     * @param level        the merchant's own level. Not the level an offer came from: an offer
     *                     carries no record of that, and guessing it from list order would break
     *                     the moment another mod appends to the same list.
     */
    /**
     * @param merchantKey who this is, for naming one of its offers: the profession id for a
     *                    villager, and a stand-in for anything without a profession — the
     *                    wandering trader and every merchant another mod writes. Never null,
     *                    which is what lets a single offer be named for any merchant at all.
     */
    public record MerchantView(String professionId, int level, String merchantKey) {}

    /**
     * Answers the item action {@code trade} for one half of an offer.
     *
     * <p>Handed in rather than resolved here because it rides on ordinary item, tag and mod
     * entries, and deciding whether a stack is in a tag needs the registry.
     */
    @FunctionalInterface
    public interface ItemActionGate {
        /** The stages gating this item for trading, or an empty list. */
        List<String> gatingStagesForTrading(OfferItem item);
    }

    /**
     * What survived, and who is responsible for what did not.
     *
     * @param keptIndices  indices into the list that was passed in, in their original order. The
     *                     caller maps them back onto the real offers; handing back indices rather
     *                     than rebuilt offers is what keeps this class free of Minecraft.
     * @param gatingStages every stage that removed something, in the order the stages are held,
     *                     each named once however many offers it took out
     */
    public record Result(List<Integer> keptIndices, List<String> gatingStages,
                         int offeredCount) {

        /**
         * Whether anything was taken away.
         *
         * <p>The seam needs this to tell "empty because the player may not see any of it" from
         * "empty because this merchant genuinely has nothing" — only the first deserves a message.
         */
        public boolean removedAnything() {
            return keptIndices.size() < offeredCount;
        }
    }

    /**
     * Filters {@code offers} for one viewer in one scope.
     *
     * <p>Profession and level are checked first and take the whole merchant out, so in the common
     * "this player may not deal with librarians yet" case not a single offer is examined.
     */
    public static Result filter(List<Offer> offers, MerchantView merchant,
                                Map<String, StageEntry> stages, StageStateView state,
                                ItemActionGate itemActionGate) {
        Set<String> gating = new LinkedHashSet<>();

        List<String> hidesTheMerchant = merchantWide(merchant, stages, state);
        if (!hidesTheMerchant.isEmpty()) {
            return new Result(List.of(), List.copyOf(hidesTheMerchant), offers.size());
        }

        List<Integer> kept = new ArrayList<>(offers.size());
        for (int i = 0; i < offers.size(); i++) {
            List<String> gatingThisOffer = gatingThisOffer(offers.get(i), merchant, stages, state,
                    itemActionGate);
            if (gatingThisOffer.isEmpty()) {
                kept.add(i);
            } else {
                gating.addAll(gatingThisOffer);
            }
        }
        return new Result(List.copyOf(kept), List.copyOf(gating), offers.size());
    }

    /** Whether one offer is gated at all, for the seam that only ever asks about one. */
    public static List<String> gatingStagesFor(Offer offer, MerchantView merchant,
                                               Map<String, StageEntry> stages,
                                               StageStateView state,
                                               ItemActionGate itemActionGate) {
        List<String> hidesTheMerchant = merchantWide(merchant, stages, state);
        if (!hidesTheMerchant.isEmpty()) return hidesTheMerchant;
        return gatingThisOffer(offer, merchant, stages, state, itemActionGate);
    }

    /**
     * The stages that take this merchant out whole — by profession or by its own level.
     *
     * <p>Two gates, and either one on its own is enough. They are not the same question asked
     * twice: the level list gates a level for <em>every</em> profession, which is what a pack
     * wants for "until the Bronze Age there are only novices", while a profession entry may name
     * the levels it covers and so says "librarians, but only from apprentice up". Both have to
     * exist, because neither can express the other without repeating itself fifteen times.
     */
    private static List<String> merchantWide(MerchantView merchant,
                                             Map<String, StageEntry> stages,
                                             StageStateView state) {
        Set<String> gating = new LinkedHashSet<>();
        if (merchant.professionId() != null) {
            gating.addAll(CategoryLockResolver.missingStages(
                    category("historystages:trade_professions"),
                    new MerchantSubject(merchant.professionId(), merchant.level()),
                    stages, state));
        }
        gating.addAll(CategoryLockResolver.missingStages(
                category("historystages:trade_levels"), String.valueOf(merchant.level()),
                stages, state));
        return List.copyOf(gating);
    }

    /**
     * The stages holding back one offer: the ones that name it, plus the ones that gate any item
     * it touches for trading at all.
     *
     * <p>Two questions on purpose, and they are not the same one twice. Naming an offer is
     * surgical — this merchant, this level, these goods. The item action is broad — nobody trades
     * with iron ingots, wherever they turn up. A pack wants both, and neither can express the
     * other without listing every trade in the game.
     */
    private static List<String> gatingThisOffer(Offer offer, MerchantView merchant,
                                                Map<String, StageEntry> stages,
                                                StageStateView state,
                                                ItemActionGate itemActionGate) {
        Set<String> gating = new LinkedHashSet<>(CategoryLockResolver.missingStages(
                category("historystages:trades"), subjectOf(offer, merchant), stages, state));

        collectItemAction(gating, offer.result(), itemActionGate);
        collectItemAction(gating, offer.costA(), itemActionGate);
        collectItemAction(gating, offer.costB(), itemActionGate);

        return List.copyOf(gating);
    }

    private static TradeOfferSubject subjectOf(Offer offer, MerchantView merchant) {
        return new TradeOfferSubject(merchant.merchantKey(), merchant.level(),
                idOf(offer.result()), idOf(offer.costA()), idOf(offer.costB()),
                offer.result() == null ? null : offer.result().stack());
    }

    private static String idOf(OfferItem item) {
        return item == null ? null : item.id();
    }

    private static void collectItemAction(Set<String> gating, OfferItem item,
                                          ItemActionGate itemActionGate) {
        if (item == null || item.id() == null) return;
        gating.addAll(itemActionGate.gatingStagesForTrading(item));
    }

    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        if (found == null) {
            throw new IllegalStateException("built-in category missing: " + id);
        }
        return found;
    }
}
