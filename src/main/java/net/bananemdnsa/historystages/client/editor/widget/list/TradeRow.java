package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.data.lock.TradePreview;
import org.jetbrains.annotations.Nullable;

/**
 * One line in the trade picker: either an offer somebody can choose, or the caption above a
 * merchant's offers.
 *
 * <p>Captions travel as rows rather than as a layer of their own because the picker underneath
 * measures everything in rows of one height — drawing, hit tests, the scrollbar, the drag. A
 * caption with a height of its own would have to be accounted for in every one of those places,
 * in a class every picker in the editor hangs from. As a row that is merely drawn differently,
 * none of that arithmetic changes.
 *
 * <p>Free of Minecraft, so the rule that a caption cannot be picked is provable without a client.
 */
public record TradeRow(@Nullable TradePreview offer, @Nullable String caption) {

    public static TradeRow of(TradePreview offer) {
        return new TradeRow(offer, null);
    }

    public static TradeRow header(String caption) {
        return new TradeRow(null, caption);
    }

    public boolean isHeader() {
        return offer == null;
    }

    /**
     * What picking this row hands back: the trade in the form a lock entry is written in.
     *
     * <p><strong>Not {@code TradePreview.identity()}.</strong> Two types here have a method by
     * that name and they are not interchangeable. The preview's groups rows in this picker and is
     * readable; this one is fed straight to {@code TradeOfferEntry.decode}, which wants the five
     * NUL-joined fields it wrote itself and answers null to anything else. The caller of the
     * picker gives up silently on null, so handing back the wrong one shows up as "Add does
     * nothing" and in no log — hence the name saying which of the two this is.
     *
     * @throws IllegalStateException on a caption. Every caller guards against captions before it
     *         gets here; throwing means a new one forgot to, and an empty string returned quietly
     *         would have been written into somebody's stage file instead.
     */
    public String lockIdentity() {
        if (offer == null) throw new IllegalStateException("a caption row has no trade to pick");
        return offer.asLockEntry().identity();
    }
}
