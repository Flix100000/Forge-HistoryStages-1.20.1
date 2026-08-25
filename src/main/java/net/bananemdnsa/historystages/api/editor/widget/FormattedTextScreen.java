package net.bananemdnsa.historystages.api.editor.widget;

import net.bananemdnsa.historystages.client.editor.widget.dialog.FormatTextArea;

import net.bananemdnsa.historystages.api.editor.widget.InputValues;

import net.bananemdnsa.historystages.api.editor.widget.InputField;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog for the mod's "special" text values — the ones carrying format codes and placeholders:
 * tooltip lines, chat message formats, descriptions.
 *
 * <p>It exists because those values are edited blind everywhere else. A one-line
 * {@code EditBox} cuts the text off past its width, format codes are invisible punctuation until
 * the value is saved and seen in game, and the placeholders a given field accepts are something
 * you either remember or go looking for in a config comment.
 *
 * <p>So: the field wraps rather than scrolls, a preview underneath shows the value rendered, and
 * the codes and placeholders are buttons. Clicking a format button with text selected wraps the
 * selection; with nothing selected it inserts the code at the caret, which is what the code does
 * anyway — it runs until something resets it.
 *
 * <p>Codes are written as {@code &}, not {@code §}. That is the convention the mod's message
 * formats already use and the only one a player can type by hand.
 */
public class FormattedTextScreen extends AbstractInputScreen {

    /** Bounds for how many wrapped lines the edit area shows; the actual count fits the window. */
    private static final int AREA_LINES_MIN = 3;
    private static final int AREA_LINES_MAX = 7;
    private static final int PREVIEW_LINES = 2;

    private static final int BLOCK_LABEL_H = 11;
    private static final int BLOCK_GAP = 7;
    private static final int CHIP_H = 14;
    private static final int CHIP_GAP = 4;
    private static final int SWATCH = 14;

    /** Space kept above the content so the first field's frame is not clipped by the scissor. */
    private static final int FRAME_HEADROOM = 1;

    /** Marker for the line-break button; not a format code, handled separately in {@link #activate}. */
    private static final String NEWLINE = String.valueOf('\n');

    /** The format codes offered, in the order they are drawn. */
    private static final List<FormatCode> FORMATS = List.of(
            new FormatCode(NEWLINE, "editor.historystages.rich.newline"),
            new FormatCode("&l", "editor.historystages.rich.bold"),
            new FormatCode("&o", "editor.historystages.rich.italic"),
            new FormatCode("&n", "editor.historystages.rich.underline"),
            new FormatCode("&m", "editor.historystages.rich.strike"),
            new FormatCode("&r", "editor.historystages.rich.reset"));

    private record FormatCode(String code, String labelKey) {}

    /** The sixteen colours a {@code &} code can express, with the code character each needs. */
    private static final List<ChatFormatting> COLOURS = List.of(
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA, ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY,
            ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW,
            ChatFormatting.WHITE);

    private final String initial;
    private final String hint;
    private final List<String> placeholders;
    private final Consumer<String> onDone;

    private final FormatTextArea area;
    private final Map<String, Anim> chipHover = new HashMap<>();
    private final EditorTooltip tooltip = new EditorTooltip();

    /** Placeholder the cursor is on, and the explanation to show for it. */
    private String hoveredPlaceholder;

    /** How far the content is scrolled, and the band it is clipped to. Set during render. */
    private int contentScroll;
    private int bandTop;
    private int bandBottom;

    /** Hit rects of everything clickable in the extra content, filled during render. */
    private final Map<String, int[]> hitRects = new HashMap<>();

    public FormattedTextScreen(Screen parent, Component title, String initial, String hint,
                               List<String> placeholders, Consumer<String> onDone) {
        super(parent, title);
        this.initial = initial == null ? "" : initial;
        this.hint = hint == null ? "" : hint;
        this.placeholders = placeholders == null ? List.of() : List.copyOf(placeholders);
        this.onDone = onDone;
        this.area = new FormatTextArea(net.minecraft.client.Minecraft.getInstance().font, 256);
        this.area.setValue(this.initial);
        this.area.setHint(this.hint);
        this.area.setFocused(true);
    }

    /** Wider than the default dialog: this one is all about seeing the whole value at once. */
    @Override
    protected int dialogWidth() {
        return Math.min(360, this.width - 40);
    }

    @Override
    protected List<InputField> fields() {
        return List.of();
    }

