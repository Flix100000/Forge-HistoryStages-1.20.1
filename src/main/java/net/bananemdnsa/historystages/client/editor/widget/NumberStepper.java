package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.IntConsumer;

/**
 * A bounded integer picked with a minus button, a readout and a plus button.
 *
 * <p>Chosen over a text field wherever the value is small and bounded: there is no invalid state to
 * validate, no keyboard focus to steal from the surrounding screen, and the caller never has to
 * parse anything. Clamping happens here, so {@link #getValue()} is always inside the range.
 *
 * <p>A step button greys out once its end of the range is reached, so the limit is visible before
 * it is hit rather than showing up as a click that does nothing.
 *
 * <p>The readout is also a small text field: clicking it types a value directly, which is what
 * keeps the widget usable on a wide range where stepping to 250 would be 250 clicks. It holds only
 * digits, caps their number at what {@code max} needs, and clamps on commit — so no invalid state
 * ever leaves the widget. Enter or a click elsewhere commits, ESC reverts.
 *
 * <p>Usage: {@link #setPosition} each frame (or once, if the layout is fixed), {@link #render}
 * during normal rendering, and route clicks through {@link #mouseClicked}. For the text field the
 * owner also has to forward {@link #keyPressed} and {@link #charTyped}, and should consult
 * {@link #isEditing()} before acting on ESC itself.
 */
public class NumberStepper {

    public static final int HEIGHT = 14;

    private static final int STEP_W = 14;
    private static final int VALUE_W = 34;

    /** How long the gold flash after a press takes to fade out — matches {@link StyledButton}. */
    private static final float PRESS_FLASH_MS = 320.0f;

    private final int min;
    private final int max;
    private final int step;
    private final IntConsumer onChange;

    private int value;
    private int x, y;
    private boolean enabled = true;

    private final Anim minusHover = new Anim();
    private final Anim plusHover = new Anim();
    private final Anim minusPress = new Anim();
    private final Anim plusPress = new Anim();

    /** Typed digits while the readout is being edited; null when it is not. */
    private String buffer = null;
    /** Value to fall back to when the edit is cancelled or committed empty. */
    private int valueBeforeEdit;

    /**
     * @param step how much one click moves the value; the range ends are still hard limits, so a
     *             step that does not divide the range evenly simply stops at {@code min}/{@code max}
     * @param onChange notified with the new value on every change, or null if the caller polls
     */
    public NumberStepper(int min, int max, int step, int initial, IntConsumer onChange) {
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.onChange = onChange;
        this.value = clamp(initial);
    }

    public int getValue() { return value; }

    /** Sets the value without notifying {@link #onChange} — for pushing state in from the owner. */
    public void setValue(int newValue) {
        this.value = clamp(newValue);
        this.buffer = null;
    }

    /** True while the readout has keyboard focus, so the owner can leave ESC and typing alone. */
    public boolean isEditing() { return buffer != null; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static int width() { return STEP_W * 2 + VALUE_W; }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return inBox(mouseX, mouseY, x, y, width(), HEIGHT);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean canDecrease = enabled && value > min;
        boolean canIncrease = enabled && value < max;

        int minusX = x;
        int valueX = x + STEP_W;
        int plusX = valueX + VALUE_W;

        drawStep(g, font, minusX, "-", canDecrease,
                canDecrease && inBox(mouseX, mouseY, minusX, y, STEP_W, HEIGHT), minusHover, minusPress);

        if (isEditing()) {
            g.fill(valueX, y, valueX + VALUE_W, y + HEIGHT, 0x25FFCC00);
            g.fill(valueX, y + HEIGHT - 1, valueX + VALUE_W, y + HEIGHT, 0xFFFFCC00);
            // No caret: the gold face and edge already say the field has focus, and a blinking
            // glyph in a box this small reads as flicker rather than as a cursor.
            g.drawCenteredString(font, buffer, valueX + VALUE_W / 2, y + 3, 0xFFFFFFFF);
        } else {
            boolean valueHovered = enabled && inBox(mouseX, mouseY, valueX, y, VALUE_W, HEIGHT);
            g.fill(valueX, y, valueX + VALUE_W, y + HEIGHT, valueHovered ? 0x20FFFFFF : 0x10FFFFFF);
            g.drawCenteredString(font, String.valueOf(value), valueX + VALUE_W / 2, y + 3,
                    Fade.grey(enabled ? (valueHovered ? 0xFF : 0xCC) : 0x66));
        }

        drawStep(g, font, plusX, "+", canIncrease,
                canIncrease && inBox(mouseX, mouseY, plusX, y, STEP_W, HEIGHT), plusHover, plusPress);
    }

