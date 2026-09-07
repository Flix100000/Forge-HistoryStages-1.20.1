package net.bananemdnsa.historystages.data.lock;

/**
 * The merchant itself, as the profession category is asked about it.
 *
 * <p>Two fields and not one, because a profession entry may name the levels it covers, and a
 * question that carried only the profession could not answer "librarians from apprentice up". The
 * level travels with it rather than being asked separately: asked separately, the two answers
 * would have to be combined by whoever asked, and every caller would have to remember to do it
 * the same way.
 *
 * <p>Free of Minecraft, like {@link TradeOfferSubject} and for the same reason — this is where the
 * decision is made, so this is where a unit test has to be able to reach.
 *
 * @param professionId the merchant's profession, never null here. A merchant that has none — the
 *                     wandering trader, and every merchant from another mod — is never asked
 *                     about, because no profession entry could catch it.
 * @param level        the merchant's own level, not the level an offer came from. An offer keeps
 *                     no record of which level it was unlocked at, and guessing from list order
 *                     breaks as soon as another mod appends to the same list.
 */
public record MerchantSubject(String professionId, int level) {
}
