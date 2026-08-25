package net.bananemdnsa.historystages.api.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * One field an addon puts on the HistoryStages config screen.
 *
 * <p>The field itself holds no value. It carries a {@link Supplier} that reads the addon's own
 * state and a {@link Consumer} that writes back into it — the same shape {@code
 * CommonConfigSync.Entry} already uses for HistoryStages' own synced values. Every value on the
 * boundary between this field and the screen is a {@code String}; the screen is string-based end
 * to end, and the addon already holds the typed value on its own side, so this class only passes
 * it through.
 */
public final class AddonConfigField {

    /** The twelve field shapes an addon may declare. See spec §6 for why the other three are not here. */
    public enum AddonConfigKind {
        BOOL,
        INTEGER,
        DECIMAL,
        TEXT,
        RICH_TEXT,
        COLOR,
        ITEM,
        ITEM_LIST,
        TAG_LIST,
        TEXTURE,
        CHOICE,

        /**
         * Like {@link #TEXT} in storage, but edited in a screen the addon supplies through the
         * client-side {@code CustomFieldScreens} registry. The escape hatch from the fixed kinds.
         */
        CUSTOM_SCREEN
    }

    private final String key;
    private final AddonConfigKind kind;
    private final String labelLangKey;
    private final String descLangKey;
    private final String defaultValue;
    private final Supplier<String> read;
    private final Consumer<String> write;
    private final double min;
    private final double max;
    private final List<String> optionValues;
    private final Map<String, String> optionLangKeys;
    private final List<String> placeholders;

    private AddonConfigField(Builder builder) {
        this.key = builder.key;
        this.kind = builder.kind;
        this.labelLangKey = builder.labelLangKey;
        this.descLangKey = builder.descLangKey;
        this.defaultValue = builder.defaultValue;
        this.read = builder.read;
        this.write = builder.write;
        this.min = builder.min == null ? 0 : builder.min;
        this.max = builder.max == null ? 0 : builder.max;
        this.optionValues = List.copyOf(builder.options.keySet());
        this.optionLangKeys = Map.copyOf(builder.options);
        this.placeholders = List.copyOf(builder.placeholders);
    }

    public static Builder bool(String key) {
        return new Builder(key, AddonConfigKind.BOOL);
    }

    public static Builder integer(String key) {
        return new Builder(key, AddonConfigKind.INTEGER);
    }

    public static Builder decimal(String key) {
        return new Builder(key, AddonConfigKind.DECIMAL);
    }

    public static Builder text(String key) {
        return new Builder(key, AddonConfigKind.TEXT);
    }

    public static Builder richText(String key) {
        return new Builder(key, AddonConfigKind.RICH_TEXT);
    }

    public static Builder color(String key) {
        return new Builder(key, AddonConfigKind.COLOR);
    }

    public static Builder item(String key) {
        return new Builder(key, AddonConfigKind.ITEM);
    }

    public static Builder itemList(String key) {
        return new Builder(key, AddonConfigKind.ITEM_LIST);
    }

    public static Builder tagList(String key) {
        return new Builder(key, AddonConfigKind.TAG_LIST);
    }

    public static Builder texture(String key) {
        return new Builder(key, AddonConfigKind.TEXTURE);
    }

    public static Builder choice(String key) {
        return new Builder(key, AddonConfigKind.CHOICE);
    }

    /**
     * A value only the addon knows how to edit: stored as a plain string, edited in a screen the
     * addon supplies through {@link
     * net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent}. Because the value
     * stays a string, nothing about saving, syncing or permissions differs from a text field.
     */
    public static Builder customScreen(String key) {
        return new Builder(key, AddonConfigKind.CUSTOM_SCREEN);
    }

    public String key() {
        return key;
    }

    public AddonConfigKind kind() {
        return kind;
    }

    public String labelLangKey() {
        return labelLangKey;
    }

