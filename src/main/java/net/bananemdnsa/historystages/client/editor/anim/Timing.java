package net.bananemdnsa.historystages.client.editor.anim;

/**
 * Every duration the editor animates over, in one place.
 *
 * <p>These numbers used to be copied into each screen, and had already drifted apart — the
 * tooltip delay was 500 ms in the config editor and 400 ms everywhere else, which makes the
 * same gesture feel differently responsive depending on where the player happens to be.
 * Anything timed belongs here so a change lands across the whole editor at once.
 */
public final class Timing {

    private Timing() {
    }

    // ---- Hover ----

    /** Cursor arriving on a control. Short, because it is a direct response to the player. */
    public static final float HOVER_IN_MS = 130.0f;
    /** Cursor leaving. Longer than the arrival so the interface does not flicker on a sweep. */
    public static final float HOVER_OUT_MS = 190.0f;

    // ---- Appearing and disappearing ----

    /** Overlays that appear next to the cursor: context menus, dropdown popups. */
    public static final float POPUP_MS = 110.0f;
    /** Modal dialogs, which cover the screen and so may take slightly longer. */
    public static final float MODAL_MS = 140.0f;
    /** Toast dismissed by a click — short on purpose; the player wants it gone. */
    public static final float TOAST_DISMISS_MS = 140.0f;

    // ---- Movement ----

    /**
     * Half-life for a scroll offset chasing its target. Matches the feel of the old
     * per-frame {@code * 0.25f} at 60 FPS, but now holds at any frame rate.
     */
    public static final float SCROLL_HALF_LIFE_MS = 45.0f;
    /** Content sliding sideways when entering or leaving a folder. */
    public static final float NAV_SLIDE_MS = 170.0f;
    /** A control row growing or collapsing, e.g. the organize-mode checkbox column. */
    public static final float REVEAL_MS = 160.0f;

    // ---- Delays before something extra happens ----

    /** How long a control must be hovered before its explanatory tooltip appears. */
    public static final long TOOLTIP_DELAY_MS = 400L;
    /** How long a clipped label must be hovered before it starts scrolling its full text. */
    public static final long MARQUEE_DELAY_MS = 800L;
    /** Marquee scroll rate, in pixels per second. */
    public static final float MARQUEE_SPEED = 25.0f;

    // ---- One-shot and looping feedback ----

    /** How long a drop target stays highlighted after a move completes. */
    public static final long DROP_PULSE_MS = 600L;
    /** How long a control flashes after its action succeeded, e.g. Save. */
    public static final long FLASH_MS = 450L;
    /** Period of a looping attention indicator such as the unsaved-changes dot. */
    public static final float BREATHE_PERIOD_MS = 1600.0f;
}
