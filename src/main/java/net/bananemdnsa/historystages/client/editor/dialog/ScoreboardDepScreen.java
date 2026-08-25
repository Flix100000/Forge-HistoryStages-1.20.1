package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Editor for a scoreboard dependency: objective, score holder, comparison operator and value.
 *
 * <p>The operator is a dropdown and sits at the last slot of the tab order, after the three
 * text fields — hence {@link #focusableCount()} exceeds the field count.
 *
 * <p>Replaces the dialog {@code DependencyEditorScreen} used to hand-draw inline, which had
 * no clipboard, selection or cursor movement because it rendered its own text fields.
 */
public class ScoreboardDepScreen extends AbstractInputScreen {

    /** Callback carrying the four edited values back to the dependency editor. */
    public interface Result {
        void accept(String objective, String holder, int opIndex, int value);
    }

    private static final int DROPDOWN_H = 16;
    private static final int OPTION_H = 14;
    private static final int POPUP_PAD = 2;
    /** Vertical inset of the dropdown button below its label inside the extra-content row. */
    private static final int DROPDOWN_INSET_Y = 11;

    private final Screen parent;
    private final String initialObjective;
    private final String initialHolder;
    private final int initialValue;
    private final String[] operators;
    private final Result onDone;

    private int opIndex;
    private boolean opDropdownOpen = false;

    /** Cached button geometry from the last render, used for hit-testing clicks. */
    private int dropdownX, dropdownY, dropdownW;

    public ScoreboardDepScreen(Screen parent, Component title, String objective, String holder,
                               int opIndex, int value, String[] operators, Result onDone) {
        super(parent, title);
        this.parent = parent;
        this.initialObjective = objective;
        this.initialHolder = holder;
        this.opIndex = opIndex;
        this.initialValue = value;
        this.operators = operators;
        this.onDone = onDone;
    }

    @Override
    protected List<InputField> fields() {
        return List.of(
                InputField.text("objective")
                        .label(Component.translatable("editor.historystages.dep.dialog.objective"))
                        .maxLength(64)
                        .initial(initialObjective)
                        .validator(v -> v.isEmpty()
                                ? Component.translatable("editor.historystages.input.empty") : null),
                InputField.text("holder")
                        .label(Component.translatable("editor.historystages.dep.dialog.score_holder"))
                        .maxLength(64)
                        .initial(initialHolder),
                InputField.number("value")
                        .label(Component.translatable("editor.historystages.dep.dialog.value"))
                        .range(Integer.MIN_VALUE + 1, Integer.MAX_VALUE)
                        .initial(initialValue));
    }

    /** Three fields plus the operator dropdown at the trailing slot. */
    @Override
    protected int focusableCount() {
        return fieldCount() + 1;
    }

    @Override
    protected int extraContentHeight() {
        return 30;
    }

    private boolean inDropdownButton(double mx, double my) {
        return mx >= dropdownX && mx < dropdownX + dropdownW
                && my >= dropdownY && my < dropdownY + DROPDOWN_H;
    }

    /**
     * Popup rectangle, flipped above the button when it would overflow the screen bottom.
     *
     * @return [x, y, w, h]
     */
    private int[] popupGeometry() {
        int h = OPTION_H * operators.length + POPUP_PAD * 2;
        int y = dropdownY + DROPDOWN_H + 2;
        if (y + h > this.height - 2) y = dropdownY - h - 2;
        if (y < 2) y = 2;
        return new int[] { dropdownX, y, dropdownW, h };
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        dropdownX = x;
        dropdownY = y + DROPDOWN_INSET_Y;
        dropdownW = w;

        g.drawString(this.font, Component.translatable("editor.historystages.dep.dialog.operator"),
                x, y, LABEL_GREY, false);

        // A dropdown has no isFocused() of its own, so the gold ring is driven by the slot the
        // base reports — otherwise tabbing onto the operator would give no visible feedback.
        boolean hover = inDropdownButton(mouseX, mouseY);
        boolean slotFocused = focusedSlot() == fieldCount();
        int border = opDropdownOpen || slotFocused ? ACCENT_GOLD : (hover ? 0xFF888888 : FIELD_BORDER);
        int bg = hover ? 0xFF252525 : FIELD_BG;
        g.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + DROPDOWN_H, border);
        g.fill(dropdownX + 1, dropdownY + 1, dropdownX + dropdownW - 1, dropdownY + DROPDOWN_H - 1, bg);

        int textY = dropdownY + (DROPDOWN_H - 8) / 2;
        g.drawString(this.font, operators[opIndex], dropdownX + 6, textY, 0xFFEEEEEE, false);

        // Pixel-art caret on the right, matching the other dropdowns in the editor.
        int cx = dropdownX + dropdownW - 9;
        int cy = dropdownY + DROPDOWN_H / 2 - 1;
        int caret = hover || opDropdownOpen ? 0xFFDDDDDD : 0xFF999999;
        if (opDropdownOpen) {
            g.fill(cx + 2, cy, cx + 3, cy + 1, caret);
            g.fill(cx + 1, cy + 1, cx + 4, cy + 2, caret);
            g.fill(cx, cy + 2, cx + 5, cy + 3, caret);
        } else {
            g.fill(cx, cy, cx + 5, cy + 1, caret);
            g.fill(cx + 1, cy + 1, cx + 4, cy + 2, caret);
            g.fill(cx + 2, cy + 2, cx + 3, cy + 3, caret);
        }

        if (!opDropdownOpen) return;

        // The popup is an overlay: it overflows extraContentHeight() and must beat both the
        // error line and the widgets drawn after renderContent, hence the z translate.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A1A);

        for (int i = 0; i < operators.length; i++) {
            int iy = py + POPUP_PAD + i * OPTION_H;
            boolean optHover = mouseX >= px && mouseX < px + pw && mouseY >= iy && mouseY < iy + OPTION_H;
            if (optHover) g.fill(px + 1, iy, px + pw - 1, iy + OPTION_H, 0x25FFFFFF);
            g.drawString(this.font, operators[i], px + 6, iy + (OPTION_H - 8) / 2,
                    i == opIndex ? ACCENT_GOLD : 0xFFEEEEEE, false);
        }
        g.pose().popPose();
    }

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        // Options first: while open, the popup swallows every left click.
        if (opDropdownOpen) {
            int[] geom = popupGeometry();
            int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
            if (mx >= px && mx < px + pw && my >= py && my < py + ph) {
                int idx = (int) ((my - py - POPUP_PAD) / OPTION_H);
                if (idx >= 0 && idx < operators.length) {
                    opIndex = idx;
                    playClick();
                }
                opDropdownOpen = false;
                return true;
            }
            // Click outside the popup just closes it
            opDropdownOpen = false;
            return true;
        }

        if (inDropdownButton(mx, my)) {
            opDropdownOpen = true;
            // Move focus onto the dropdown slot too, so left/right cycle the operator rather
            // than moving the caret in whichever text field happened to be focused.
            focusExtraSlot(fieldCount());
            playClick();
            return true;
        }
        return false;
    }

    @Override
    protected boolean extraContentKeyPressed(int keyCode) {
        if (opDropdownOpen && keyCode == 256) { opDropdownOpen = false; return true; }
        // Left/right cycle the operator while its slot holds focus, so the dialog stays
        // fully keyboard-operable as it was before it became a real screen.
        if (focusedSlot() == fieldCount()) {
            if (keyCode == 263) { // LEFT
                opIndex = (opIndex - 1 + operators.length) % operators.length;
                return true;
            }
            if (keyCode == 262) { // RIGHT
                opIndex = (opIndex + 1) % operators.length;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onExtraFocused(int slot) {
        // Tab landed on the operator dropdown; it is click-driven, nothing to focus.
    }

    @Override
    protected void onConfirm(InputValues values) {
        onDone.accept(values.getString("objective"), values.getString("holder"),
                opIndex, values.getInt("value"));
        this.minecraft.setScreen(parent);
    }
}
