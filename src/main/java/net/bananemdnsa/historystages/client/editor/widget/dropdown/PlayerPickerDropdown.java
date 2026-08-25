package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Player picker for the editor's individual-stage section. Collapsed it shows the
 * selected player's face and name; expanded it shows a search row plus "@a" and every
 * online player in a scrollable list. The popup keeps the button's x and width and
 * opens straight down, flipping straight up when it would leave the screen.
 *
 * <p>Render order: {@link #renderButton} during normal rendering, {@link #renderPopup}
 * AFTER all other widgets, and route clicks through {@link #mouseClicked} BEFORE other
 * widgets. While {@link #isExpanded()}, route typing through {@link #charTyped} and
 * {@link #keyPressed} so the host screen's own search box stays out of it.</p>
 */
public class PlayerPickerDropdown {

    public static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int SEARCH_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 7;
    private static final int POPUP_PAD = 2;
    private static final int FACE_SIZE = 10;
    private static final int SCROLLBAR_W = 3;
    private static final int MAX_SEARCH_LENGTH = 32;
    /** GLFW key codes, matching the literals used elsewhere in the editor screens. */
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_A = 65;
    private static final int KEY_C = 67;
    private static final int KEY_V = 86;

    /** One pickable row. A null uuid is the "@a" entry. */
    public record Entry(UUID uuid, String name) {}

    private final int buttonW;
    private int buttonX, buttonY;
    private boolean expanded = false;
    private String search = "";
    /** Ctrl+A select-all state, same convention as {@code SearchBar}: the next typed
     *  character or paste replaces the whole text, backspace clears it. */
    private boolean allSelected = false;
    private int scrollRow = 0;
    /** Reveal progress of the popup; also drives the caret turning over. */
    private final Anim open = new Anim();
    private final Anim buttonHover = new Anim();
    /** Per-slot hover progress. Keyed by visible slot, not entry — the list scrolls under it. */
    private final Map<Integer, Anim> rowHover = new HashMap<>();
    /** Currently picked target; null means "@a". */
    private UUID selected;
    private List<Entry> filtered = new ArrayList<>();

    public PlayerPickerDropdown(int width) {
        this.buttonW = width;
        Minecraft mc = Minecraft.getInstance();
        this.selected = mc.player != null ? mc.player.getUUID() : null;
    }

    /** The picked player, or null for "@a". */
    public UUID getSelected() { return selected; }

    public boolean isExpanded() { return expanded; }

    public void close() { expanded = false; }

    public int getWidth() { return buttonW; }

    public void setPosition(int x, int y) { this.buttonX = x; this.buttonY = y; }

    public boolean isMouseOver(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) return true;
        if (!expanded) return false;
        int[] g = popupGeometry();
        return mx >= g[0] && mx < g[0] + g[2] && my >= g[1] && my < g[1] + g[3];
    }

    // --- data -------------------------------------------------------------

    private static List<Entry> onlinePlayers() {
        List<Entry> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return list;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            list.add(new Entry(info.getProfile().getId(), info.getProfile().getName()));
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }

    /**
     * Rebuilds the visible rows from the tab list and the search string. Also drops a
     * selection whose player logged out, falling back to the local player.
     */
    private void refresh() {
        List<Entry> online = onlinePlayers();
        if (selected != null && online.stream().noneMatch(e -> selected.equals(e.uuid()))) {
            Minecraft mc = Minecraft.getInstance();
            selected = mc.player != null ? mc.player.getUUID() : null;
        }

        String query = search.toLowerCase(Locale.ROOT).trim();
        List<Entry> rows = new ArrayList<>();
        // "@a" matches both the selector itself and its translated label, so searching
        // "all" (or "alle") finds it too.
        if (query.isEmpty() || allShortLabel().toLowerCase(Locale.ROOT).startsWith(query)
                || allLabel().toLowerCase(Locale.ROOT).contains(query)) {
            rows.add(new Entry(null, allShortLabel()));
        }
        for (Entry e : online) {
            if (query.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(query)) rows.add(e);
        }
        filtered = rows;

        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        if (scrollRow > maxScroll) scrollRow = maxScroll;
    }

    /** Single place that writes the search text, so the selection and scroll never go stale. */
    private void setSearch(String text) {
        if (text == null) text = "";
        search = text.length() > MAX_SEARCH_LENGTH ? text.substring(0, MAX_SEARCH_LENGTH) : text;
        allSelected = false;
        scrollRow = 0;
        refresh();
    }

    private static String allLabel() {
        return Component.translatable("editor.historystages.player_picker.all").getString();
    }

    private static String allShortLabel() {
        return Component.translatable("editor.historystages.player_picker.all_short").getString();
    }

    private String selectedLabel() {
        if (selected == null) return allShortLabel();
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(selected) : null;
        return info != null ? info.getProfile().getName() : allShortLabel();
    }

    // --- rendering --------------------------------------------------------

    /** Face for a player, or a gold star for the "@a" entry. */
    private static void drawIcon(GuiGraphics g, Font font, UUID uuid, int x, int y) {
        if (uuid == null) {
            g.drawString(font, "★", x + 1, y + 1, 0xFFFFCC00, false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
        if (info != null) {
            PlayerFaceRenderer.draw(g, info.getSkin().texture(), x, y, FACE_SIZE);
        }
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        refresh();
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
        float hp = Ease.outCubic(buttonHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        int border = expanded ? 0xFFFFCC00 : Fade.mix(0xFF4A4A4A, 0xFF888888, hp);
        int bg = Fade.mix(0xFF0D0D0D, 0xFF252525, hp);
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_HEIGHT, border);
        g.fill(buttonX + 1, buttonY + 1, buttonX + buttonW - 1, buttonY + BUTTON_HEIGHT - 1, bg);

        drawIcon(g, font, selected, buttonX + 4, buttonY + (BUTTON_HEIGHT - FACE_SIZE) / 2);

        int textX = buttonX + 4 + FACE_SIZE + 4;
        int textRight = buttonX + buttonW - 12;
        g.enableScissor(textX, buttonY, textRight, buttonY + BUTTON_HEIGHT);
        g.drawString(font, selectedLabel(), textX, buttonY + 5, 0xFFEEEEEE, false);
        g.disableScissor();

        DropdownChrome.drawCaret(g, buttonX + buttonW - 8, buttonY + BUTTON_HEIGHT / 2 - 1,
                Fade.mix(0xFF999999, 0xFFDDDDDD, hp), open.value());
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        float t = open.ramp(expanded ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (!DropdownChrome.begin(g, px, py, pw, ph, t, py < buttonY)) return;

        String shown = search.isEmpty()
                ? Component.translatable("editor.historystages.player_picker.search").getString()
                : search;
        g.enableScissor(px + 1, py, px + pw - 1, py + SEARCH_HEIGHT);
        if (allSelected && !search.isEmpty()) {
            g.fill(px + 4, py + 3, px + 6 + font.width(search), py + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }
        g.drawString(font, shown, px + 5, py + 4, search.isEmpty() ? 0xFF666666 : 0xFFEEEEEE, false);
        // The search row is always the keyboard target while the popup is open, so the
        // caret blinks unconditionally — there is no separate focus to win first. It is
        // hidden while everything is selected, matching SearchBar.
        if (!allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = px + 5 + font.width(search);
            g.fill(caretX, py + 3, caretX + 1, py + 13, 0xFFFFCC00);
        }
        g.disableScissor();
        g.fill(px + 1, py + SEARCH_HEIGHT, px + pw - 1, py + SEARCH_HEIGHT + 1, 0xFF333333);

        int listTop = py + SEARCH_HEIGHT + POPUP_PAD;
        int visible = Math.min(visibleRows(), filtered.size());
        for (int i = 0; i < visible; i++) {
            Entry entry = filtered.get(scrollRow + i);
            int rowY = listTop + i * ROW_HEIGHT;
            boolean rowHovered = expanded && mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(rowHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            DropdownChrome.drawRowHighlight(g, px + 1, rowY, pw - 2, ROW_HEIGHT, rh);

            drawIcon(g, font, entry.uuid(), px + 4, rowY + (ROW_HEIGHT - FACE_SIZE) / 2);

            String label = entry.uuid() == null
                    ? Component.translatable("editor.historystages.player_picker.all").getString()
                    : entry.name();
            int textX = px + 4 + FACE_SIZE + 4;
            int textRight = px + pw - SCROLLBAR_W - 4;
            g.enableScissor(textX, rowY, textRight, rowY + ROW_HEIGHT);
            g.drawString(font, label, textX, rowY + 5,
                    Objects.equals(entry.uuid(), selected) ? 0xFFFFCC00 : 0xFFEEEEEE, false);
            g.disableScissor();
        }

        if (filtered.size() > visible) {
            int trackH = visible * ROW_HEIGHT;
            int sbX = px + pw - SCROLLBAR_W - 2;
            g.fill(sbX, listTop, sbX + SCROLLBAR_W, listTop + trackH, 0x20FFFFFF);
            int thumbH = Math.max(10, trackH * visible / filtered.size());
            int maxScroll = filtered.size() - visible;
            int thumbY = listTop + (trackH - thumbH) * scrollRow / Math.max(1, maxScroll);
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0x80FFFFFF);
        }

        DropdownChrome.end(g);
    }

    /**
     * How many rows the popup shows. Capped by {@link #VISIBLE_ROWS} and by whatever
     * vertical space the roomier side of the button offers, so the popup always fits
     * on screen and never has to be clamped on top of its own button — which would
     * make the covered rows unclickable.
     */
    private int visibleRows() {
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int chrome = SEARCH_HEIGHT + POPUP_PAD * 2;
        int below = screenH - 4 - (buttonY + BUTTON_HEIGHT + 2) - chrome;
        int above = (buttonY - 2) - 4 - chrome;
        int fits = Math.max(below, above) / ROW_HEIGHT;
        return Math.max(1, Math.min(VISIBLE_ROWS, fits));
    }

    /**
     * {x, y, w, h} of the popup. Same x and width as the button — deliberately never
     * shifted horizontally — directly below it, flipped directly above when it would
     * run off the bottom of the screen.
     */
    private int[] popupGeometry() {
        int pw = buttonW;
        int rows = Math.min(visibleRows(), Math.max(1, filtered.size()));
        int ph = SEARCH_HEIGHT + rows * ROW_HEIGHT + POPUP_PAD * 2;
        int px = buttonX;
        int py = buttonY + BUTTON_HEIGHT + 2;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (py + ph > screenH - 4) py = buttonY - ph - 2;
        if (py < 4) py = 4;
        return new int[] { px, py, pw, ph };
    }

    // --- input ------------------------------------------------------------

    /** @return true if the click was consumed. */
    public boolean mouseClicked(double mx, double my) {
        // The popup is tested before the button: on a short screen it can be flipped up
        // over the button, and what the user sees on top has to be what they hit.
        if (expanded) {
            int[] geom = popupGeometry();
            int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
            if (mx >= px && mx < px + pw && my >= py && my < py + ph) {
                int listTop = py + SEARCH_HEIGHT + POPUP_PAD;
                if (my < listTop) return true; // search row — nothing to pick, stays open

                int visible = Math.min(visibleRows(), filtered.size());
                int row = (int) ((my - listTop) / ROW_HEIGHT);
                // The popup's bottom padding sits below the last drawn row; a click there
                // must not resolve to the next, off-screen entry.
                if (row < 0 || row >= visible) return true;

                selected = filtered.get(scrollRow + row).uuid();
                expanded = false;
                playClick();
                return true;
            }
        }

        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) {
            expanded = !expanded;
            if (expanded) setSearch("");
            playClick();
            return true;
        }

        // A click anywhere outside just closes the popup, and is swallowed so it does
        // not also hit the stage row underneath.
        if (expanded) {
            expanded = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!expanded) return false;
        int[] geom = popupGeometry();
        if (mx < geom[0] || mx >= geom[0] + geom[2] || my < geom[1] || my >= geom[1] + geom[3]) return false;
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - (int) Math.signum(scrollY)));
        return true;
    }

    /**
     * Accepts the same character set as {@link net.bananemdnsa.historystages.api.editor.widget.SearchBar}
     * — letters, digits and the separators that show up in player names. Returns false
     * for anything it does not consume, so the host screen stays in control.
     */
    public boolean charTyped(char c) {
        if (!expanded) return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '@' || c == ' ') {
            if (allSelected) {
                setSearch(String.valueOf(c));
            } else if (search.length() < MAX_SEARCH_LENGTH) {
                setSearch(search + c);
            }
            return true;
        }
        return false;
    }

    /**
     * Handles ESC, backspace and Ctrl+A/Ctrl+V. Everything else falls through — swallowing
     * unhandled keys here would also swallow the key events that produce typed characters.
     */
    public boolean keyPressed(int keyCode) {
        if (!expanded) return false;
        if (keyCode == KEY_ESCAPE) {
            expanded = false;
            return true;
        }
        if (keyCode == KEY_BACKSPACE) {
            if (allSelected) {
                setSearch("");
            } else if (!search.isEmpty()) {
                setSearch(search.substring(0, search.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == KEY_A) {
            // Select all — the text stays put until the next keystroke replaces it.
            if (!search.isEmpty()) allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == KEY_C) {
            if (!search.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(search);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                setSearch(allSelected ? clip : search + clip);
            }
            return true;
        }
        return false;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
