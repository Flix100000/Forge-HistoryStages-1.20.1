package net.bananemdnsa.historystages.screen;

/**
 * Where everything sits on the open scroll document screen.
 *
 * <p>The artwork is authored on a 256x256 sheet, but only x35-220 / y19-236 is painted. The panel
 * is that drawn region, and the sheet is blitted at a negative offset so Minecraft centres the
 * screen on what the player actually sees — the pedestal screen sat 23px too high until it was
 * done this way.
 *
 * <p>Every constant is panel-relative (sheet coordinate minus the sheet origin). Deliberately free
 * of Minecraft types so the arithmetic is unit-testable.
 *
 * <p>The overview page has no constants here on purpose: its blocks flow from the top of the
 * content band, one under the other, so their positions depend on the config's block order and
 * cannot be written down in advance.
 */
public final class OpenScrollGeometry {

    private OpenScrollGeometry() {}

    // --- the sheet ---

    /** Top-left of the drawn region inside the 256x256 sheet. */
    public static final int SHEET_X = 35;
    public static final int SHEET_Y = 19;

    /** Panel size — the drawn region, not the sheet. */
    public static final int WIDTH = 186;
    public static final int HEIGHT = 218;

    /** Full sheet size, for the single blit. */
    public static final int SHEET_SIZE = 256;

    // --- the writable parchment between the two rods (sheet x58-197 / y55-202) ---

    public static final int PARCHMENT_X = 23;
    public static final int PARCHMENT_Y = 36;
    public static final int PARCHMENT_WIDTH = 140;
    public static final int PARCHMENT_HEIGHT = 147;

    public static int parchmentBottom() {
        return PARCHMENT_Y + PARCHMENT_HEIGHT;
    }

    // --- the text block inside the parchment ---

    /**
     * Margin between the paper and anything drawn on it.
     *
     * <p>{@code PARCHMENT_*} is the paper, not the writing area: setting text flush to it puts ink
     * on the very edge of the sheet, which no scribe would do and which reads as a clipping bug.
     * Everything the screen draws is bounded by {@link #CONTENT_X} and {@link #CONTENT_WIDTH}
     * instead, and the rules and the sheet counter are inset by the same amount.
     */
    public static final int PAD = 4;

    public static final int CONTENT_X = PARCHMENT_X + PAD;
    public static final int CONTENT_WIDTH = PARCHMENT_WIDTH - 2 * PAD;

    // --- chrome ---

    /**
     * The chapter words along the top of the text block. One 9px band whatever happens: long
     * translations shorten the inactive words rather than changing this height, so nothing below
     * depends on how a language spells "creatures".
     */
    public static final int TABS_Y = PARCHMENT_Y + PAD;
    public static final int TABS_HEIGHT = 9;
    /** Gap between two chapter words. */
    public static final int TAB_GAP = 6;

    /** Ink rule under the chapter words; 2px below the underline so the two do not read as one. */
    public static final int RULE_TOP_Y = 51;

    /** The search line: a chevron and a writing rule, not a box. Hidden by config. */
    public static final int SEARCH_Y = 54;
    public static final int SEARCH_HEIGHT = 11;

    /** Ink rule above the sheet counter, and the counter itself. Both drop on a single sheet. */
    public static final int RULE_BOTTOM_Y = 166;
    public static final int FOOT_Y = 170;
    public static final int FOOT_HEIGHT = 9;

    // --- content ---

    /** Icon size inside a cell; {@link #CELL_PITCH} is what the grid steps by. */
    public static final int CELL = 18;
    /**
     * 6 x 22 = 132, exactly {@link #CONTENT_WIDTH} — no unused strip on the right, and a 4px
     * gutter between icons. The earlier 7 x 20 filled the whole parchment instead, which only
     * worked while the page had no margin.
     */
    public static final int CELL_PITCH = 22;
    public static final int COLUMNS = 6;

    public static final int TEXT_ROW_HEIGHT = 10;
    public static final int TEXT_GROUP_HEIGHT = 9;

    /** Where the content starts — under the search line when there is one. */
    public static int contentY(boolean searchShown) {
        return searchShown ? SEARCH_Y + SEARCH_HEIGHT + 1 : SEARCH_Y;
    }

    /** Where the content ends — above the sheet counter when there is one, else at the margin. */
    public static int contentBottom(boolean footerShown) {
        return footerShown ? RULE_BOTTOM_Y - 3 : parchmentBottom() - PAD;
    }

    public static int contentHeight(boolean searchShown, boolean footerShown) {
        return contentBottom(footerShown) - contentY(searchShown);
    }

    public static int gridRowsPerSheet(boolean searchShown, boolean footerShown) {
        return contentHeight(searchShown, footerShown) / CELL_PITCH;
    }

    public static int cellsPerSheet(boolean searchShown, boolean footerShown) {
        return gridRowsPerSheet(searchShown, footerShown) * COLUMNS;
    }

    public static int textRowsPerSheet(boolean searchShown, boolean footerShown) {
        return contentHeight(searchShown, footerShown) / TEXT_ROW_HEIGHT;
    }

    public static int cellX(int column) {
        return CONTENT_X + column * CELL_PITCH;
    }

    /** @param contentTop from {@link #contentY(boolean)}, so callers pass what they already know. */
    public static int cellY(int contentTop, int row) {
        return contentTop + row * CELL_PITCH;
    }
}
