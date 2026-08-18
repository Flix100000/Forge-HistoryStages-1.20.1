package net.bananemdnsa.historystages.data.graph;

import java.util.List;

/**
 * Reads and writes one field of a {@link StageStyle} by its leaf name, as text.
 *
 * <p>The leaf names are the last segment of a {@code graph.toml} style path
 * ({@code style.global.locked.fillOpacity} -> {@code fillOpacity}), which is what
 * {@link GraphKey#leaf()} hands out. Everything that edits an override addresses fields that
 * way: the rows, the validator and the clipboard. Without this class each of them would carry
 * its own ten-arm switch, and the day a field is added to {@link StageStyle} only some of them
 * would learn about it.
 *
 * <p>Text rather than Object because {@code ConfigEntry.value} is a String and so is the wire
 * form; converting once here beats converting at every call site.
 */
public final class StageStyleFields {

    /** Every field of {@link StageStyle}, in the order {@code graph.toml} declares them. */
    public static final List<String> LEAVES = List.of(
            "shape", "size", "cornerRadius", "border", "borderWidth",
            "fill", "fillOpacity", "label", "labelColor", "checkmark");

    private StageStyleFields() {}

    /** The field's value as text, or null when the field is unset (i.e. inherited). */
    public static String get(StageStyle style, String leaf) {
        if (style == null || leaf == null) return null;
        Object value = switch (leaf) {
            case "shape" -> style.shape;
            case "size" -> style.size;
            case "cornerRadius" -> style.cornerRadius;
            case "border" -> style.border;
            case "borderWidth" -> style.borderWidth;
            case "fill" -> style.fill;
            case "fillOpacity" -> style.fillOpacity;
            case "label" -> style.label;
            case "labelColor" -> style.labelColor;
            case "checkmark" -> style.checkmark;
            default -> null;
        };
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Sets the field from text. A null or blank {@code value} clears it, which is how a row goes
     * back to inheriting. A number that will not parse also clears it rather than throwing: the
     * text can come from a field the user is still typing in.
     */
    public static void set(StageStyle style, String leaf, String value) {
        if (style == null || leaf == null) return;
        String text = (value == null || value.isBlank()) ? null : value.trim();
        switch (leaf) {
            case "shape" -> style.shape = text;
            case "size" -> style.size = parseDouble(text);
            case "cornerRadius" -> style.cornerRadius = parseInt(text);
            case "border" -> style.border = text;
            case "borderWidth" -> style.borderWidth = parseInt(text);
            case "fill" -> style.fill = text;
            case "fillOpacity" -> style.fillOpacity = parseDouble(text);
            case "label" -> style.label = text;
            case "labelColor" -> style.labelColor = text;
            case "checkmark" -> style.checkmark = text == null ? null : Boolean.valueOf(text);
            default -> {
                // An unknown leaf is a newer client talking to older data, or a typo in a
                // hand-edited file. Neither is worth an exception.
            }
        }
    }

    private static Double parseDouble(String text) {
        if (text == null) return null;
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String text) {
        if (text == null) return null;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
