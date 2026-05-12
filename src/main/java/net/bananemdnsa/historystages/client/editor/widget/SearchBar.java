package net.bananemdnsa.historystages.client.editor.widget;

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

    private int searchFieldWidth() {
        return width - FilterDropdown.BUTTON_SIZE - BUTTON_GAP;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int sx = x;
        int sy = y;
        int sw = searchFieldWidth();

        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + HEIGHT + 1, 0xFF4A4A4A);
        g.fill(sx, sy, sx + sw, sy + HEIGHT, 0xFF0D0D0D);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        String displayText = text.isEmpty() ? "§7" + placeholder : text;
        if (allSelected && !text.isEmpty()) {
            int textW = font.width(text);
            g.fill(sx + 3, sy + 3, sx + 5 + textW, sy + HEIGHT - 3, 0xFF4A6A9A);
        }
        g.drawString(font, displayText, sx + 4, sy + 6, text.isEmpty() ? 0x666666 : 0xFFFFFF, false);
        if (focused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = sx + 4 + (text.isEmpty() ? 0 : font.width(text));
            g.fill(cursorX, sy + 4, cursorX + 1, sy + HEIGHT - 4, 0xFFFFFFFF);
        }
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