    @Override
    protected void onConfirm(InputValues values) {
        if (onDone != null) onDone.accept(area.getValue());
        this.minecraft.setScreen(parent);
    }

    // --- layout ---

    /**
     * How many wrapped lines the edit area shows. Chosen to fit the window rather than fixed:
     * {@code AbstractModalScreen} centres the dialog around whatever height is declared here, so
     * declaring more than fits pushes the title and the buttons off both ends of the screen.
     * Text past the visible lines is still reachable — the area scrolls.
     */
    private int areaLines() {
        int lineH = this.font.lineHeight + 1;
        int room = availableContentHeight() - blocksBelowAreaHeight() - 6;
        return Mth.clamp(room / lineH, AREA_LINES_MIN, AREA_LINES_MAX);
    }

    /**
     * True while the cursor sits over the visible content band. Used to keep the placeholder
     * tooltip from appearing for a chip that has been scrolled out of sight.
     */
    private boolean inBand(int mouseY) {
        return mouseY >= bandTop && mouseY < bandBottom;
    }

    /**
     * Room the modal leaves for {@link #extraContentHeight()}. Mirrors
     * {@code boxH = TITLE_H + PAD + contentHeight() + PAD + BUTTON_H + PAD} and keeps a margin
     * so the dialog never sits flush against the window edge.
     */
    private int availableContentHeight() {
        int chrome = TITLE_H + PAD + PAD + BUTTON_H + PAD + ERROR_H + 12;
        return Math.max(60, this.height - chrome - 24);
    }

    private int blocksBelowAreaHeight() {
        int h = BLOCK_GAP;
        h += BLOCK_LABEL_H + previewHeight() + BLOCK_GAP;
        if (!placeholders.isEmpty()) {
            h += BLOCK_LABEL_H + chipRows() * (CHIP_H + CHIP_GAP) + BLOCK_GAP;
        }
        h += BLOCK_LABEL_H + CHIP_H + BLOCK_GAP;
        h += BLOCK_LABEL_H + SWATCH + BLOCK_GAP;
        return h;
    }

    private int areaHeight() {
        return areaLines() * (this.font.lineHeight + 1) + 6;
    }

    private int previewHeight() {
        return PREVIEW_LINES * (this.font.lineHeight + 1) + 6;
    }

    private int chipRows() {
        if (placeholders.isEmpty()) return 0;
        int perRow = chipsPerRow();
        return (placeholders.size() + perRow - 1) / perRow;
    }

    /**
     * How many chips share a row. Three where they fit, fewer when a token is too long for a
     * third of the dialog — the chip label is drawn centred and unclipped, so a token wider than
     * its chip runs out over its neighbours.
     */
    private int chipsPerRow() {
        int w = dialogWidth() - PAD * 2;
        int widest = 0;
        for (String placeholder : placeholders) {
            widest = Math.max(widest, this.font.width(placeholder));
        }
        int perRow = 3;
        // The same arithmetic renderChips does, stepped down until the widest token fits.
        while (perRow > 1 && (w - CHIP_GAP * (perRow - 1)) / perRow < widest + 8) perRow--;
        return perRow;
    }

    /** Everything the content wants, whether or not the window can show it at once. */
    private int fullContentHeight() {
        return FRAME_HEADROOM + areaHeight() + BLOCK_GAP + blocksBelowAreaHeight();
    }

    /**
     * Height the dialog reserves. Capped at what the window can hold: the modal centres itself
     * around this number, so a taller one pushes the title and the buttons off both ends of the
     * screen instead of overflowing into empty space. Anything past the cap is reached by
     * scrolling {@link #contentScroll}.
     */
    @Override
    protected int extraContentHeight() {
        return Math.min(fullContentHeight(), availableContentHeight());
    }

    private int maxContentScroll() {
        return Math.max(0, fullContentHeight() - extraContentHeight());
    }

    // --- rendering ---

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        hitRects.clear();
        hoveredPlaceholder = null;

        contentScroll = Mth.clamp(contentScroll, 0, maxContentScroll());
        bandTop = y;
        bandBottom = y + extraContentHeight();

        // Everything is drawn at its real screen position, shifted by the scroll — so the hit
        // rects recorded below are already in screen space and the click paths need no second
        // correction. The scissor is what keeps the scrolled-away parts from painting over the
        // dialog's title and buttons.
        g.enableScissor(x - 2, bandTop, x + w + 2, bandBottom);
        // One pixel of headroom: every field draws its frame at cy - 1, which would otherwise
        // land just above the scissor and lose its top edge — most visible on the focused text
        // area, whose gold frame simply had no top line.
        int cy = y + FRAME_HEADROOM - contentScroll;

