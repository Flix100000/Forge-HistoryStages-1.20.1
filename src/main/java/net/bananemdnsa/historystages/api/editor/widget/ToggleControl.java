package net.bananemdnsa.historystages.api.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The editor's on/off switch: both states side by side in one frame, the active half filled.
 *
 * <p>The editor used to draw two different switches. Config rows showed a green tick and a red
 * cross with no frame at all, while the cards in the stage settings showed a dark box whose only
 * difference between on and off was the word inside it — so a column of twenty rows could not be
 * read at a glance. Showing both halves at once fixes that without asking anyone to read: the
 * fill sits on the left or on the right.
 *
 * <p>It also turns flipping into picking. A click sets the half it landed on, so clicking the
 * value that is already set does nothing, and nobody has to click and then check what came out.
 *
 * <p>Off is grey, not red. Red means "something is wrong" everywhere else in this editor, and a
 * setting that is switched off is not wrong.
 *
 * <p>Usage: keep one {@link State} per switch, call {@link State#update} once a frame with the
 * current value and whatever {@link #segmentAt} reports, then {@link #draw}. Clicks go through
 * {@link #valueAt}.
 */
public final class ToggleControl {

    private static final int FRAME = 0xFF4A4A4A;
    private static final int FRAME_DIM = 0xFF2E2E2E;
    private static final int BACKDROP = 0xFF0D0D0D;

    private static final int FILL_ON = 0xFFFFCC00;
    private static final int FILL_OFF = 0xFF555452;
    private static final int FILL_DIM = 0xFF2E2E2E;

    private static final int TEXT_ON_FILL_ON = 0xFF2C2C2A;
    private static final int TEXT_ON_FILL_OFF = 0xFF0D0D0D;
    private static final int TEXT_IDLE = 0xFF5F5E5A;
    private static final int TEXT_HOVER = 0xFFCCCCCC;
    private static final int HOVER_WASH = 0xFF3D3520;

    private static final int TEXT_DIM_ACTIVE = 0xFF777777;
    private static final int TEXT_DIM_INACTIVE = 0xFF4A4A4A;

    private ToggleControl() {
    }

    /**
     * The three animations one switch needs, held by whoever draws it.
     *
     * <p>Not a field on the widget, because a list draws many switches from one call site and
     * would otherwise share a single animation across all of them. The {@link Anim}s are private:
     * {@code Anim} is an internal type, and nothing internal may appear in an api signature.
     */
    public static final class State {

        private final Anim onHover = new Anim();
        private final Anim offHover = new Anim();
        private final Anim slide = new Anim();
        private boolean started;

        /**
         * Advances all three animations. Call once per frame, before {@link #draw}.
         *
         * @param value   what the switch currently shows
         * @param hovered which half the cursor is over, or {@code null} for neither
         */
        public void update(boolean value, Boolean hovered) {
            float target = value ? 1.0f : 0.0f;
            // A switch drawn for the first time is simply in its state; it did not just get moved
            // there, so it must not slide in from the opposite half.
            if (!started) {
                slide.set(target);
                started = true;
            }
            slide.ramp(target, Timing.HOVER_IN_MS, Timing.HOVER_IN_MS);
            onHover.ramp(Boolean.TRUE.equals(hovered), Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS);
            offHover.ramp(Boolean.FALSE.equals(hovered), Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS);
        }
    }

    /** Total width of the control for this font, the same whichever value it shows. */
    public static int width(Font font) {
        return ToggleGeometry.width(font.width(onLabel()), font.width(offLabel()));
    }

    /** Height of the control. */
    public static int height() {
        return ToggleGeometry.HEIGHT;
    }

    /** Which half the cursor is over, or {@code null} for neither. Both axes — for hovering. */
    public static Boolean segmentAt(Font font, int x, int y, double mouseX, double mouseY) {
        return ToggleGeometry.segmentAt(x, y, font.width(onLabel()), font.width(offLabel()),
                mouseX, mouseY);
    }

    /** Which value a click at {@code mouseX} means, or {@code null} beside the control. */
    public static Boolean valueAt(Font font, int x, double mouseX) {
        return ToggleGeometry.valueAt(x, font.width(onLabel()), font.width(offLabel()), mouseX);
    }

    /**
     * Draws the switch.
     *
     * @param dimmed true for a value that is not this row's own — inherited from a layer below.
     *               The whole control goes grey, so the difference is visible without reading.
     */
    public static void draw(GuiGraphics g, Font font, int x, int y, boolean value,
                            State state, boolean dimmed) {
        String on = onLabel();
        String off = offLabel();
        int onW = font.width(on);
        int offW = font.width(off);

        int segW = ToggleGeometry.segmentWidth(onW, offW);
        int w = ToggleGeometry.width(onW, offW);
        int onX = ToggleGeometry.onSegmentX(x);
        int offX = ToggleGeometry.offSegmentX(x, onW, offW);
        int bottom = y + ToggleGeometry.HEIGHT;

        g.fill(x, y, x + w, bottom, dimmed ? FRAME_DIM : FRAME);
        g.fill(x + 1, y + 1, x + w - 1, bottom - 1, BACKDROP);

        float slide = Ease.outCubic(state.slide.value());
        float onHover = Ease.outCubic(state.onHover.value());
        float offHover = Ease.outCubic(state.offHover.value());

        // The half under the cursor warms up, so it is clear which value a click would set.
        if (!dimmed && onHover > 0.001f) {
            g.fill(onX, y + 1, onX + segW, bottom - 1, Fade.mix(BACKDROP, HOVER_WASH, onHover));
        }
        if (!dimmed && offHover > 0.001f) {
            g.fill(offX, y + 1, offX + segW, bottom - 1, Fade.mix(BACKDROP, HOVER_WASH, offHover));
        }

        // One fill that travels, rather than one lighting up as the other goes out: the movement
        // is what says the same switch changed, instead of a different area having appeared.
        int fillX = Math.round(Ease.lerp(offX, onX, slide));
        int fill = dimmed ? FILL_DIM : Fade.mix(FILL_OFF, FILL_ON, slide);
        g.fill(fillX, y + 1, fillX + segW, bottom - 1, fill);

        g.fill(x + 1 + segW, y + 1, x + 2 + segW, bottom - 1, dimmed ? FRAME_DIM : FRAME);

        int textY = y + (ToggleGeometry.HEIGHT - 8) / 2;
        g.drawString(font, on, onX + (segW - onW) / 2, textY,
                labelColor(dimmed, value, slide, onHover, TEXT_ON_FILL_ON), false);
        g.drawString(font, off, offX + (segW - offW) / 2, textY,
                labelColor(dimmed, !value, 1.0f - slide, offHover, TEXT_ON_FILL_OFF), false);
    }

    /**
     * Colour for one of the two labels.
     *
     * @param covered how much of the travelling fill is under this label, 0..1. The label
     *                crossfades with it, so no word is left dark on a dark half mid-travel.
     */
    private static int labelColor(boolean dimmed, boolean active, float covered, float hover,
                                  int onFill) {
        if (dimmed) return active ? TEXT_DIM_ACTIVE : TEXT_DIM_INACTIVE;
        return Fade.mix(Fade.mix(TEXT_IDLE, TEXT_HOVER, hover), onFill, Ease.clamp01(covered));
    }

    private static String onLabel() {
        return Component.translatable("editor.historystages.display.on").getString();
    }

    private static String offLabel() {
        return Component.translatable("editor.historystages.display.off").getString();
    }
}
