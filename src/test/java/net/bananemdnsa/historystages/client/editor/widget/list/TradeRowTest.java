package net.bananemdnsa.historystages.client.editor.widget.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A picker line is either an offer or a caption, and the two must not be confusable.
 *
 * <p>The one that matters is that a caption cannot be picked. Everything above this in the picker
 * treats a row as something a click selects; a caption that quietly answered with an empty string
 * would put an empty entry into somebody's stage file.
 */
class TradeRowTest {

    private static TradePreview offer() {
        return new TradePreview("minecraft:librarian", 2,
                "minecraft:bookshelf", 1, "minecraft:emerald", 10, "minecraft:book", 3);
    }

    @Test
    @DisplayName("an offer row carries its offer and is not a caption")
    void offerRow() {
        TradeRow row = TradeRow.of(offer());
        assertFalse(row.isHeader());
        assertEquals("minecraft:librarian", row.offer().professionId());
        assertNull(row.caption());
    }

    @Test
    @DisplayName("a caption row carries a caption and no offer")
    void headerRow() {
        TradeRow row = TradeRow.header("Librarian");
        assertTrue(row.isHeader());
        assertEquals("Librarian", row.caption());
        assertNull(row.offer());
    }

    @Test
    @DisplayName("a caption has nothing to pick, so asking for its value is a mistake")
    void headerHasNoValue() {
        assertThrows(IllegalStateException.class, () -> TradeRow.header("Librarian").lockIdentity());
    }

    /**
     * The contract the picker actually has to keep.
     *
     * <p>Whatever a row hands back is passed straight to {@code TradeOfferEntry.decode}, and that
     * returns null for a string in any other shape — on which the caller gives up without a word,
     * so a wrong format shows up as "Add does nothing" and nowhere else.
     *
     * <p>Written against decode rather than against another identity method on purpose. Two types
     * in this area both have one, they are not interchangeable, and a test that compares a row to
     * the same method the row calls proves only that the code is the code.
     */
    @Test
    @DisplayName("what a row hands back can be decoded back into the trade it came from")
    void offerValueDecodesBackIntoTheSameTrade() {
        TradeOfferEntry decoded = TradeOfferEntry.decode(TradeRow.of(offer()).lockIdentity());

        assertNotNull(decoded, "the picker handed back something decode() does not recognise");
        assertEquals("minecraft:librarian", decoded.merchantKey());
        assertEquals(2, decoded.level());
        assertEquals("minecraft:bookshelf", decoded.givesId());
        assertEquals("minecraft:emerald", decoded.takesAId());
        assertEquals("minecraft:book", decoded.takesBId());
    }

    @Test
    @DisplayName("an offer with only one price also survives the round trip")
    void singlePriceOfferDecodesBack() {
        TradePreview single = new TradePreview("minecraft:farmer", 1,
                "minecraft:emerald", 1, "minecraft:wheat", 20, null, 0);

        TradeOfferEntry decoded = TradeOfferEntry.decode(TradeRow.of(single).lockIdentity());

        assertNotNull(decoded);
        assertEquals("minecraft:wheat", decoded.takesAId());
        assertNull(decoded.takesBId());
    }
}
