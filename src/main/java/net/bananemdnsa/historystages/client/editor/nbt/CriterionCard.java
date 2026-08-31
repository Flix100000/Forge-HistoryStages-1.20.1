package net.bananemdnsa.historystages.client.editor.nbt;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one criterion as a card: badge and name on top, the description under it, and the value
 * editor below that.
 *
 * <p>Layout, drawing and hit-testing all read the same {@link Built} piece list, which is the point
 * of the class. The screen it replaced computed its row geometry three times — once while drawing,
 * once while measuring the scroll height and once while testing clicks — and the three had drifted
 * apart, which is why the value fields never lined up.
 */
public final class CriterionCard {

    public sealed interface Hit {
        record Remove() implements Hit {}
        record EditValue() implements Hit {}
        record EditKey() implements Hit {}
        record EditLine(int index) implements Hit {}
        record EditLevel(int index) implements Hit {}
        record RemoveLine(int index) implements Hit {}
        record AddLine() implements Hit {}
        record ConvertLegacy() implements Hit {}
        record FillFromItem() implements Hit {}
    }

    /** A laid-out card: what to draw, where, and what a click there means. */
    public record Built(List<Piece> pieces, int width, int height) {}

    public record Piece(int x, int y, int w, int h, PieceKind kind,
                        String text, String hint, int color, Hit hit) {}

    public enum PieceKind { BADGE, TITLE, SUBTITLE, TEXT, FIELD, REMOVE, ADD, LINK }

    private static final int PAD_X = 8;
    private static final int PAD_Y = 7;
    private static final int HEADER_H = 10;
    private static final int LINE_H = 10;
    private static final int FIELD_H = 18;
    private static final int ROW_GAP = 5;
    private static final int GAP = 6;
    private static final int REMOVE_W = 10;
    private static final int LEVEL_W = 44;

    public static final int CARD_BG = 0xFF181818;
    public static final int CARD_BORDER = 0xFF2F2F2F;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFF777777;
    private static final int DESC_COLOR = 0xFF8A8A8A;
    private static final int WARN_COLOR = 0xFFFF9955;
    private static final int LINK_COLOR = 0xFFFFCC00;
    private static final int FIELD_BG = 0xFF0D0D0D;
    private static final int FIELD_BORDER = 0xFF4A4A4A;
    private static final int FIELD_BORDER_HOVER = 0xFF6A6A6A;
    private static final int FIELD_TEXT = 0xFFCCCCCC;
    private static final int FIELD_HINT = 0xFF555555;
    private static final int REMOVE_COLOR = 0xFF777777;
    private static final int REMOVE_HOVER = 0xFFFF6666;
    private static final int ADD_COLOR = 0xFF888888;

    private final Font font;

    public CriterionCard(Font font) {
        this.font = font;
    }

    // =============================================
    // Layout
    // =============================================

    /**
     * Lays the criterion out at the given width. Coordinates are card-local: (0,0) is the card's
     * top-left corner, so the same {@link Built} can be drawn anywhere the scroll offset puts it.
     */
    public Built layout(NbtCriterion criterion, int width,
                        List<NbtCriteriaValidator.Warning> warnings) {
        List<Piece> pieces = new ArrayList<>();
        int available = Math.max(20, width - PAD_X * 2);
        int y = PAD_Y;

        y = layoutHeader(pieces, criterion, available, y);
        y = layoutDescription(pieces, criterion, available, y);
        y = layoutValue(pieces, criterion, available, y, warnings);

        return new Built(pieces, width, y + PAD_Y);
    }

    private int layoutHeader(List<Piece> pieces, NbtCriterion criterion, int available, int y) {
        String badge = badgeText(criterion);
        int badgeW = font.width(badge) + 6;
        pieces.add(new Piece(PAD_X, y, badgeW, HEADER_H, PieceKind.BADGE,
                badge, null, badgeColor(criterion), null));

        int x = PAD_X + badgeW + GAP;
        int removeX = PAD_X + available - REMOVE_W;
        // Everything between the badge and the remove button, minus a gap so the two never touch.
        int room = removeX - GAP - x;

        String title = titleOf(criterion);
        String subtitle = subtitle(criterion);
        boolean showSubtitle = subtitle != null
                && font.width(title) + GAP + font.width(subtitle) <= room;

        String shownTitle = showSubtitle ? title : fit(title, room);
        pieces.add(new Piece(x, y + 1, font.width(shownTitle), 8, PieceKind.TITLE,
                shownTitle, null, TITLE_COLOR, null));

        if (showSubtitle) {
            int subX = x + font.width(title) + GAP;
            pieces.add(new Piece(subX, y + 1, font.width(subtitle), 8, PieceKind.SUBTITLE,
                    subtitle, null, SUBTITLE_COLOR, null));
        }

        pieces.add(new Piece(removeX, y, REMOVE_W, HEADER_H, PieceKind.REMOVE,
                "x", null, REMOVE_COLOR, new Hit.Remove()));

        return y + HEADER_H + 4;
    }

