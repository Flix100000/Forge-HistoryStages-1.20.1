package net.bananemdnsa.historystages.client.editor.widget.dialog;

import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Declarative specification for one field in an {@link AbstractInputScreen}.
 *
 * <p>{@link #number} and {@link #decimal} derive their typing filter, max length and range
 * validator from the range itself, so callers never hand-roll a digit filter. A leading
 * minus is accepted exactly when {@code min < 0}.
 */
public final class InputField {

    /** Which characters the field accepts and how its value is parsed. */
    public enum Kind { TEXT, NUMBER, DECIMAL }

    private final String key;
    private final Kind kind;

    private Component label;
    private Component hint;
    private int maxLength = 256;
    private String regex;
    private Function<String, Component> validator;
    private int width = -1;               // -1 = full content width
    private String initial = "";

    // Range, only meaningful for NUMBER / DECIMAL
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private boolean rangeSet = false;

    private InputField(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    public static InputField text(String key) {
        return new InputField(key, Kind.TEXT);
    }

    public static InputField number(String key) {
        InputField f = new InputField(key, Kind.NUMBER);
        f.maxLength = 11; // int range plus sign
        return f;
    }

    public static InputField decimal(String key) {
        InputField f = new InputField(key, Kind.DECIMAL);
        f.maxLength = 12;
        return f;
    }

    public InputField label(Component v) { this.label = v; return this; }
    public InputField hint(Component v) { this.hint = v; return this; }
    public InputField maxLength(int v) { this.maxLength = v; return this; }
    public InputField regex(String v) { this.regex = v; return this; }
    public InputField validator(Function<String, Component> v) { this.validator = v; return this; }
    public InputField width(int v) { this.width = v; return this; }
    public InputField initial(String v) { this.initial = v == null ? "" : v; return this; }

    public InputField range(int min, int max) {
        this.min = min;
        this.max = max;
        this.rangeSet = true;
        // Only NUMBER can size its box from the bounds' digit count. A DECIMAL given integer
        // literals (range(1, 32) on a 1.0-32.0 field) would otherwise cap at 2 characters and
        // silently refuse "12.5", so leave its default length alone.
        if (kind == Kind.NUMBER) {
            this.maxLength = Math.max(String.valueOf(max).length(), String.valueOf(min).length());
        }
        return this;
    }

    public InputField range(double min, double max) {
        this.min = min;
        this.max = max;
        this.rangeSet = true;
        return this;
    }

    public InputField initial(int v) { this.initial = String.valueOf(v); return this; }
    public InputField initial(double v) { this.initial = String.valueOf(v); return this; }

    // ============ Accessors ============

    public String key() { return key; }
    public Kind kind() { return kind; }
    public Component label() { return label; }
    public Component hint() { return hint; }
    /**
     * Never shorter than the initial value. {@code EditBox.setValue} truncates rather than
     * rejects, so a cap below the incoming value would silently rewrite it — an out-of-range
     * count from a hand-written pack would be clipped to a passing value and saved back with
     * no warning. Letting it in whole means {@link #validate} reports it instead.
     */
    public int maxLength() { return Math.max(maxLength, initial.length()); }
    public int width() { return width; }
    public String initial() { return initial; }

    private boolean allowsNegative() {
        return rangeSet && min < 0;
    }

    /**
     * The typing filter handed to {@code EditBox.setFilter}. Always permits the empty string
     * so the field can be cleared; emptiness is a validation concern, not a typing one.
     */
    public boolean acceptsTyping(String s) {
        if (s.isEmpty()) return true;
        switch (kind) {
            case NUMBER -> {
                if (allowsNegative() && s.equals("-")) return true;
                String body = allowsNegative() && s.startsWith("-") ? s.substring(1) : s;
                return body.matches("\\d*");
            }
            case DECIMAL -> {
                if (allowsNegative() && s.equals("-")) return true;
                String body = allowsNegative() && s.startsWith("-") ? s.substring(1) : s;
                return body.matches("\\d*\\.?\\d*");
            }
            default -> {
                return regex == null || s.matches(regex);
            }
        }
    }

    /**
     * Validates the current value. Returns null when acceptable, otherwise the message to
     * show. Range checks run first; a custom {@link #validator} runs last so it can assume
     * a well-formed value.
     */
    public Component validate(String raw) {
        String v = raw == null ? "" : raw.trim();

        if (kind == Kind.NUMBER || kind == Kind.DECIMAL) {
            if (v.isEmpty() || v.equals("-")) {
                return Component.translatable("editor.historystages.input.empty");
            }
            double parsed;
            try {
                parsed = kind == Kind.NUMBER ? Integer.parseInt(v) : Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return Component.translatable("editor.historystages.input.not_a_number");
            }
            if (rangeSet && (parsed < min || parsed > max)) {
                return Component.translatable("editor.historystages.input.out_of_range",
                        formatBound(min), formatBound(max));
            }
        }

        return validator == null ? null : validator.apply(v);
    }

    private String formatBound(double b) {
        return kind == Kind.NUMBER ? String.valueOf((long) b) : String.valueOf(b);
    }
}