    @Nullable
    public String descLangKey() {
        return descLangKey;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public Supplier<String> read() {
        return read;
    }

    public Consumer<String> write() {
        return write;
    }

    /** Meaningful only for {@link AddonConfigKind#INTEGER} and {@link AddonConfigKind#DECIMAL}; {@code 0} otherwise. */
    public double min() {
        return min;
    }

    /** Meaningful only for {@link AddonConfigKind#INTEGER} and {@link AddonConfigKind#DECIMAL}; {@code 0} otherwise. */
    public double max() {
        return max;
    }

    /** The declared option values, in declaration order. Empty unless {@link AddonConfigKind#CHOICE}. */
    public List<String> optionValues() {
        return optionValues;
    }

    /** The lang key for a declared option value, or {@code null} if it was never declared. */
    @Nullable
    public String optionLangKey(String value) {
        return optionLangKeys.get(value);
    }

    /**
     * Literal placeholder tokens offered as buttons in the rich text dialog, in declaration
     * order. Meaningful only for {@link AddonConfigKind#RICH_TEXT}; empty otherwise.
     */
    public List<String> placeholders() {
        return placeholders;
    }

    public static final class Builder {
        private final String key;
        private final AddonConfigKind kind;
        private final Map<String, String> options = new LinkedHashMap<>();
        private final List<String> placeholders = new ArrayList<>();
        private String labelLangKey;
        private String descLangKey;
        private String defaultValue;
        private Supplier<String> read;
        private Consumer<String> write;
        private Double min;
        private Double max;

        private Builder(String key, AddonConfigKind kind) {
            this.key = key;
            this.kind = kind;
        }

        public Builder labelLangKey(String labelLangKey) {
            this.labelLangKey = labelLangKey;
            return this;
        }

        public Builder descLangKey(String descLangKey) {
            this.descLangKey = descLangKey;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder read(Supplier<String> read) {
            this.read = read;
            return this;
        }

        public Builder write(Consumer<String> write) {
            this.write = write;
            return this;
        }

        /** Inclusive bounds. Only meaningful for {@link AddonConfigKind#INTEGER} and {@link AddonConfigKind#DECIMAL}. */
        public Builder range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        /** Only meaningful for {@link AddonConfigKind#CHOICE}. Preserves declaration order. */
        public Builder option(String value, String optionLangKey) {
            if (options.containsKey(value)) {
                throw new IllegalArgumentException(
                        "Config field '" + key + "' already declares an option '" + value + "'.");
            }
            options.put(value, optionLangKey);
            return this;
        }

        /**
         * Declares a literal placeholder token (e.g. {@code "{player}"}), offered to the player as
         * a button in the rich text dialog. Repeatable; preserves declaration order. Only
         * meaningful for {@link AddonConfigKind#RICH_TEXT}.
         */
        public Builder placeholder(String token) {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "Config field '" + key + "' declares a blank placeholder.");
            }
            if (placeholders.contains(token)) {
                throw new IllegalArgumentException(
                        "Config field '" + key + "' already declares a placeholder '" + token + "'.");
            }
            placeholders.add(token);
            return this;
        }

        public AddonConfigField build() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("A config field key must not be blank.");
            }
            if (labelLangKey == null) {
                throw new IllegalStateException("Config field '" + key + "' has no labelLangKey.");
            }
            if (defaultValue == null) {
                throw new IllegalStateException("Config field '" + key + "' has no defaultValue.");
            }
            if (read == null) {
                throw new IllegalStateException("Config field '" + key + "' has no read callback.");
            }
            if (write == null) {
                throw new IllegalStateException("Config field '" + key + "' has no write callback.");
            }
            if (kind == AddonConfigKind.INTEGER || kind == AddonConfigKind.DECIMAL) {
                double value;
                try {
                    value = Double.parseDouble(defaultValue);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Config field '" + key + "' has a defaultValue of '"
                            + defaultValue + "', which is not a number.");
                }
                if (min != null && max != null && (value < min || value > max)) {
                    throw new IllegalArgumentException("Config field '" + key + "' has a defaultValue of "
                            + defaultValue + ", outside its range [" + min + ", " + max + "].");
                }
            }
            if (kind == AddonConfigKind.CHOICE) {
                if (options.isEmpty()) {
                    throw new IllegalStateException(
                            "Config field '" + key + "' is a choice field with no options.");
                }
                if (!options.containsKey(defaultValue)) {
                    throw new IllegalArgumentException("Config field '" + key + "' has a defaultValue of '"
                            + defaultValue + "', which is not among its declared options " + options.keySet() + ".");
                }
            }
            return new AddonConfigField(this);
        }
    }
}
