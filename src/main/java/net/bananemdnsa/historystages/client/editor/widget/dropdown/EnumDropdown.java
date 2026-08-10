package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generic dropdown for picking one constant out of a fixed list of names — the config editor's
 * control for graph.toml's enum rows (node shape, label mode, edge style, ...). Same chrome and
 * interaction as {@link DisplayModeDropdown}, generalised from a {@code DisplayMode[]} with a
 * hard-coded lang prefix to a {@code List<String>} of constant names with a caller-supplied
 * label function, so one dropdown class serves every enum type instead of one per type.
 *
 * <p>Render order: {@link #renderButton} during normal rendering, {@link #renderPopup}
 * AFTER all other widgets, and route clicks through {@link #mouseClicked} BEFORE other
 * widgets.</p>
 */
public class EnumDropdown {
    public static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int POPUP_PAD = 2;

    private final List<String> options;
    private final Function<String, Component> labelFn;
    private final Consumer<String> onChange;
    private String current;
    private int buttonX, buttonY, buttonW;
    private boolean expanded = false;

    /** Reveal progress of the popup; also drives the caret turning over. */
    private final Anim open = new Anim();
    private final Anim buttonHover = new Anim();
    private final Map<Integer, Anim> rowHover = new HashMap<>();

    public EnumDropdown(List<String> options, String initial, int minWidth,
                         Function<String, Component> labelFn, Consumer<String> onChange) {
        this.options = options;
        this.labelFn = labelFn;
        this.current = (initial != null && options.contains(initial)) ? initial : options.get(0);
        this.buttonW = computeWidth(options, labelFn, minWidth);
        this.onChange = onChange;
    }

    /**
     * Widest label across all options (+padding), so the box is never narrower than its content.
     *
     * <p>Public because a config row draws the collapsed button itself and has to arrive at the
     * same width: the popup that opens on top of it is positioned from this.
     */
    public static int computeWidth(List<String> options, Function<String, Component> labelFn, int minWidth) {
        Font font = Minecraft.getInstance().font;
        int w = minWidth;
        for (String o : options) {
            int rowW = font.width(labelFn.apply(o).getString()) + 16;
            if (rowW > w) w = rowW;
        }
        return w;
    }

    public String getValue() { return current; }

    /**
     * Sets the selection without notifying {@code onChange} — for a screen that reuses one instance
     * across rows and has to push the row's own value in before showing it. Unknown values are
     * ignored rather than clearing the selection.
     */
    public void setValue(String value) {
        if (value != null && options.contains(value)) current = value;
    }
    public boolean isExpanded() { return expanded; }
    public void close() { expanded = false; }

    /**
     * Opens the popup without a click on the button. The config editor needs this because its
     * rows are the button: the click that opens the picker lands on the row, not on a widget
     * this class drew.
     */
    public void expand() { expanded = true; }

    /** Width the widget actually renders at (>= the minWidth passed to the constructor). */
    public int getWidth() { return buttonW; }

    public void setPosition(int x, int y) { this.buttonX = x; this.buttonY = y; }

    public boolean isMouseOver(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) return true;
        if (!expanded) return false;
        int[] g = popupGeometry();
        return mx >= g[0] && mx < g[0] + g[2] && my >= g[1] && my < g[1] + g[3];
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
        float hp = Ease.outCubic(buttonHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        DropdownChrome.drawButton(g, font, buttonX, buttonY, buttonW, BUTTON_HEIGHT,
                labelFn.apply(current).getString(), hp, expanded, open.value());
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        // Kept rendering past the click that closed it, so the popup rolls back up instead of
        // vanishing. open drives both the reveal here and the caret in renderButton.
        float t = open.ramp(expanded ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (!DropdownChrome.begin(g, px, py, pw, ph, t, py < buttonY)) return;

        for (int i = 0; i < options.size(); i++) {
            int rowY = py + POPUP_PAD + i * ROW_HEIGHT;
            boolean rowHovered = expanded && mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(rowHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            DropdownChrome.drawRowHighlight(g, px + 1, rowY, pw - 2, ROW_HEIGHT, rh);
            String option = options.get(i);
            int textColor = option.equals(current) ? 0xFFFFCC00 : 0xFFEEEEEE;
            g.drawString(font, labelFn.apply(option), px + 5 + Math.round(rh * 2.0f), rowY + 5,
                    textColor, false);
        }
        DropdownChrome.end(g);
    }

    /**
     * The option the cursor is over inside the open popup, or null when the popup is closed or
     * the cursor is elsewhere. Lets a screen put a per-option hover tooltip on the picker
     * without this class having to own tooltip state — the appear delay belongs to the screen,
     * since it has to restart when the cursor moves between unrelated elements.
     */
    public String hoveredOption(double mx, double my) {
        if (!expanded) return null;
        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) return null;
        int idx = (int) ((my - py - POPUP_PAD) / ROW_HEIGHT);
        return (idx >= 0 && idx < options.size()) ? options.get(idx) : null;
    }

    private int[] popupGeometry() {
        int pw = buttonW;
        int ph = options.size() * ROW_HEIGHT + POPUP_PAD * 2;
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
        if (idx >= 0 && idx < options.size()) {
            String picked = options.get(idx);
            if (!picked.equals(current)) {
                current = picked;
                if (onChange != null) onChange.accept(current);
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
