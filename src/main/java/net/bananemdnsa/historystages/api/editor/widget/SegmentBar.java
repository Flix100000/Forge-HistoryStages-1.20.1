package net.bananemdnsa.historystages.api.editor.widget;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A row of segments of which exactly one is chosen — the editor's on/off switch with more than
 * two words in it.
 *
 * <p>Same frame, same travelling fill, same hover wash as {@code ToggleControl}, and the same
 * geometry underneath, because looking alike and behaving alike have to go together. What differs
 * is what it means: the switch <em>sets a value</em>, this one <em>changes what you are looking
 * at</em>. That is why it lives here rather than as another face of the switch.
 *
 * <p>For a long time only the arithmetic was public and the painting was not, on the grounds that
 * a promise about where a segment starts costs nothing while a promise about how one looks is a
 * promise not to restyle the editor. The painting is part of the promise now, because the
 * alternative was worse: an addon wanting a segmented control inside its own tab had to paint one
 * by hand, and a hand-painted one drifts from these the first time the editor's colours move.
 * Restyling the editor now means restyling this, for everybody at once.
 *
 * <p>Note that a tab built with {@link net.bananemdnsa.historystages.api.editor.CompositeCategoryTab}
 * needs none of this: that bar is drawn by the host screen, above the list rather than inside it.
 * This is for a control an addon draws in its own {@code renderContent}.
 *
 * <p>Not a widget with state of its own beyond the animations: whoever draws it owns the chosen
 * index, exactly as the switch's caller owns its boolean.
 */
public final class SegmentBar {

    private static final int FRAME = 0xFF4A4A4A;
    private static final int BACKDROP = 0xFF0D0D0D;

    private static final int FILL = 0xFFFFCC00;
    private static final int TEXT_ON_FILL = 0xFF2C2C2A;
    private static final int TEXT_IDLE = 0xFF5F5E5A;
    /** Dimmer than idle, and the same grey the strip above uses for a tab this stage cannot use. */
    private static final int TEXT_DISABLED = 0xFF3A3A38;
    private static final int TEXT_HOVER = 0xFFCCCCCC;
    private static final int HOVER_WASH = 0xFF3D3520;

    /**
     * The animations one bar needs, held by whoever draws it.
     *
     * <p>One hover ramp per segment plus one travelling fill, and the fill slides between segment
     * <em>indices</em> rather than between two fixed positions — which is the whole reason the
     * switch's {@code State} could not simply be reused.
     */
    public static final class State {

        private final List<Anim> hover = new ArrayList<>();
        private final Anim slide = new Anim();
        private boolean started;

        /**
         * Advances the animations. Call once per frame, before {@link #draw}.
         *
         * @param selected the chosen segment
         * @param hovered  which segment the cursor is over, or -1 for none
         * @param count    how many segments there are
         */
        public void update(int selected, int hovered, int count) {
            while (hover.size() < count) hover.add(new Anim());
            // A bar drawn for the first time is simply on its segment; it did not just get moved
            // there, so it must not slide in from wherever index zero happens to be.
            if (!started) {
                slide.set(selected);
                started = true;
            }
            slide.ramp(selected, Timing.HOVER_IN_MS, Timing.HOVER_IN_MS);
            for (int i = 0; i < count; i++) {
                hover.get(i).ramp(i == hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS);
            }
        }

        private float hoverAt(int index) {
            return index < hover.size() ? Ease.outCubic(hover.get(index).value()) : 0.0f;
        }
    }

    private SegmentBar() {
    }

    /** Height of the bar. */
    public static int height() {
        return SegmentBarGeometry.HEIGHT;
    }

    /** Total width for these labels, the same whichever segment is chosen. */
    public static int width(Font font, List<String> labels) {
        return SegmentBarGeometry.width(measure(font, labels));
    }

    /** Which segment the cursor is over, or -1. Both axes — for hovering. */
    public static int segmentAt(Font font, int x, int y, double mouseX, double mouseY,
                                List<String> labels) {
        return SegmentBarGeometry.segmentAt(x, y, mouseX, mouseY, measure(font, labels));
    }

    /** Which segment a click at {@code mouseX} means, or -1 beside the bar. */
    public static int indexAt(Font font, int x, double mouseX, List<String> labels) {
        return SegmentBarGeometry.indexAt(x, mouseX, measure(font, labels));
    }

    /**
     * Draws the bar with {@code selected} filled.
     *
     * @param disabled one flag per label; a disabled segment is drawn dim and cannot be chosen.
     *                 Hover is the caller's business — it passes {@code -1} for a segment nobody
     *                 can open, which is also what keeps the hover ramp from warming it up.
     */
    public static void draw(GuiGraphics g, Font font, int x, int y, List<String> labels,
                            int selected, State state, boolean[] disabled) {
        int[] widths = measure(font, labels);
        int segW = SegmentBarGeometry.segmentWidth(widths);
        int total = SegmentBarGeometry.width(widths);
        int bottom = y + SegmentBarGeometry.HEIGHT;

        g.fill(x, y, x + total, bottom, FRAME);
        g.fill(x + 1, y + 1, x + total - 1, bottom - 1, BACKDROP);

        // The segment under the cursor warms up, so it is clear which one a click would open.
        for (int i = 0; i < labels.size(); i++) {
            float hover = state.hoverAt(i);
            if (hover <= 0.001f) continue;
            int segX = SegmentBarGeometry.segmentX(x, i, widths);
            g.fill(segX, y + 1, segX + segW, bottom - 1, Fade.mix(BACKDROP, HOVER_WASH, hover));
        }

        // One fill that travels, rather than one lighting up as another goes out: the movement is
        // what says you are still looking at the same control.
        float slide = state.slide.value();
        int fillX = Math.round(Ease.lerp(SegmentBarGeometry.segmentX(x, 0, widths),
                SegmentBarGeometry.segmentX(x, labels.size() - 1, widths),
                labels.size() > 1 ? slide / (labels.size() - 1) : 0.0f));
        g.fill(fillX, y + 1, fillX + segW, bottom - 1, FILL);

        for (int i = 1; i < labels.size(); i++) {
            int dividerX = SegmentBarGeometry.segmentX(x, i, widths) - 1;
            g.fill(dividerX, y + 1, dividerX + 1, bottom - 1, FRAME);
        }

        int textY = y + (SegmentBarGeometry.HEIGHT - 8) / 2;
        for (int i = 0; i < labels.size(); i++) {
            int segX = SegmentBarGeometry.segmentX(x, i, widths);
            // How much of the travelling fill is under this label. The label crossfades with it,
            // so no word is left dark on a lit segment while the fill is still on its way — and
            // that holds for a disabled label the fill merely passes over on its way elsewhere.
            float covered = Ease.clamp01(1.0f - Math.abs(slide - i));
            int idle = i < disabled.length && disabled[i]
                    ? TEXT_DISABLED
                    : Fade.mix(TEXT_IDLE, TEXT_HOVER, state.hoverAt(i));
            int colour = Fade.mix(idle, TEXT_ON_FILL, covered);
            g.drawString(font, labels.get(i), segX + (segW - widths[i]) / 2, textY, colour, false);
        }
    }

    private static int[] measure(Font font, List<String> labels) {
        int[] widths = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) widths[i] = font.width(labels.get(i));
        return widths;
    }
}
