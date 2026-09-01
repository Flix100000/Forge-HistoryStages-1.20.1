package net.bananemdnsa.historystages.api.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A modal list of named choices: a title, one row per option, a click runs that option's action.
 *
 * <p>The third way to ask a question in this editor. {@link AbstractSearchableList} asks "which of
 * these hundreds of ids", {@link CountInputScreen} asks "how many" — neither fits "which of these
 * four", which until now meant hand-drawing a pair of buttons and hand-drawing them again for the
 * next question that had five.
 *
 * <p>A {@link PickerOverlay}, so a host that can already show a searchable list can show this with
 * no new plumbing. An addon reaching this through {@link net.bananemdnsa.historystages.api.editor.TriggerEditor}
 * wants {@link ChoiceScreen} instead, which is this in a screen of its own.
 *
 * <p>Draws no backdrop of its own: the host dims behind every modal it shows, and a second layer
 * here made the panel visibly darker than the searchable lists beside it.
 */
public final class ChoiceOverlay implements PickerOverlay {

    private static final int ROW_H = 18;
    private static final int PAD = 10;
    /** Distance from the panel top to the first row: title plus its separator. */
    private static final int HEADER_H = 26;
    private static final int MIN_W = 170;
    /** Room reserved on the right for the chevron a submenu row draws. */
    private static final int CHEVRON_W = 12;
    /** How far the panel rises into place while it appears. */
    private static final float RISE_PX = 6.0f;

    private static final int PANEL_BG = 0xFF1A1A1A;
    private static final int PANEL_BORDER = 0xFF555555;
    private static final int ACCENT_GOLD = 0xFFCC00;
    private static final int SEPARATOR = 0x40FFFFFF;
    private static final int LABEL = 0xEEEEEE;
    private static final int TITLE = 0xFFFFFF;

    /**
     * One row: what it says, whether picking it leads to another step, and what it does.
     *
     * <p>The submenu flag is drawn as a chevron. It is worth the field: a flow that asks three
     * questions in a row — pick a statistic category, then an id, then a count — otherwise shows a
     * first panel whose rows look final, and a row that looks final but opens another panel is the
     * kind of surprise that makes people stop trusting the first click.
     */
    public record Option(String label, boolean opensMore, Runnable onPick) {

        /** A row that finishes the job. */
        public static Option of(String label, Runnable onPick) {
            return new Option(label, false, onPick);
        }

        /** A row that opens another panel, dialog or screen. */
        public static Option more(String label, Runnable onPick) {
            return new Option(label, true, onPick);
        }
    }

    private final String title;
    private final List<Option> options;

    private boolean visible = false;
    /** Row the keyboard is on, and the one Enter picks. -1 while the cursor is driving. */
    private int selected = -1;
    private int centerX;
    private int centerY;
    private final Anim reveal = new Anim();
    private final Map<Integer, Anim> rowHover = new HashMap<>();

    /**
     * Panel rectangle from the last render, used to hit-test clicks.
     *
     * <p>{@link #mouseClicked} is handed no font, and the panel is only as wide as its longest
     * label — so the geometry has to come from the last frame. Safe because nothing can be clicked
     * before it has been drawn.
     */
    private int lastX, lastY, lastW, lastH;

    public ChoiceOverlay(String title, List<Option> options) {
        this.title = title == null ? "" : title;
        this.options = List.copyOf(options);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code parentWidth} is ignored: this panel is sized by its longest label, not by the
     * screen it sits on.
     */
    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.rowHover.clear();
        this.selected = -1;
        this.reveal.set(0.0f);
        this.visible = true;
    }

    @Override
    public void hide() {
        this.visible = false;
        this.selected = -1;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    /** Nothing to filter — the rows are a fixed, named set. */
    @Override
    public void setFilter(String filter) {
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;
        float t = Ease.outCubic(reveal.ramp(1.0f, Timing.MODAL_MS));

        int w = Math.max(MIN_W, font.width(title) + PAD * 2);
        for (Option o : options) w = Math.max(w, font.width(o.label()) + PAD * 2 + CHEVRON_W);
        int h = HEADER_H + options.size() * ROW_H + PAD - 4;
        int x = centerX - w / 2;
        // Rises into place rather than simply appearing, so the panel reads as having been opened
        // by the click that opened it.
        int y = centerY - h / 2 + Math.round((1.0f - t) * RISE_PX);
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Fade.alpha(PANEL_BORDER, t));
        g.fill(x, y, x + w, y + h, Fade.alpha(PANEL_BG, t));
        g.fill(x, y, x + w, y + 1, Fade.rgba(ACCENT_GOLD, t));
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 8, Fade.rgba(TITLE, t), false);
        g.fill(x + PAD, y + HEADER_H - 5, x + w - PAD, y + HEADER_H - 4, Fade.alpha(SEPARATOR, t));

        for (int i = 0; i < options.size(); i++) {
            int rowY = y + HEADER_H + i * ROW_H;
            boolean hot = i == selected || overRow(mouseX, mouseY, x, w, rowY);
            float hp = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hot, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)) * t;
            DropdownChrome.drawRowHighlight(g, x + 1, rowY, w - 2, ROW_H, hp);

            Option option = options.get(i);
            g.drawString(font, option.label(), x + PAD + Math.round(hp * 2.0f),
                    rowY + (ROW_H - 8) / 2, Fade.rgba(LABEL, t), false);
            if (option.opensMore()) {
                drawChevron(g, x + w - PAD, rowY + ROW_H / 2 - 2,
                        Fade.rgba(Fade.mix(0xFF777777, 0xFFDDDDDD, hp) & 0xFFFFFF, t));
            }
        }
    }

    private boolean overRow(double mx, double my, int x, int w, int rowY) {
        return mx >= x && mx <= x + w && my >= rowY && my < rowY + ROW_H;
    }

    /** The 3x5 right-pointing arrow that marks a row leading to another step. */
    private static void drawChevron(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 5, color);
        g.fill(x + 1, y + 1, x + 2, y + 4, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A click outside the panel closes it. The overlay hides itself <em>before</em> running the
     * option, because an option is allowed to open the next one.
     *
     * @return true whenever the overlay is visible; it is modal and swallows every click.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        for (int i = 0; i < options.size(); i++) {
            if (overRow(mouseX, mouseY, lastX, lastW, lastY + HEADER_H + i * ROW_H)) {
                pick(i);
                return true;
            }
        }
        if (mouseX < lastX || mouseX > lastX + lastW
                || mouseY < lastY || mouseY > lastY + lastH) {
            hide();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseReleased() {
        return false;
    }

    /** Nothing scrolls: the row list is short by construction. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Arrow keys move the selection, Enter takes it, ESC closes.
     *
     * @return true whenever the overlay is visible; it swallows every key while it is up.
     */
    @Override
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        switch (keyCode) {
            case 256 -> hide();                                     // ESC
            case 264 -> move(1);                                    // DOWN
            case 265 -> move(-1);                                   // UP
            case 257, 335 -> { if (selected >= 0) pick(selected); } // ENTER, KP_ENTER
            default -> { }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c) {
        return false;
    }

    private void move(int delta) {
        if (options.isEmpty()) return;
        // From "nothing selected", down lands on the first row and up on the last.
        int from = selected < 0 ? (delta > 0 ? -1 : 0) : selected;
        selected = Math.floorMod(from + delta, options.size());
    }

    private void pick(int index) {
        Runnable onPick = options.get(index).onPick();
        hide();
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        onPick.run();
    }
}
