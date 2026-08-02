package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable funnel-icon button + checkbox dropdown for filter toggles.
 *
 * Each searchable list owns one of these and registers its own {@link Option}s
 * via {@link #addOption}. Options sharing a non-null {@code groupId} are
 * mutually exclusive (radio behaviour); options with {@code groupId == null}
 * are independent checkboxes. The owning widget reads filter state through
 * {@link #isActive} when narrowing its result set, and re-runs its filter
 * pipeline from the {@code onChange} callback wired in the constructor.
 */
public class FilterDropdown {
    public static final int BUTTON_SIZE = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int POPUP_PAD = 4;
    private static final int MIN_POPUP_WIDTH = 110;

    public static class Option {
        public final String id;
        public final String label;
        /** When non-null, only one option per group can be active at a time. */
        public final String groupId;
        public boolean active;

        public Option(String id, String label, String groupId, boolean active) {
            this.id = id;
            this.label = label;
            this.groupId = groupId;
            this.active = active;
        }
    }

    private final List<Option> options = new ArrayList<>();
    private final Runnable onChange;
    private boolean expanded = false;
    private int buttonX, buttonY;

    /** Reveal progress of the popup. */
    private final Anim open = new Anim();
    private final Anim buttonHover = new Anim();
    private final Map<Integer, Anim> rowHover = new HashMap<>();
    /** Per-option tick progress, so a checkbox fills in rather than flipping. */
    private final Map<Integer, Anim> checkAnim = new HashMap<>();

    public FilterDropdown(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Register a filter toggle. {@code groupId} = null for independent, non-null for mutex group. */
    public FilterDropdown addOption(String id, String label, String groupId) {
        return addOption(id, label, groupId, false);
    }

    /** Register a filter toggle with an explicit initial-active state. */
    public FilterDropdown addOption(String id, String label, String groupId, boolean initialActive) {
        options.add(new Option(id, label, groupId, initialActive));
        return this;
    }

    public boolean isActive(String id) {
        for (Option opt : options) {
            if (opt.id.equals(id))
                return opt.active;
        }
        return false;
    }

    public boolean hasActiveFilters() {
        for (Option opt : options) {
            if (opt.active)
                return true;
        }
        return false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void close() {
        expanded = false;
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    /**
     * @return true if {@code (mx, my)} hits the funnel button or the open
     *         popup. Use to suppress underlying tooltips/hover effects in the
     *         hosting widget.
     */
    public boolean isMouseOver(double mx, double my) {
        if (options.isEmpty())
            return false;
        if (mx >= buttonX && mx < buttonX + BUTTON_SIZE && my >= buttonY && my < buttonY + BUTTON_SIZE)
            return true;
        if (!expanded)
            return false;
        int[] geom = popupGeometry(Minecraft.getInstance().font);
        return mx >= geom[0] && mx < geom[0] + geom[2] && my >= geom[1] && my < geom[1] + geom[3];
    }

    /** Position the funnel button. Call before render each frame. */
    public void setButtonPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }

    public int getButtonX() {
        return buttonX;
    }

    public int getButtonY() {
        return buttonY;
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (options.isEmpty())
            return;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
        boolean active = hasActiveFilters();
        float hp = Ease.outCubic(buttonHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        int border = active ? 0xFFFFCC00 : Fade.mix(0xFF4A4A4A, 0xFF888888, hp);
        int bg = active ? 0xFF2A2510 : Fade.mix(0xFF0D0D0D, 0xFF252525, hp);
        g.fill(buttonX, buttonY, buttonX + BUTTON_SIZE, buttonY + BUTTON_SIZE, border);
        g.fill(buttonX + 1, buttonY + 1, buttonX + BUTTON_SIZE - 1, buttonY + BUTTON_SIZE - 1, bg);

        int color = active ? 0xFFFFCC00 : Fade.mix(0xFF999999, 0xFFDDDDDD, hp);
        int cx = buttonX + BUTTON_SIZE / 2;
        int top = buttonY + 4;
        g.fill(cx - 5, top, cx + 5, top + 1, color);
        g.fill(cx - 4, top + 1, cx + 4, top + 2, color);
        g.fill(cx - 3, top + 2, cx + 3, top + 3, color);
        g.fill(cx - 2, top + 3, cx + 2, top + 4, color);
        g.fill(cx - 1, top + 4, cx + 1, top + 10, color);

        if (active) {
            g.fill(buttonX + BUTTON_SIZE - 5, buttonY + 2, buttonX + BUTTON_SIZE - 2, buttonY + 5, 0xFFFFCC00);
        }
    }

    /** Renders the dropdown popup. Call AFTER all other UI so it draws on top. */
    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (options.isEmpty()) return;

        // Kept rendering past the click that closed it, so the popup rolls back up.
        float t = open.ramp(expanded ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        int[] geom = popupGeometry(font);
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (!DropdownChrome.begin(g, px, py, pw, ph, t, py < buttonY)) return;

        for (int i = 0; i < options.size(); i++) {
            Option opt = options.get(i);
            int rowY = py + POPUP_PAD + i * ROW_HEIGHT;
            boolean rowHovered = expanded && mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(rowHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            DropdownChrome.drawRowHighlight(g, px + 2, rowY, pw - 4, ROW_HEIGHT, rh);

            // The tick grows in instead of appearing, which makes it obvious which box the
            // click actually landed on when several rows sit close together.
            float check = Ease.outCubic(checkAnim.computeIfAbsent(i, k -> new Anim())
                    .ramp(opt.active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            int boxX = px + 6;
            int boxY = rowY + 3;
            int boxSize = 10;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, Fade.mix(0xFF666666, 0xFFFFCC00, check));
            g.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1,
                    Fade.mix(0xFF0D0D0D, 0xFF2A2510, check));
            if (check > 0.001f) {
                int tick = Fade.rgba(0xFFCC00, check);
                // Drawn left to right so the stroke reads as being written on.
                int steps = Math.max(1, Math.round(5 * check));
                if (steps > 0) g.fill(boxX + 3, boxY + 5, boxX + 4, boxY + 8, tick);
                if (steps > 1) g.fill(boxX + 4, boxY + 6, boxX + 5, boxY + 9, tick);
                if (steps > 2) g.fill(boxX + 5, boxY + 5, boxX + 6, boxY + 8, tick);
                if (steps > 3) g.fill(boxX + 6, boxY + 4, boxX + 7, boxY + 7, tick);
                if (steps > 4) g.fill(boxX + 7, boxY + 3, boxX + 8, boxY + 6, tick);
            }
            g.drawString(font, opt.label, boxX + boxSize + 5 + Math.round(rh * 2.0f), rowY + 4,
                    0xFFEEEEEE, false);
        }
        DropdownChrome.end(g);
    }

    private int[] popupGeometry(Font font) {
        int pw = MIN_POPUP_WIDTH;
        for (Option opt : options) {
            int w = font.width(opt.label) + 32;
            if (w > pw)
                pw = w;
        }
        int ph = options.size() * ROW_HEIGHT + POPUP_PAD * 2;
        // Right-align under button; flip up if it would go off-screen.
        int px = buttonX + BUTTON_SIZE - pw;
        int py = buttonY + BUTTON_SIZE + 2;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (px < 4)
            px = 4;
        if (px + pw > screenW - 4)
            px = screenW - pw - 4;
        if (py + ph > screenH - 4)
            py = buttonY - ph - 2;
        if (py < 4)
            py = 4;
        return new int[] { px, py, pw, ph };
    }

    /**
     * @return true if the click was consumed (button toggle or option click);
     *         false otherwise. When false and the popup was open, it is closed
     *         as a side-effect — the caller may continue handling the click.
     */
    public boolean mouseClicked(double mx, double my) {
        if (options.isEmpty())
            return false;

        if (mx >= buttonX && mx < buttonX + BUTTON_SIZE && my >= buttonY && my < buttonY + BUTTON_SIZE) {
            expanded = !expanded;
            playClick();
            return true;
        }
        if (!expanded)
            return false;

        Font font = Minecraft.getInstance().font;
        int[] geom = popupGeometry(font);
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            expanded = false;
            return false;
        }

        int idx = (int) ((my - py - POPUP_PAD) / ROW_HEIGHT);
        if (idx >= 0 && idx < options.size()) {
            toggleOption(idx);
            playClick();
        }
        return true;
    }

    private void toggleOption(int idx) {
        Option opt = options.get(idx);
        boolean newState = !opt.active;
        opt.active = newState;
        if (newState && opt.groupId != null) {
            for (Option other : options) {
                if (other != opt && opt.groupId.equals(other.groupId)) {
                    other.active = false;
                }
            }
        }
        if (onChange != null)
            onChange.run();
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