        area.setBounds(x, cy, w, areaHeight());
        g.fill(x - 1, cy - 1, x + w + 1, cy + areaHeight() + 1,
                area.isFocused() ? ACCENT_GOLD : FIELD_BORDER);
        g.fill(x, cy, x + w, cy + areaHeight(), FIELD_BG);
        area.render(g, mouseX, mouseY);
        cy += areaHeight() + BLOCK_GAP;

        cy = drawLabel(g, "editor.historystages.rich.preview", x, cy);
        g.fill(x - 1, cy - 1, x + w + 1, cy + previewHeight() + 1, FIELD_BORDER);
        g.fill(x, cy, x + w, cy + previewHeight(), 0xFF0A0A0A);
        renderPreview(g, x + 3, cy + 3, w - 6);
        cy += previewHeight() + BLOCK_GAP;

        if (!placeholders.isEmpty()) {
            cy = drawLabel(g, "editor.historystages.rich.placeholders", x, cy);
            cy = renderChips(g, placeholders, "ph:", x, cy, w, mouseX, mouseY);
            cy += BLOCK_GAP;
        }

        cy = drawLabel(g, "editor.historystages.rich.format", x, cy);
        cy = renderFormatButtons(g, x, cy, mouseX, mouseY);
        cy += BLOCK_GAP;

        drawLabel(g, "editor.historystages.rich.colors", x, cy);
        renderColours(g, x, cy + BLOCK_LABEL_H, w, mouseX, mouseY);

        g.disableScissor();

        if (maxContentScroll() > 0) {
            drawScrollHint(g, x + w + 3, bandTop, bandBottom);
        }

