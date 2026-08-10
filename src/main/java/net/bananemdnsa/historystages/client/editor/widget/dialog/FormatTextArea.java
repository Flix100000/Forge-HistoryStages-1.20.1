package net.bananemdnsa.historystages.client.editor.widget.dialog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A text field that wraps instead of scrolling sideways, so a long format string is readable in
 * full while it is being edited.
 *
 * <p>The value stays <em>one logical line</em>: there is no newline character and Enter does not
 * insert one. Wrapping is purely how it is drawn. That is deliberate — the fields this edits are
 * things like one tooltip line or one chat message format, where a real newline would either be
 * meaningless or silently split the value into something the consumer cannot use.
 *
 * <p>Vanilla's {@code MultiLineEditBox} looks like the obvious choice and is not: it keeps its
 * cursor and selection inside a private field, and {@link #wrapSelection} needs both to put the
 * format codes around what the player highlighted.
 */
public final class FormatTextArea {

    private static final int CURSOR_BLINK_MS = 600;

    private static final int TEXT_COLOUR = 0xFFFFFFFF;
    private static final int HINT_COLOUR = 0xFF707070;
    private static final int SELECTION_COLOUR = 0x803060C0;
    private static final int CURSOR_COLOUR = 0xFFD0D0D0;

    private final Font font;
    private final int maxLength;

    private int x;
    private int y;
    private int width;
    private int height;

    private String value = "";
    private String hint = "";
    private Consumer<String> onChange = v -> {};

    /** Caret position as an index into {@link #value}. */
    private int cursor;
    /** Other end of the selection; equal to {@link #cursor} when nothing is selected. */
    private int anchor;

    private boolean focused;
    private long focusedAt = System.currentTimeMillis();

    /** First wrapped line drawn, so a value taller than the field can still be reached. */
    private int scrollLine;

    /** Start index of each wrapped display line, recomputed whenever the value or width changes. */
    private final List<Integer> lineStarts = new ArrayList<>();
    private String wrappedFor = null;
    private int wrappedWidth = -1;

    public FormatTextArea(Font font, int maxLength) {
        this.font = font;
        this.maxLength = maxLength;
    }

    // --- configuration ---

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setHint(String hint) {
        this.hint = hint == null ? "" : hint;
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange == null ? v -> {} : onChange;
    }

    public void setValue(String v) {
        this.value = v == null ? "" : v;
        if (this.value.length() > maxLength) this.value = this.value.substring(0, maxLength);
        this.cursor = this.value.length();
        this.anchor = this.cursor;
        invalidateWrap();
    }

    public String getValue() {
        return value;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        this.focusedAt = System.currentTimeMillis();
    }

    public boolean isFocused() {
        return focused;
    }

    // --- editing entry points used by the dialog's buttons ---

    /**
     * Wraps the selection in {@code open}/{@code close}, or — when nothing is selected — inserts
     * {@code open} at the caret so the formatting applies from there on.
     *
     * <p>Both behaviours are what Minecraft's format codes actually do: a code runs until it is
     * reset or the string ends, so "make this bit bold" is a pair and "bold from here" is a single
     * code. Which one the player meant is exactly whether they highlighted something.
     */
    public void wrapSelection(String open, String close) {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        if (from == to) {
            insert(open);
            return;
        }
        String selected = value.substring(from, to);
        String replacement = open + selected + close;
        if (value.length() - selected.length() + replacement.length() > maxLength) return;
        value = value.substring(0, from) + replacement + value.substring(to);
        cursor = from + replacement.length();
        anchor = cursor;
        changed();
    }

    /** Appends text at the caret, replacing the selection if there is one. */
    public void insert(String text) {
        if (text == null || text.isEmpty()) return;
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        int room = maxLength - (value.length() - (to - from));
        if (room <= 0) return;
        String fitted = text.length() > room ? text.substring(0, room) : text;
        value = value.substring(0, from) + fitted + value.substring(to);
        cursor = from + fitted.length();
        anchor = cursor;
        changed();
    }

    // --- rendering ---

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        rewrapIfNeeded();
        clampScroll();

        int lineH = font.lineHeight + 1;
        g.enableScissor(x, y, x + width, y + height);

        if (value.isEmpty() && !hint.isEmpty()) {
            g.drawString(font, hint, x + 3, y + 3, HINT_COLOUR, false);
        }

        for (int line = scrollLine; line < lineStarts.size(); line++) {
            int start = lineStarts.get(line);
            int end = lineEnd(line);
            int ly = y + 3 + (line - scrollLine) * lineH;
            if (ly + lineH > y + height) break;

            drawSelection(g, line, start, end, ly, lineH);
            g.drawString(font, value.substring(start, end), x + 3, ly, TEXT_COLOUR, false);
        }

        if (focused && ((System.currentTimeMillis() - focusedAt) / CURSOR_BLINK_MS) % 2 == 0) {
            int line = lineOf(cursor);
            if (line >= scrollLine && line < scrollLine + visibleLines()) {
                int cx = x + 3 + font.width(value.substring(lineStarts.get(line), cursor));
                int cy = y + 3 + (line - scrollLine) * lineH;
                g.fill(cx, cy - 1, cx + 1, cy + font.lineHeight, CURSOR_COLOUR);
            }
        }

        // A hint that there is more text above or below, so a value longer than the field does
        // not look like it simply ends where the field does.
        if (scrollLine > 0) {
            g.fill(x + width - 5, y + 2, x + width - 2, y + 3, 0xFF808080);
        }
        if (scrollLine + visibleLines() < lineStarts.size()) {
            g.fill(x + width - 5, y + height - 3, x + width - 2, y + height - 2, 0xFF808080);
        }

        g.disableScissor();
    }

    private int visibleLines() {
        return Math.max(1, (height - 6) / (font.lineHeight + 1));
    }

    /** Keeps the caret's line on screen and the view inside the text. */
    private void clampScroll() {
        int visible = visibleLines();
        int maxScroll = Math.max(0, lineStarts.size() - visible);
        int cursorLine = lineOf(cursor);
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + visible) scrollLine = cursorLine - visible + 1;
        scrollLine = Mth.clamp(scrollLine, 0, maxScroll);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY)) return false;
        rewrapIfNeeded();
        int maxScroll = Math.max(0, lineStarts.size() - visibleLines());
        scrollLine = Mth.clamp(scrollLine - (int) Math.signum(scrollY), 0, maxScroll);
        return true;
    }

    private void drawSelection(GuiGraphics g, int line, int start, int end, int ly, int lineH) {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        if (from == to) return;
        int selStart = Math.max(from, start);
        int selEnd = Math.min(to, end);
        if (selStart >= selEnd) return;

        int sx = x + 3 + font.width(value.substring(start, selStart));
        int ex = x + 3 + font.width(value.substring(start, selEnd));
        g.fill(sx, ly - 1, ex, ly + lineH - 1, SELECTION_COLOUR);
    }

    // --- wrapping ---

    private void invalidateWrap() {
        wrappedFor = null;
    }

    private void rewrapIfNeeded() {
        if (value.equals(wrappedFor) && wrappedWidth == width) return;
        wrappedFor = value;
        wrappedWidth = width;
        lineStarts.clear();
        lineStarts.add(0);

        int usable = Math.max(20, width - 6);
        int lineStart = 0;
        int lastBreak = -1;
        for (int i = 0; i < value.length(); i++) {
            // A real line break always ends the line, whatever else would fit on it.
            if (value.charAt(i) == '\n') {
                lineStarts.add(i + 1);
                lineStart = i + 1;
                lastBreak = -1;
                continue;
            }
            if (value.charAt(i) == ' ') lastBreak = i;
            if (font.width(value.substring(lineStart, i + 1)) <= usable) continue;

            // Break after the last space when there is one, so words stay whole; mid-word only
            // when a single word is itself wider than the field.
            int breakAt = lastBreak > lineStart ? lastBreak + 1 : i;
            if (breakAt <= lineStart) breakAt = lineStart + 1;
            lineStarts.add(breakAt);
            lineStart = breakAt;
            lastBreak = -1;
            i = breakAt - 1;
        }
    }

    private int lineEnd(int line) {
        return line + 1 < lineStarts.size() ? lineStarts.get(line + 1) : value.length();
    }

    private int lineOf(int index) {
        for (int line = lineStarts.size() - 1; line >= 0; line--) {
            if (index >= lineStarts.get(line)) return line;
        }
        return 0;
    }

    /** Total drawn height of the wrapped text, so the dialog can size itself around it. */
    public int contentHeight() {
        rewrapIfNeeded();
        return lineStarts.size() * (font.lineHeight + 1) + 6;
    }

    // --- input ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        setFocused(true);
        cursor = indexAt(mouseX, mouseY);
        anchor = cursor;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!focused) return false;
        cursor = indexAt(mouseX, mouseY);
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int indexAt(double mouseX, double mouseY) {
        rewrapIfNeeded();
        int lineH = font.lineHeight + 1;
        int line = Mth.clamp((int) ((mouseY - y - 3) / lineH) + scrollLine, 0, lineStarts.size() - 1);
        int start = lineStarts.get(line);
        int end = lineEnd(line);

        int best = start;
        double bestDist = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            double cx = x + 3 + font.width(value.substring(start, i));
            double dist = Math.abs(cx - mouseX);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    public boolean charTyped(char c) {
        if (!focused || !net.minecraft.util.StringUtil.isAllowedChatCharacter(c)) return false;
        insert(String.valueOf(c));
        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (!focused) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (net.minecraft.client.gui.screens.Screen.isSelectAll(keyCode)) {
            anchor = 0;
            cursor = value.length();
            return true;
        }
        if (net.minecraft.client.gui.screens.Screen.isCopy(keyCode)) {
            copySelection();
            return true;
        }
        if (net.minecraft.client.gui.screens.Screen.isCut(keyCode)) {
            copySelection();
            deleteSelection();
            return true;
        }
        if (net.minecraft.client.gui.screens.Screen.isPaste(keyCode)) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard().replace("\n", " "));
            return true;
        }

        switch (keyCode) {
            // Shift+Enter breaks the line; plain Enter is left to the dialog, which confirms on
            // it. A line break is the rarer of the two, so it takes the modifier.
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!shift) return false;
                insert("\n");
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor != anchor) {
                    deleteSelection();
                } else if (cursor > 0) {
                    value = value.substring(0, cursor - 1) + value.substring(cursor);
                    cursor--;
                    anchor = cursor;
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor != anchor) {
                    deleteSelection();
                } else if (cursor < value.length()) {
                    value = value.substring(0, cursor) + value.substring(cursor + 1);
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(Math.max(0, cursor - 1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(Math.min(value.length(), cursor + 1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveCursor(verticalTarget(-1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveCursor(verticalTarget(1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(lineStarts.get(lineOf(cursor)), shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(lineEnd(lineOf(cursor)), shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private int verticalTarget(int delta) {
        rewrapIfNeeded();
        int line = lineOf(cursor);
        int target = line + delta;
        if (target < 0 || target >= lineStarts.size()) return cursor;
        int column = cursor - lineStarts.get(line);
        return Math.min(lineStarts.get(target) + column, lineEnd(target));
    }

    private void moveCursor(int to, boolean keepSelection) {
        cursor = Mth.clamp(to, 0, value.length());
        if (!keepSelection) anchor = cursor;
        focusedAt = System.currentTimeMillis();
    }

    private void copySelection() {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        if (from == to) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(from, to));
    }

    private void deleteSelection() {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        if (from == to) return;
        value = value.substring(0, from) + value.substring(to);
        cursor = from;
        anchor = from;
        changed();
    }

    private void changed() {
        invalidateWrap();
        focusedAt = System.currentTimeMillis();
        onChange.accept(value);
    }
}
