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

    /**
     * Ink rule above the sheet counter, and the counter itself. Both drop on a single sheet.
     *
     * <p>Two pixels higher than the text-only footer used to sit: the page arrows are 13px tall
     * where the counter alone was 9, and the extra height has to come off the top or the arrows
     * end up on the paper's edge. It costs the content two pixels and no rows.
     */
    public static final int RULE_BOTTOM_Y = 164;
    /** Top of the counter's glyphs, set so the text sits centred against the page arrows. */
    public static final int FOOT_Y = 168;

    /** A vanilla {@code PageButton} is exactly this big; the numbers are its, not ours. */
    public static final int PAGE_BUTTON_WIDTH = 23;
    public static final int PAGE_BUTTON_HEIGHT = 13;

    /**
     * The page arrows, two pixels under the bottom rule, ending exactly on the same bottom margin
     * every other mark on the page respects.
     */
    public static final int PAGE_BUTTON_Y = 166;

    /** The arrows flank the sheet counter, one at each end of the text block. */
    public static int pageBackwardX() {
        return CONTENT_X;
    }

    public static int pageForwardX() {
        return CONTENT_X + CONTENT_WIDTH - PAGE_BUTTON_WIDTH;
    }

    // --- the done button, under the sheet ---

    /**
     * Size and spacing copied from {@code BookViewScreen}: a 200x20 button two pixels below the
     * artwork. A reader who has closed a book knows where this one is without looking.
     */
    public static final int DONE_WIDTH = 200;
    public static final int DONE_HEIGHT = 20;
    public static final int DONE_GAP = 2;

    /** Sheet plus button — what the screen centres vertically, so the pair sits together. */
    public static int totalHeight() {
        return HEIGHT + DONE_GAP + DONE_HEIGHT;
    }

    /** Gap between Done and a second button sharing its row. Vanilla's lectern screen uses 4. */
    public static final int BUTTON_GAP = 4;

    /**
     * Width of one button in the row. Two halves plus the gap add up to {@link #DONE_WIDTH}
     * exactly, so the row keeps its width whether Done stands alone or shares it.
     */
    public static int buttonWidth(boolean paired) {
        return paired ? (DONE_WIDTH - BUTTON_GAP) / 2 : DONE_WIDTH;
    }

    /** Left edge of the button row. Centred on the screen, not on the sheet — as Done always was. */
    public static int buttonRowX(int screenWidth) {
        return (screenWidth - DONE_WIDTH) / 2;
    }

    /** Left edge of the second button in a shared row. */
    public static int secondButtonX(int screenWidth) {
        return buttonRowX(screenWidth) + buttonWidth(true) + BUTTON_GAP;
    }

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
