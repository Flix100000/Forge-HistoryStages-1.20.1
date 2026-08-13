package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.data.temporary.DurationUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact time-unit picker for {@link DurationUnit}. Collapsed shows the current
 * unit's name with a caret; expanded lists every unit (seconds / minutes / hours /
 * days). Single-select.
 *
 * <p>Render order: call {@link #renderButton} during normal widget rendering,
 * {@link #renderPopup} AFTER all other widgets, and route mouse clicks through
 * {@link #mouseClicked} BEFORE other widgets.</p>
 */
public class DurationUnitDropdown {
    public static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int POPUP_PAD = 2;

    private final Consumer<DurationUnit> onChange;
    private DurationUnit unit;
    private int buttonX, buttonY, buttonW;
    private boolean expanded = false;

    /** Reveal progress of the popup; also drives the caret turning over. */
    private final Anim open = new Anim();
    private final Anim buttonHover = new Anim();
    private final Map<Integer, Anim> rowHover = new HashMap<>();

    public DurationUnitDropdown(DurationUnit initial, int minWidth, Consumer<DurationUnit> onChange) {
        this.unit = initial != null ? initial : DurationUnit.HOURS;
        this.buttonW = computeWidth(minWidth);
        this.onChange = onChange;
    }

    /** Widest unit label (+padding), so the box is never narrower than its content. */
    private static int computeWidth(int minWidth) {
        Font font = Minecraft.getInstance().font;
        int w = minWidth;
        for (DurationUnit u : DurationUnit.values()) {
            int rowW = font.width(label(u)) + 15;
            if (rowW > w) w = rowW;
        }
        return w;
    }

    public DurationUnit getUnit() { return unit; }

    public void setUnit(DurationUnit unit) { this.unit = unit != null ? unit : DurationUnit.HOURS; }

    /** Width the widget actually renders at (>= the minWidth passed to the constructor). */
    public int getWidth() { return buttonW; }

    public boolean isExpanded() { return expanded; }

    public void close() { expanded = false; }

    public void setPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }

    public boolean isMouseOver(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) {
            return true;
        }
        if (!expanded) return false;
        int[] g = popupGeometry();
        return mx >= g[0] && mx < g[0] + g[2] && my >= g[1] && my < g[1] + g[3];
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
        float hp = Ease.outCubic(buttonHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        int border = expanded ? 0xFFFFCC00 : Fade.mix(0xFF4A4A4A, 0xFF888888, hp);
        int bg = Fade.mix(0xFF0D0D0D, 0xFF252525, hp);
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_HEIGHT, border);
        g.fill(buttonX + 1, buttonY + 1, buttonX + buttonW - 1, buttonY + BUTTON_HEIGHT - 1, bg);

        g.drawString(font, label(unit), buttonX + 4, buttonY + 5, 0xFFEEEEEE, false);

        DropdownChrome.drawCaret(g, buttonX + buttonW - 7, buttonY + BUTTON_HEIGHT / 2 - 1,
                Fade.mix(0xFF999999, 0xFFDDDDDD, hp), open.value());
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        float t = open.ramp(expanded ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
        DurationUnit[] all = DurationUnit.values();

        if (!DropdownChrome.begin(g, px, py, pw, ph, t, py < buttonY)) return;

        for (int i = 0; i < all.length; i++) {
            int rowY = py + POPUP_PAD + i * ROW_HEIGHT;
            boolean rowHovered = expanded && mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(rowHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            DropdownChrome.drawRowHighlight(g, px + 1, rowY, pw - 2, ROW_HEIGHT, rh);

            boolean selected = all[i] == unit;
            if (selected) g.fill(px + 1, rowY, px + 2, rowY + ROW_HEIGHT, 0xFFFFCC00);
            int textColor = selected ? 0xFFFFCC00 : 0xFFEEEEEE;
            g.drawString(font, label(all[i]), px + 5 + Math.round(rh * 2.0f), rowY + 4,
                    textColor, false);
        }
        DropdownChrome.end(g);
    }

    private static String label(DurationUnit u) {
        return switch (u) {
            case SECONDS -> Component.translatable("editor.historystages.temporary.unit.seconds").getString();
            case MINUTES -> Component.translatable("editor.historystages.temporary.unit.minutes").getString();
            case HOURS -> Component.translatable("editor.historystages.temporary.unit.hours").getString();
            case DAYS -> Component.translatable("editor.historystages.temporary.unit.days").getString();
        };
    }

    private int[] popupGeometry() {
        int pw = buttonW;
        int ph = DurationUnit.values().length * ROW_HEIGHT + POPUP_PAD * 2;
        int px = buttonX;
        int py = buttonY + BUTTON_HEIGHT + 2;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (px + pw > screenW - 4) px = screenW - pw - 4;
        if (px < 4) px = 4;
        if (py + ph > screenH - 4) py = buttonY - ph - 2;
        if (py < 4) py = 4;
        return new int[] { px, py, pw, ph };
    }

    /** @return true if the click was consumed (button toggle or row pick). */
    public boolean mouseClicked(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) {
            expanded = !expanded;
            playClick();
            return true;
        }
        if (!expanded) return false;

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            expanded = false;
            return false;
        }
        int idx = (int) ((my - py - POPUP_PAD) / ROW_HEIGHT);
        DurationUnit[] all = DurationUnit.values();
        if (idx >= 0 && idx < all.length) {
            DurationUnit picked = all[idx];
            if (picked != unit) {
                unit = picked;
                if (onChange != null) onChange.accept(unit);
            }
            expanded = false;
            playClick();
        }
        return true;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