        if (hoveredPlaceholder != null) {
            String key = placeholderHelpKey(hoveredPlaceholder);
            String help = Component.translatable(key).getString();
            // A missing key renders as the key itself; showing that helps nobody.
            if (!help.equals(key)) {
                tooltip.render(g, this.font, hoveredPlaceholder, help,
                        mouseX, mouseY, this.width, this.height);
            }
        }
    }

    /** Slim bar beside the content, showing how much of it is on screen. */
    private void drawScrollHint(GuiGraphics g, int x, int top, int bottom) {
        int track = bottom - top;
        int thumb = Math.max(12, track * track / Math.max(1, fullContentHeight()));
        int offset = maxContentScroll() == 0 ? 0
                : (track - thumb) * contentScroll / maxContentScroll();
        g.fill(x, top, x + 2, bottom, 0x20FFFFFF);
        g.fill(x, top + offset, x + 2, top + offset + thumb, 0x80FFFFFF);
    }

    private int drawLabel(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key).getString(), x, y, LABEL_GREY, false);
        return y + BLOCK_LABEL_H;
    }

    /**
     * The value as the game would show it: {@code &} becomes {@code §}, which is the same
     * conversion the mod does when it sends one of these strings.
     */
    private void renderPreview(GuiGraphics g, int x, int y, int w) {
        String rendered = area.getValue().replace('&', '§');
        if (rendered.isEmpty()) {
            g.drawString(this.font, Component.translatable("editor.historystages.rich.preview_empty")
                    .getString(), x, y, 0xFF555555, false);
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> wrapped =
                this.font.split(Component.literal(rendered), w);
        int ly = y;
        for (int i = 0; i < Math.min(PREVIEW_LINES, wrapped.size()); i++) {
            g.drawString(this.font, wrapped.get(i), x, ly, 0xFFFFFFFF, false);
            ly += this.font.lineHeight + 1;
        }
    }

    private int renderChips(GuiGraphics g, List<String> labels, String prefix,
                           int x, int y, int w, int mouseX, int mouseY) {
        int perRow = chipsPerRow();
        int chipW = (w - CHIP_GAP * (perRow - 1)) / perRow;
        int cy = y;
        for (int i = 0; i < labels.size(); i++) {
            int col = i % perRow;
            if (col == 0 && i > 0) cy += CHIP_H + CHIP_GAP;
            int cx = x + col * (chipW + CHIP_GAP);
            drawChip(g, prefix + labels.get(i), labels.get(i), cx, cy, chipW, mouseX, mouseY);
            if (mouseX >= cx && mouseX < cx + chipW && mouseY >= cy && mouseY < cy + CHIP_H
                    && inBand(mouseY)) {
                hoveredPlaceholder = labels.get(i);
            }
        }
        return cy + CHIP_H + CHIP_GAP;
    }

    /**
     * Explanation for a placeholder chip, looked up by the token's bare name — {@code %stage%}
     * becomes {@code editor.historystages.rich.ph.stage}. Resolving it here rather than making
     * every caller pass descriptions keeps the widget usable with one list of tokens; a token
     * with no key simply gets no tooltip.
     */
    private static String placeholderHelpKey(String token) {
        // Both placeholder styles the mod uses: %icon% in the tooltip templates, {stage} in the
        // chat message formats. The bare name is what the lang key is built from either way.
        return "editor.historystages.rich.ph."
                + token.replace("%", "").replace("{", "").replace("}", "").trim();
    }

    private int renderFormatButtons(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int cx = x;
        for (FormatCode format : FORMATS) {
            String label = Component.translatable(format.labelKey()).getString();
            int cw = this.font.width(label) + 10;
            drawChip(g, "fmt:" + format.code(), label, cx, y, cw, mouseX, mouseY);
            cx += cw + CHIP_GAP;
        }
        return y + CHIP_H + CHIP_GAP;
    }

    private void renderColours(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int cx = x;
        for (ChatFormatting colour : COLOURS) {
            if (cx + SWATCH > x + w) break;
            boolean hovered = mouseX >= cx && mouseX < cx + SWATCH
                    && mouseY >= y && mouseY < y + SWATCH;
            hitRects.put("col:" + colour.getChar(), new int[]{cx, y, SWATCH, SWATCH});

            Integer rgb = colour.getColor();
            g.fill(cx, y, cx + SWATCH, y + SWATCH, hovered ? 0xFFFFCC00 : 0xFF3D3D3D);
            g.fill(cx + 1, y + 1, cx + SWATCH - 1, y + SWATCH - 1,
                    0xFF000000 | (rgb == null ? 0 : rgb));
            cx += SWATCH + 2;
        }
    }

    private void drawChip(GuiGraphics g, String id, String label, int x, int y, int w,
                         int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CHIP_H;
        hitRects.put(id, new int[]{x, y, w, CHIP_H});

        float hp = Ease.outCubic(chipHover.computeIfAbsent(id, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(x, y, x + w, y + CHIP_H, Fade.mix(0x18FFFFFF, 0x35FFFFFF, hp));
        g.drawCenteredString(this.font, label, x + w / 2, y + 3,
                Fade.mix(0xFFAAAAAA, 0xFFFFFFFF, hp));
    }

    // --- input ---

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (my < bandTop || my >= bandBottom) return false;
        if (area.mouseClicked(mx, my, button)) return true;

        for (Map.Entry<String, int[]> hit : hitRects.entrySet()) {
            int[] r = hit.getValue();
            if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) continue;
            activate(hit.getKey());
            return true;
        }
        return false;
    }

    /** Applies whatever chip or swatch was clicked, then hands focus back to the text. */
    private void activate(String id) {
        if (id.startsWith("ph:")) {
            area.insert(id.substring(3));
        } else if (id.startsWith("fmt:")) {
            String code = id.substring(4);
            if (NEWLINE.equals(code)) {
                area.insert(NEWLINE);
                area.setFocused(true);
                return;
            }
            // Reset is a single marker, never a pair — wrapping a selection in two resets would
            // strip the formatting the player is trying to end.
            if ("&r".equals(code)) {
                area.insert(code);
            } else {
                area.wrapSelection(code, "&r");
            }
        } else if (id.startsWith("col:")) {
            area.wrapSelection("&" + id.substring(4), "&r");
        }
        area.setFocused(true);
    }

    @Override
    protected boolean extraContentMouseDragged(double mx, double my, int button) {
        return area.mouseDragged(mx, my);
    }

    @Override
    protected boolean extraContentMouseScrolled(double mx, double my, double scrollY) {
        if (area.mouseScrolled(mx, my, scrollY)) return true;
        if (maxContentScroll() > 0 && my >= bandTop && my < bandBottom) {
            contentScroll = Mth.clamp(contentScroll - (int) (scrollY * 12), 0, maxContentScroll());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (area.keyPressed(keyCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (area.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }
}
