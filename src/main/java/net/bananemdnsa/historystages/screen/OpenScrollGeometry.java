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

    // --- chrome ---

    /** Chapter tabs: 18x18 icons in a row along the top of the parchment. */
    public static final int TAB_SIZE = 18;
    public static final int TAB_GAP = 2;
    public static final int TAB_Y = PARCHMENT_Y;

    public static int tabX(int index) {
        return PARCHMENT_X + index * (TAB_SIZE + TAB_GAP);
    }

    /** The spelled-out chapter name and its count, under the tabs. */
    public static final int HEAD_Y = 55;
    public static final int HEAD_HEIGHT = 9;

    /**
     * The search bar. Only content chapters have one; the overview page does not.
     *
     * <p>The height is the shared {@code SearchBar} widget's fixed 20px, not a free choice — the
     * content below has to start under it, so the number lives here rather than being rediscovered
     * by the screen.
     */
    public static final int SEARCH_Y = 66;
    public static final int SEARCH_HEIGHT = 20;

    /** Matches the shared {@code Scrollbar} widget's width, which the screen cannot change. */
    public static final int SCROLLBAR_WIDTH = 5;
    public static final int SCROLLBAR_X = PARCHMENT_X + PARCHMENT_WIDTH - SCROLLBAR_WIDTH;

    // --- the icon grid ---

    public static final int CONTENT_Y = SEARCH_Y + SEARCH_HEIGHT + 3;
    public static final int CELL = 18;
    public static final int COLUMNS = 7;
    public static final int VISIBLE_ROWS = 5;

    public static int cellX(int column) {
        return PARCHMENT_X + column * CELL;
    }

    public static int cellY(int row) {
        return CONTENT_Y + row * CELL;
    }

    /** How tall the scrolling area is, for both the grid and the text list. */
    public static int contentHeight() {
        return parchmentBottom() - CONTENT_Y;
    }

    // --- the text list ---

    public static final int TEXT_ROW_HEIGHT = 10;
    public static final int TEXT_GROUP_HEIGHT = 9;

    // --- the overview page ---

    public static final int OVERVIEW_ICON_SIZE = 32;
    public static final int OVERVIEW_ICON_X = PARCHMENT_X + (PARCHMENT_WIDTH - OVERVIEW_ICON_SIZE) / 2;
    public static final int OVERVIEW_ICON_Y = 57;
    public static final int OVERVIEW_TITLE_Y = 92;
    public static final int OVERVIEW_DESC_Y = 104;
    public static final int OVERVIEW_COUNTS_Y = 174;
}
