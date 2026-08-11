package net.bananemdnsa.historystages.screen;

/**
 * Where everything sits on the research pedestal screen.
 *
 * <p>The artwork is authored on a 256x256 sheet; the panel body is the region starting at
 * ({@link #SHEET_X}, {@link #SHEET_Y}). Every constant below is panel-relative — sheet
 * coordinate minus that origin — because that is what the screen draws in.
 *
 * <p>Deliberately free of Minecraft types so the arithmetic is unit-testable. Turning a tier
 * into a texture happens in the screen.
 */
public final class PedestalLayout {

    private PedestalLayout() {}

    // --- the sheet ---

    /**
     * Top-left of the drawn region inside the 256x256 sheet. The whole artwork is one blit,
     * candles included, so that {@code imageHeight} covers everything that is painted and
     * Minecraft centres the screen on what the player actually sees.
     */
    public static final int SHEET_X = 10;
    public static final int SHEET_Y = 0;

    /** Panel size. Wider than the vanilla 176 the old sheet used, and tall enough to hold
     *  the candles that stand above the slab in tiers 2-4. */
    public static final int WIDTH = 236;
    public static final int HEIGHT = 254;

    /** Where the slab starts inside the panel. Above this are only the candles, which is
     *  why the dependency scroll hangs off this line rather than off the panel top. */
    public static final int BODY_TOP = 46;

    // --- controls ---

    /** Item position of the scroll slot. */
    public static final int SCROLL_SLOT_X = 57;
    public static final int SCROLL_SLOT_Y = 111;

    /** Top-left of the 18x18 start/pause button. */
    public static final int BUTTON_X = 80;
    public static final int BUTTON_Y = 110;

    /** The window the progress fill shows through, punched out of the bar mask. */
    public static final int BAR_X = 104;
    public static final int BAR_Y = 113;
    public static final int BAR_W = 73;
    public static final int BAR_H = 13;

    /** The green the bar fills with. Warm enough to sit on the parchment. */
    public static final int BAR_COLOR = 0xFF6AA84F;

    /**
     * Where the percentage starts inside the bar. Fixed at the left end rather than centred,
     * so the number stays put instead of drifting as the fill grows. Inset a little to clear
     * the bar's ragged left edge.
     */
    public static final int BAR_TEXT_X = BAR_X + 4;

    /** Top of the percentage, so an 8px glyph sits centred in the 13px window. */
    public static final int BAR_TEXT_Y = BAR_Y + (BAR_H - 8) / 2;

    // --- text ---
    //
    // Measured off the artwork rather than guessed, because very little of this panel can
    // actually hold a label:
    //   * the pedestal top (y46-96) is taken by candles from tier 2 on, and tier 4 covers
    //     the whole surface with gold cloth. What is left clear is about 30px wide.
    //   * the parchment strip (y97-141) carries the slot, the button and the bar.
    //   * the inventory starts at y167.
    // That leaves one band, y142-166, and only two lines fit in it.
    //
    // The band is dark slab in tiers 1-3 and gold cloth in tier 4, so there is no background
    // colour to design against — labels are drawn with a shadow instead, the way vanilla
    // does it, which reads on both.

    /** Left margin. Inset far enough to clear the pedestal's sloped edge across the band. */
    public static final int TEXT_X = 18;

    /** Right edge for right-aligned labels. */
    public static final int TEXT_RIGHT = WIDTH - 18;

    /** Stage line, with the speed multiplier pinned right. */
    public static final int LINE_1_Y = 144;

    /** Remaining time, or a warning across the whole width. The percentage does not live
     *  here — it is written across the bar itself. */
    public static final int LINE_2_Y = 156;

    // --- player inventory ---

    public static final int INV_X = 38;
    public static final int INV_Y = 174;
    public static final int HOTBAR_Y = 232;
    public static final int SLOT_PITCH = 18;

    // --- dependency panel ---

    /** Content size of the rolled dependency scroll. */
    public static final int DEP_W = 124;
    public static final int DEP_H = 164;

    /** Its top-left inside its own 256x256 sheet. */
    public static final int DEP_SHEET_X = 66;
    public static final int DEP_SHEET_Y = 46;

    /** Gap between the panel body and the dependency scroll. */
    public static final int DEP_GAP = 4;

    /** Baseline of the "Requirements" heading, relative to the dependency panel. */
    public static final int DEP_TITLE_Y = 10;

    // The scroll is drawn rolled: gold ends top and bottom, and the sheet narrows towards
    // them. Only the flat middle can carry content, and it is a good deal smaller than the
    // panel it sits in — measured off the artwork, not assumed.

    /** Left edge of the writable parchment, relative to the dependency panel. */
    public static final int DEP_CONTENT_X = 21;

    /** Writable width. Narrow enough that most requirement values need truncating. */
    public static final int DEP_CONTENT_W = 82;

    /** Top of the requirement list: the first row below the upper roll. */
    public static final int DEP_LIST_Y = 26;

    /** The rule drawn across the scroll, above the deposit controls. */
    public static final int DEP_RULE_Y = 110;

    /** List height. Runs from under the roll down to the rule and no further — anything
     *  taller scrolls rather than spilling over the artwork. */
    public static final int DEP_LIST_H = DEP_RULE_Y - DEP_LIST_Y;

    /** Top-left of the 18x18 XP deposit button, relative to the dependency panel. */
    public static final int DEP_XP_BUTTON_X = 38;
    public static final int DEP_XP_BUTTON_Y = 163;

    /** Item position of the deposit slot, relative to the dependency panel. */
    public static final int DEP_SLOT_X = 69;
    public static final int DEP_SLOT_Y = 164;

    // --- the button sheet: 3 columns (play, pause, flask) x 2 rows (normal, hovered) ---

    public static final int ICON_SIZE = 18;
    public static final int ICON_COL_PLAY = 0;
    public static final int ICON_COL_PAUSE = 1;
    public static final int ICON_COL_XP = 2;

    // --- arithmetic ---

    /** The four sheets that exist. Anything else would be a missing-texture screen. */
    public static int clampTier(int tier) {
        if (tier < 1) return 1;
        if (tier > 4) return 4;
        return tier;
    }

    /** How many pixels of the bar window are filled at this progress. */
    public static int barFillWidth(int progress, int maxProgress) {
        if (progress <= 0 || maxProgress <= 0) return 0;
        if (progress >= maxProgress) return BAR_W;
        return BAR_W * progress / maxProgress;
    }
}