    /** Shortens text to fit the given width, with an ellipsis. Never returns something wider. */
    private String fit(String text, int room) {
        if (room <= 0) return "";
        if (font.width(text) <= room) return text;
        int ellipsis = font.width("...");
        if (room <= ellipsis) return "";
        return font.plainSubstrByWidth(text, room - ellipsis) + "...";
    }

    private int layoutDescription(List<Piece> pieces, NbtCriterion criterion, int available, int y) {
        if (criterion instanceof CustomDataCriterion custom && custom.legacySuspect) {
            String componentId = NbtPresets.componentForLegacyKey(custom.key);
            y = layoutWrapped(pieces, Component.translatable(
                    "editor.historystages.nbt.legacy.hint", componentId), available, y, WARN_COLOR);

            String convert = Component.translatable("editor.historystages.nbt.legacy.convert").getString();
            pieces.add(new Piece(PAD_X, y, font.width(convert), 8, PieceKind.LINK,
                    convert, null, LINK_COLOR, new Hit.ConvertLegacy()));
            return y + LINE_H + 2;
        }

        return layoutWrapped(pieces, description(criterion), available, y, DESC_COLOR) + 2;
    }

    private int layoutValue(List<Piece> pieces, NbtCriterion criterion, int available, int y,
                            List<NbtCriteriaValidator.Warning> warnings) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            int idW = available - LEVEL_W - GAP - REMOVE_W - GAP;
            for (int i = 0; i < ench.lines.size(); i++) {
                EnchantmentListCriterion.Line line = ench.lines.get(i);
                pieces.add(new Piece(PAD_X, y, idW, FIELD_H, PieceKind.FIELD, line.id,
                        Component.translatable("editor.historystages.nbt.enchantment_id_hint").getString(),
                        FIELD_TEXT, new Hit.EditLine(i)));
                pieces.add(new Piece(PAD_X + idW + GAP, y, LEVEL_W, FIELD_H, PieceKind.FIELD,
                        line.level, "1", FIELD_TEXT, new Hit.EditLevel(i)));
                pieces.add(new Piece(PAD_X + available - REMOVE_W, y + (FIELD_H - REMOVE_W) / 2,
                        REMOVE_W, REMOVE_W, PieceKind.REMOVE, "x", null, REMOVE_COLOR,
                        new Hit.RemoveLine(i)));
                y += FIELD_H + ROW_GAP;
                y = layoutWarnings(pieces, warnings, i, available, y);
            }
            return layoutAddLine(pieces, y);
        }

        if (criterion instanceof TextListCriterion list) {
            int fieldW = available - REMOVE_W - GAP;
            for (int i = 0; i < list.lines.size(); i++) {
                pieces.add(new Piece(PAD_X, y, fieldW, FIELD_H, PieceKind.FIELD, list.lines.get(i),
                        Component.translatable("editor.historystages.nbt.click_to_edit").getString(),
                        FIELD_TEXT, new Hit.EditLine(i)));
                pieces.add(new Piece(PAD_X + available - REMOVE_W, y + (FIELD_H - REMOVE_W) / 2,
                        REMOVE_W, REMOVE_W, PieceKind.REMOVE, "x", null, REMOVE_COLOR,
                        new Hit.RemoveLine(i)));
                y += FIELD_H + ROW_GAP;
            }
            return layoutAddLine(pieces, y);
        }

        if (criterion instanceof CustomDataCriterion custom) {
            int keyW = (available - GAP) * 2 / 5;
            pieces.add(new Piece(PAD_X, y, keyW, FIELD_H, PieceKind.FIELD, custom.key,
                    Component.translatable("editor.historystages.nbt.custom.key_hint").getString(),
                    FIELD_TEXT, new Hit.EditKey()));
            pieces.add(new Piece(PAD_X + keyW + GAP, y, available - keyW - GAP, FIELD_H,
                    PieceKind.FIELD, custom.valueText,
                    Component.translatable("editor.historystages.nbt.custom.value_hint").getString(),
                    FIELD_TEXT, new Hit.EditValue()));
            y += FIELD_H;
            return layoutWarnings(pieces, warnings, -1, available, y + 2);
        }

        ComponentCriterion comp = (ComponentCriterion) criterion;
        if (comp.valueKind == ValueKind.PRESENCE) {
            // Nothing to type: the criterion asks whether the component is there at all, so a
            // field would only invite a value that changes nothing.
            return layoutWrapped(pieces, Component.translatable("editor.historystages.nbt.presence"),
                    available, y, SUBTITLE_COLOR);
        }
        pieces.add(new Piece(PAD_X, y, available, FIELD_H, PieceKind.FIELD, comp.displayValue(),
                valueHint(comp.valueKind), FIELD_TEXT, new Hit.EditValue()));
        y += FIELD_H;

        // Raw JSON is the one shape nobody can guess — least of all for a mod's component, where
        // the encoded form is whatever that mod's codec produces. Reading it off a real item is
        // the only answer that is right by construction.
        if (comp.valueKind == ValueKind.JSON) {
            String label = Component.translatable("editor.historystages.nbt.fill_from_item").getString();
            pieces.add(new Piece(PAD_X, y + 4, font.width(label) + 12, 12, PieceKind.ADD,
                    label, null, ADD_COLOR, new Hit.FillFromItem()));
            y += 16;
        }
        return y;
    }

    private static String valueHint(ValueKind kind) {
        String key = switch (kind) {
            case NUMBER -> "editor.historystages.nbt.hint.number";
            case TEXT -> "editor.historystages.nbt.hint.text";
            default -> "editor.historystages.nbt.click_to_edit";
        };
        return Component.translatable(key).getString();
    }

    private int layoutAddLine(List<Piece> pieces, int y) {
        String label = Component.translatable("editor.historystages.nbt.add_line").getString();
        pieces.add(new Piece(PAD_X, y, font.width(label) + 12, 12, PieceKind.ADD,
                label, null, ADD_COLOR, new Hit.AddLine()));
        return y + 12;
    }

    /** Warnings belonging to one line of this criterion, or to the criterion itself at index -1. */
    private int layoutWarnings(List<Piece> pieces, List<NbtCriteriaValidator.Warning> warnings,
                               int lineIndex, int available, int y) {
        for (NbtCriteriaValidator.Warning warning : warnings) {
            if (warning.lineIndex() != lineIndex) continue;
            y = layoutWrapped(pieces, warningText(warning), available, y, WARN_COLOR);
        }
        return y;
    }

    private static Component warningText(NbtCriteriaValidator.Warning warning) {
        return switch (warning.kind()) {
            case UNKNOWN_ENCHANTMENT -> Component.translatable(
                    "editor.historystages.nbt.warn.unknown_enchantment", warning.subject());
            case UNKNOWN_POTION -> Component.translatable(
                    "editor.historystages.nbt.warn.unknown_potion", warning.subject());
            case LEVEL_TOO_HIGH -> Component.translatable(
                    "editor.historystages.nbt.warn.max_level",
                    warning.subject(), warning.limit(), warning.actual());
        };
    }

    private int layoutWrapped(List<Piece> pieces, Component text, int available, int y, int color) {
        for (FormattedCharSequence line : font.split(text, available)) {
            pieces.add(new Piece(PAD_X, y, available, 8, PieceKind.TEXT,
                    stringOf(line), null, color, null));
            y += LINE_H;
        }
        return y;
    }

    /** {@link Font#split} hands back glyph sequences; the pieces carry plain text. */
    private static String stringOf(FormattedCharSequence sequence) {
        StringBuilder out = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    // =============================================
    // Drawing
    // =============================================

    public void render(GuiGraphics g, Built built, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + built.width(), y + built.height(), CARD_BORDER);
        g.fill(x + 1, y + 1, x + built.width() - 1, y + built.height() - 1, CARD_BG);

        for (Piece piece : built.pieces()) {
            int px = x + piece.x();
            int py = y + piece.y();
            boolean hovered = piece.hit() != null && inside(piece, mouseX - x, mouseY - y);

            switch (piece.kind()) {
                case BADGE -> {
                    g.fill(px, py, px + piece.w(), py + piece.h(), badgeBackground(piece.color()));
                    g.drawString(font, piece.text(), px + 3, py + 1, piece.color());
                }
                case TITLE, SUBTITLE, TEXT ->
                        g.drawString(font, piece.text(), px, py, piece.color());
                case LINK -> g.drawString(font, piece.text(), px, py,
                        hovered ? 0xFFFFE066 : piece.color());
                case FIELD -> renderField(g, piece, px, py, hovered);
                case REMOVE -> g.drawString(font, piece.text(), px + 2, py + 1,
                        hovered ? REMOVE_HOVER : piece.color());
                case ADD -> {
                    g.fill(px, py, px + piece.w(), py + piece.h(),
                            hovered ? 0x40FFCC00 : 0x20FFFFFF);
                    g.drawString(font, piece.text(), px + 6, py + 2,
                            hovered ? LINK_COLOR : piece.color());
                }
            }
        }
    }

    private void renderField(GuiGraphics g, Piece piece, int px, int py, boolean hovered) {
        g.fill(px - 1, py - 1, px + piece.w() + 1, py + piece.h() + 1,
                hovered ? FIELD_BORDER_HOVER : FIELD_BORDER);
        g.fill(px, py, px + piece.w(), py + piece.h(), FIELD_BG);

        boolean empty = piece.text() == null || piece.text().isEmpty();
        String shown = fit(empty ? (piece.hint() == null ? "" : piece.hint()) : piece.text(),
                piece.w() - 8);
        g.drawString(font, shown, px + 4, py + (piece.h() - 8) / 2,
                empty ? FIELD_HINT : piece.color());
    }

    // =============================================
    // Clicks
    // =============================================

    /** What a click at the given screen position hit, or null. */
    public Hit hitTest(Built built, int x, int y, double mouseX, double mouseY) {
        for (Piece piece : built.pieces()) {
            if (piece.hit() == null) continue;
            if (inside(piece, mouseX - x, mouseY - y)) return piece.hit();
        }
        return null;
    }

    private static boolean inside(Piece piece, double localX, double localY) {
        return localX >= piece.x() && localX < piece.x() + piece.w()
                && localY >= piece.y() && localY < piece.y() + piece.h();
    }

    // =============================================
    // Per-kind text and colour
    // =============================================

    private static String badgeText(NbtCriterion criterion) {
        String key = switch (criterion.kind()) {
            case ENCHANTMENTS -> "editor.historystages.nbt.badge.enchantment";
            case COMPONENT, TEXT_LIST -> "editor.historystages.nbt.badge.component";
            case CUSTOM_DATA -> "editor.historystages.nbt.badge.custom_data";
        };
        return Component.translatable(key).getString();
    }

    private static int badgeColor(NbtCriterion criterion) {
        return switch (criterion.kind()) {
            case ENCHANTMENTS -> 0xFFC9A6FF;
            case COMPONENT, TEXT_LIST -> 0xFF8FD4FF;
            case CUSTOM_DATA -> 0xFF8FE0A4;
        };
    }

    /** The badge fill is its text colour at low alpha, so the pair can never drift apart. */
    private static int badgeBackground(int textColor) {
        return (textColor & 0x00FFFFFF) | 0x30000000;
    }

    /** One-line summary of what the criterion asks for, for lists that show it without an editor. */
    public static String previewOf(NbtCriterion criterion) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            StringBuilder out = new StringBuilder();
            for (EnchantmentListCriterion.Line line : ench.lines) {
                if (line.id.isBlank()) continue;
                if (!out.isEmpty()) out.append(", ");
                out.append(shortId(line.id)).append(' ').append(line.level.isBlank() ? "1" : line.level);
            }
            return out.toString();
        }
        if (criterion instanceof ComponentCriterion comp) {
            return comp.valueKind == ValueKind.PRESENCE ? "" : comp.displayValue();
        }
        if (criterion instanceof TextListCriterion list) {
            return String.join(" / ", list.lines);
        }
        return ((CustomDataCriterion) criterion).valueText;
    }

    private static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** The criterion's name, as the card heads it. Shared so other lists label them identically. */
    public static String titleOf(NbtCriterion criterion) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            return Component.translatable(NbtPresets.enchantmentNameKey(ench.key())).getString();
        }
        if (criterion instanceof CustomDataCriterion custom) {
            return custom.key.isEmpty() ? "…" : custom.key;
        }
        if (criterion instanceof ComponentCriterion comp) {
            return comp.presetName != null
                    ? Component.translatable(comp.presetName).getString()
                    : comp.componentId();
        }
        TextListCriterion list = (TextListCriterion) criterion;
        return list.presetName != null
                ? Component.translatable(list.presetName).getString()
                : list.componentId();
    }

    /** The technical id, shown next to a friendly preset name so nobody has to guess. */
    private static String subtitle(NbtCriterion criterion) {
        if (criterion instanceof EnchantmentListCriterion ench) return ench.key();
        if (criterion instanceof ComponentCriterion comp && comp.presetName != null) {
            return comp.componentId();
        }
        if (criterion instanceof TextListCriterion list && list.presetName != null) {
            return list.componentId();
        }
        return null;
    }

    private static Component description(NbtCriterion criterion) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            return Component.translatable(NbtPresets.enchantmentDescriptionKey(ench.key()));
        }
        if (criterion instanceof CustomDataCriterion) {
            return Component.translatable("editor.historystages.nbt.desc.custom_data");
        }
        if (criterion instanceof ComponentCriterion comp) {
            NbtPresets.Preset preset = NbtPresets.byComponentId(comp.componentId());
            return preset != null
                    ? Component.translatable(preset.descriptionKey())
                    : Component.translatable("editor.historystages.nbt.desc.component", comp.componentId());
        }
        TextListCriterion list = (TextListCriterion) criterion;
        NbtPresets.Preset preset = NbtPresets.byComponentId(list.componentId());
        return preset != null
                ? Component.translatable(preset.descriptionKey())
                : Component.translatable("editor.historystages.nbt.desc.component", list.componentId());
    }
}
