package net.bananemdnsa.historystages.api.editor.widget;
import net.bananemdnsa.historystages.api.editor.widget.FilterDropdown;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Reusable search field + funnel-icon filter dropdown, modelled on
 * {@code StyledButton}: every searchable list overlay constructs one of these
 * instead of hand-rolling the bar, key bindings, and clipboard handling.
 *
 * <p>The bar owns the text, the focus and Ctrl+A/C/V state, and an embedded
 * {@link FilterDropdown}. Hosts read the current text via {@link #getText()},
 * react to changes via {@link #onChange(Consumer)}, register filter toggles via
 * {@link #filters()}, and call {@link #setPosition(int, int, int)} once per
 * frame before {@link #render}.
 *
 * <p>The funnel button and popup are drawn as part of {@link #render}; the
 * popup is z-translated above the rest of the panel content so it can occlude
 * a grid that draws afterwards. Use {@link #isMouseOverFilterUi(double, double)}
 * to suppress underlying tooltips/hover effects when the filter UI is in the
 * way.
 */
public class SearchBar {
    public static final int HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private final FilterDropdown filterDropdown;
    private String text = "";
    private String placeholder;
    private boolean focused = true;
    private boolean allSelected = false;
    private int x, y, width;
    private Consumer<String> onChange;
    /**
     * When true the bar uses the editor's "light" chrome (semi-transparent white tint +
     * gold/grey bottom accent), matching {@code StageOverviewScreen} / the detail-screen
     * category search. Default is the popup widget's dark-inset look.
     */
    private boolean lightStyle = false;
    private final Anim hoverAnim = new Anim();
    private final Anim focusAnim = new Anim();

    public SearchBar(String placeholder) {
        this.placeholder = placeholder;
        this.filterDropdown = new FilterDropdown(() -> {
            if (onChange != null)
                onChange.accept(text);
        });
    }

    /** Access the embedded dropdown to register filter options. */
    public FilterDropdown filters() {
        return filterDropdown;
    }

    /**
     * Enables the editor-style "light" rendering — semi-transparent white tint, subtle
     * edge highlights, gold bottom accent when focused. Use in non-popup contexts (e.g.
     * the trigger editor) so the bar matches the surrounding editor chrome instead of
     * looking like a popup-overlay search field.
     */
    public SearchBar setLightStyle(boolean light) {
        this.lightStyle = light;
        return this;
    }

    public SearchBar onChange(Consumer<String> callback) {
        this.onChange = callback;
        return this;
    }

    public String getText() {
        return text;
    }

    /**
     * Updates the text and fires {@link #onChange}. Already-lowercased on set,
     * matching the pre-refactor convention of every searchable widget.
     */
    public void setText(String t) {
        if (t == null)
            t = "";
        this.text = t.toLowerCase();
        this.allSelected = false;
        if (onChange != null)
            onChange.accept(this.text);
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    /**
     * Position the bar. {@code width} is the full extent including the funnel
     * button — the search field itself is {@code width - BUTTON_SIZE - GAP}.
     */
    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    /**
     * Width of the text field itself. The filter button's space is only reserved when there are
     * filters to show — {@link FilterDropdown} already refuses to draw or click an empty
     * dropdown, so reserving it unconditionally left every filterless bar 22px of dead margin
     * that its own text then overflowed into.
     */
    private int searchFieldWidth() {
        return filterDropdown.hasOptions()
                ? width - FilterDropdown.BUTTON_SIZE - BUTTON_GAP
                : width;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int sx = x;
        int sy = y;
        int sw = searchFieldWidth();

        boolean hovered = mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + HEIGHT;
        float hp = Ease.outCubic(hoverAnim.ramp(focused || hovered,
                Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        // Focus is tracked apart from hover so the focus cue does not fire when the cursor
        // merely passes over the field.
        float fp = Ease.outCubic(focusAnim.ramp(focused, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        if (lightStyle) {
            int bgAlpha = (int) (0x25 + hp * 0x18);
            g.fill(sx, sy, sx + sw, sy + HEIGHT, (bgAlpha << 24) | 0xFFFFFF);
            // Top + side edge highlights (matches StyledButton / detail-screen search)
            g.fill(sx, sy, sx + sw, sy + 1, 0x20FFFFFF);
            g.fill(sx, sy, sx + 1, sy + HEIGHT, 0x15FFFFFF);
            g.fill(sx + sw - 1, sy, sx + sw, sy + HEIGHT, 0x15FFFFFF);
            // This style has no frame of its own, so the bottom accent is the only thing that
            // can carry state: grey at rest, gold once focused. Same accent StyledButton uses.
            int accentAlpha = (int) (0x40 + hp * 0x40);
            g.fill(sx, sy + HEIGHT - 2, sx + sw, sy + HEIGHT, (accentAlpha << 24) | 0x888888);
            if (fp > 0.001f) {
                g.fill(sx, sy + HEIGHT - 2, sx + sw, sy + HEIGHT, Fade.rgba(0xFFCC00, fp));
            }
        } else {
            // The boxed style already has a frame, so focus is carried by that frame turning
            // gold. An extra underline inside the box reads as a separate element sitting on
            // top of the field rather than as the field's own state.
            int resting = Fade.mix(0xFF4A4A4A, 0xFF888888, hp);
            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + HEIGHT + 1, Fade.mix(resting, 0xFFFFCC00, fp));
            g.fill(sx, sy, sx + sw, sy + HEIGHT, 0xFF0D0D0D);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        String displayText = text.isEmpty() ? "§7" + placeholder : text;
        int textW = font.width(text);
        // Text is clipped to the field and slides left once it outgrows it, so a long query
        // stays typeable with its caret in view instead of spilling out past the box.
        int textAvail = Math.max(0, sw - 8);
        int shift = Math.max(0, textW - textAvail);
        int textX = sx + 4 - shift;

        g.enableScissor(sx + 1, sy + 1, sx + sw - 1, sy + HEIGHT - 1);
        if (allSelected && !text.isEmpty()) {
            g.fill(textX - 1, sy + 3, textX + 1 + textW, sy + HEIGHT - 3, 0xFF4A6A9A);
        }
        int textColor = text.isEmpty() ? (lightStyle ? 0x999999 : 0x666666) : 0xFFFFFF;
        g.drawString(font, displayText, textX, sy + 6, textColor, false);
        if (focused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = textX + (text.isEmpty() ? 0 : textW);
            g.fill(cursorX, sy + 4, cursorX + 1, sy + HEIGHT - 4, 0xFFFFFFFF);
        }
        g.disableScissor();
        g.pose().popPose();

        int btnX = x + width - FilterDropdown.BUTTON_SIZE;
        int btnY = y + 1;
        filterDropdown.setButtonPosition(btnX, btnY);
        filterDropdown.renderButton(g, font, mouseX, mouseY);
        filterDropdown.renderPopup(g, font, mouseX, mouseY);
    }

    /**
     * Routes a click to the dropdown (with possible popup toggle/option
     * selection) or focuses the search field. Returns true if the click was
     * consumed; false leaves it for the host to handle (e.g. row selection).
     */
    public boolean mouseClicked(double mx, double my) {
        if (filterDropdown.mouseClicked(mx, my))
            return true;
        int sw = searchFieldWidth();
        if (mx >= x && mx < x + sw && my >= y && my < y + HEIGHT) {
            focused = true;
            return true;
        }
        return false;
    }

    /**
     * Handles ESC (closes popup if open), backspace, Ctrl+A/C/V. Returns true
     * if consumed. ESC with no popup open returns false so the host can hide
     * the overlay.
     */
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) { // ESC
            if (filterDropdown.isExpanded()) {
                filterDropdown.close();
                return true;
            }
            return false;
        }
        if (!focused)
            return false;
        if (keyCode == 259) { // BACKSPACE
            if (allSelected) {
                allSelected = false;
                setText("");
            } else if (!text.isEmpty()) {
                setText(text.substring(0, text.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 65) { // Ctrl+A
            if (!text.isEmpty())
                allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) { // Ctrl+C
            if (!text.isEmpty())
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 86) { // Ctrl+V
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                setText(allSelected ? clip : text + clip);
            }
            return true;
        }
        return false;
    }

    /**
     * Accepts ASCII letters/digits and a permissive set of separators
     * ({@code _ : . - / @ } and space) — a superset broad enough for resource
     * locations, file paths, tag IDs, mod names, and the {@code @mod} search
     * shorthand used by item/recipe pickers.
     */
    public boolean charTyped(char c) {
        if (!focused)
            return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' '
                || c == '-' || c == '@' || c == '/') {
            setText(allSelected ? String.valueOf(c) : text + c);
            return true;
        }
        return false;
    }

    public boolean isMouseOverFilterUi(double mx, double my) {
        return filterDropdown.isMouseOver(mx, my);
    }
}