    private void drawStep(GuiGraphics g, Font font, int bx, String glyph, boolean active,
                          boolean hovered, Anim hover, Anim press) {
        float hp = Ease.outCubic(hover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        float flash = Ease.outCubic(press.ramp(0.0f, PRESS_FLASH_MS));

        int bgAlpha = active ? (int) (0x10 + hp * 0x15) : 0x08;
        g.fill(bx, y, bx + STEP_W, y + HEIGHT, (bgAlpha << 24) | 0xFFFFFF);
        if (flash > 0.001f) g.fill(bx, y, bx + STEP_W, y + HEIGHT, Fade.rgba(0xFFCC00, flash * 0.22f));

        // Bottom accent, same language as StyledButton: present at rest, brighter under the cursor.
        int accentAlpha = active ? (int) (0x40 + hp * 0x40) : 0x20;
        g.fill(bx, y + HEIGHT - 1, bx + STEP_W, y + HEIGHT, (accentAlpha << 24) | 0xFFFFFF);

        int textGray = active ? (int) (0xCC + hp * 0x33) : 0x55;
        g.drawCenteredString(font, glyph, bx + STEP_W / 2, y + 3, Fade.grey(textGray));
    }

    /**
     * @return true if the click landed on the widget, whether or not it changed the value. A click
     *         anywhere else commits a running edit first and is then left to the caller, so
     *         pressing a confirm button while typing keeps the typed value.
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        boolean onReadout = enabled && inBox(mouseX, mouseY, x + STEP_W, y, VALUE_W, HEIGHT);
        if (isEditing() && !onReadout) commitEdit();

        if (!enabled || !isMouseOver(mouseX, mouseY)) return false;

        if (onReadout) {
            if (!isEditing()) beginEdit();
            return true;
        }
        if (inBox(mouseX, mouseY, x, y, STEP_W, HEIGHT)) {
            apply(value - step, minusPress);
        } else if (inBox(mouseX, mouseY, x + STEP_W + VALUE_W, y, STEP_W, HEIGHT)) {
            apply(value + step, plusPress);
        }
        return true;
    }

    /** @return true if the key was consumed; only ever true while {@link #isEditing()}. */
    public boolean keyPressed(int keyCode) {
        if (!isEditing()) return false;
        switch (keyCode) {
            case 256 -> buffer = null;                               // ESC — discard
            case 257, 335 -> commitEdit();                           // Enter / numpad Enter
            case 259 -> {                                            // Backspace
                if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
            }
            default -> { return false; }
        }
        return true;
    }

    /** @return true if the character was consumed; only ever true while {@link #isEditing()}. */
    public boolean charTyped(char c) {
        if (!isEditing() || c < '0' || c > '9') return false;
        // Capped at the digits the range can use, so the field cannot hold a number it would only
        // clamp away on commit.
        if (buffer.length() >= String.valueOf(max).length()) return true;
        // A leading zero would let "0" grow into "0999", one digit past the cap.
        if (!(buffer.isEmpty() && c == '0')) buffer += c;
        return true;
    }

    private void beginEdit() {
        valueBeforeEdit = value;
        buffer = "";
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Takes the typed digits, clamped; an empty field means the user changed their mind. */
    public void commitEdit() {
        if (!isEditing()) return;
        String typed = buffer;
        buffer = null;
        if (typed.isEmpty()) {
            value = valueBeforeEdit;
            return;
        }
        int next;
        try {
            next = clamp(Integer.parseInt(typed));
        } catch (NumberFormatException e) {
            // More digits than an int holds — on a range that wide the intent is plainly "the top".
            next = max;
        }
        if (next == value) return;
        value = next;
        if (onChange != null) onChange.accept(value);
    }

    private void apply(int candidate, Anim press) {
        int next = clamp(candidate);
        if (next == value) return;
        value = next;
        press.set(1.0f);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (onChange != null) onChange.accept(value);
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean inBox(double mouseX, double mouseY, int bx, int by, int w, int h) {
        return mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
    }
}
