package net.bananemdnsa.historystages.data.lock;

/**
 * One merchant offer, as the offer category is asked about it.
 *
 * <p>Everything needed to tell one trade from another: who is making it, at what level, and which
 * items change hands. Free of Minecraft — like {@link MerchantSubject}, because this is where the
 * decision is made and a unit test has to be able to reach it.
 *
 * @param merchantKey a villager profession id, or the stand-in for a merchant that has none
 * @param level       the merchant's own level, 1 for anything that has no levels
 * @param givesStack  the live stack the offer hands over, or null on a path that has only ids.
 *                    Typed as {@code Object} to keep this file free of Minecraft; only an entry
 *                    that actually carries a criterion ever looks at it
 */
public record TradeOfferSubject(String merchantKey, int level, String givesId,
                                String takesAId, String takesBId, Object givesStack) {

    /** For the paths and the tests that have no stack — an entry with a criterion won't match. */
    public TradeOfferSubject(String merchantKey, int level, String givesId,
                             String takesAId, String takesBId) {
        this(merchantKey, level, givesId, takesAId, takesBId, null);
    }
}
