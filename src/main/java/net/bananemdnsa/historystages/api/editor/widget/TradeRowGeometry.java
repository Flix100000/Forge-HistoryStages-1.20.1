package net.bananemdnsa.historystages.api.editor.widget;

/**
 * Where the parts of a trade row sit, measured from the left edge of the row's painted zone.
 *
 * <p>Reading order follows the merchant window: what it costs, an arrow, what you get. The zone is
 * one fixed width for every row, and the second price keeps its place even when an offer has only
 * one. Otherwise the arrow, and with it the name behind it, would start at a different x on every
 * row. A list whose columns do not line up has to be read line by line, and that is the thing this
 * layout replaces.
 *
 * <p>Plain integers and no Minecraft, so the arithmetic can be checked without a client.
 */
public final class TradeRowGeometry {

    /** Rendered size of an item icon in a row. */
    public static final int ICON_W = 12;

    /** Reserved for a stack count beside an icon. Merchant counts never exceed two digits. */
    public static final int COUNT_W = 13;

    private static final int GAP = 2;
    private static final int ARROW_W = 7;

    /** Width of one price slot: its icon plus room for the count beside it. */
    private static final int PRICE_SLOT_W = ICON_W + COUNT_W;

    /** Total width of the painted zone. The row's text begins after it. */
    public static final int WIDTH = 2 * PRICE_SLOT_W + GAP + ARROW_W + GAP + ICON_W + GAP;

    private TradeRowGeometry() {
    }

    /** X of the icon of price {@code slot}, 0 or 1. */
    public static int priceIconX(int slot) {
        return slot * PRICE_SLOT_W;
    }

    /** X of the count drawn beside price {@code slot}. */
    public static int priceCountX(int slot) {
        return priceIconX(slot) + ICON_W;
    }

    /** X of the arrow between the prices and the ware. */
    public static int arrowX() {
        return 2 * PRICE_SLOT_W + GAP;
    }

    /** X of the icon of the item the merchant hands over. */
    public static int wareIconX() {
        return arrowX() + ARROW_W + GAP;
    }
}
