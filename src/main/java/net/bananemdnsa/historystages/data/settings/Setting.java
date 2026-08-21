package net.bananemdnsa.historystages.data.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.jetbrains.annotations.Nullable;

/**
 * A field handle for a single stage setting an addon declares.
 *
 * <p>An addon holds a {@code Setting} as a {@code static final} constant and uses it as the key
 * for every read and write of that value on a stage, so a typo is a compile error rather than a
 * value that silently stays at its default. Fields are therefore standalone objects, built and
 * validated before any group registration runs, rather than something a group builder produces.
 *
 * <p>{@code T} is the value type stored under this key: {@code Boolean} for {@link
 * SettingKind#BOOL}, {@code Integer} for {@link SettingKind#INTEGER}, {@code String} for text,
 * long text and choice, and {@code String} again for item, holding the item id. Item settings
 * deliberately stay a plain id string rather than a Minecraft type — this data layer does not
 * depend on Minecraft, and {@link SettingKind#ITEM} already tells the editor to draw an item
 * picker, so a richer type would buy no extra safety.
 *
 * @param <T> the value type this setting holds
 */
public final class Setting<T> {

    private final String key;
    private final SettingKind kind;
    private final T defaultValue;
    private final String langKey;
    private final int min;
    private final int max;
    private final List<String> optionValues;
    private final Map<String, String> optionLangKeys;
    private final Set<StageScope> supportedScopes;
    private final String hintLangKey;
    private final List<String> placeholders;

    private Setting(Builder<T> builder) {
        this.key = builder.key;
        this.kind = builder.kind;
        this.defaultValue = builder.defaultValue;
        this.langKey = builder.langKey;
        this.min = builder.min == null ? 0 : builder.min;
        this.max = builder.max == null ? 0 : builder.max;
        this.optionValues = List.copyOf(builder.options.keySet());
        this.optionLangKeys = Map.copyOf(builder.options);
        this.supportedScopes = Set.copyOf(builder.supportedScopes);
        this.hintLangKey = builder.hintLangKey;
        this.placeholders = List.copyOf(builder.placeholders);
    }

    public static Builder<Boolean> bool(String key) {
        return new Builder<>(key, SettingKind.BOOL);
    }

    public static Builder<Integer> integer(String key) {
        return new Builder<>(key, SettingKind.INTEGER);
    }

    public static Builder<String> text(String key) {
        return new Builder<>(key, SettingKind.TEXT);
    }

    public static Builder<String> choice(String key) {
        return new Builder<>(key, SettingKind.CHOICE);
    }

    public static Builder<String> item(String key) {
        return new Builder<>(key, SettingKind.ITEM);
    }

    public static Builder<String> longText(String key) {
        return new Builder<>(key, SettingKind.LONG_TEXT);
    }

    public String key() {
        return key;
    }

    public SettingKind kind() {
        return kind;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String langKey() {
        return langKey;
    }

    /** Meaningful only for {@link SettingKind#INTEGER}; {@code 0} otherwise. */
    public int min() {
        return min;
    }

    /** Meaningful only for {@link SettingKind#INTEGER}; {@code 0} otherwise. */
    public int max() {
        return max;
    }

    /** The declared option values, in declaration order. Empty unless {@link SettingKind#CHOICE}. */
    public List<String> optionValues() {
        return optionValues;
    }

    /** The lang key for a declared option value, or {@code null} if it was never declared. */
    public String optionLangKey(String value) {
        return optionLangKeys.get(value);
    }

    /** Which stage scopes this field applies to. Both unless declared otherwise. */
    public Set<StageScope> supportedScopes() {
        return supportedScopes;
    }

    /** The greyed-out hint shown inside the text area. Meaningful only for {@link SettingKind#LONG_TEXT}. */
    @Nullable
    public String hintLangKey() {
        return hintLangKey;
    }

    /**
     * Literal placeholder tokens offered as buttons, in declaration order. Meaningful only for
     * {@link SettingKind#LONG_TEXT}; empty otherwise.
     */
    public List<String> placeholders() {
        return placeholders;
    }

    public static final class Builder<T> {
        private final String key;
        private final SettingKind kind;
        private final Map<String, String> options = new LinkedHashMap<>();
        private final List<String> placeholders = new ArrayList<>();
        private Set<StageScope> supportedScopes = EnumSet.allOf(StageScope.class);
        private T defaultValue;
        private String langKey;
        private Integer min;
        private Integer max;
        private String hintLangKey;

        private Builder(String key, SettingKind kind) {
            this.key = key;
            this.kind = kind;
        }

        public Builder<T> defaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder<T> langKey(String langKey) {
            this.langKey = langKey;
            return this;
        }

        /** Inclusive bounds. Only meaningful for {@link SettingKind#INTEGER}. */
        public Builder<T> range(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        /** Only meaningful for {@link SettingKind#CHOICE}. Preserves declaration order. */
        public Builder<T> option(String value, String optionLangKey) {
            if (options.containsKey(value)) {
                throw new IllegalArgumentException(
                        "Setting '" + key + "' already declares an option '" + value + "'.");
            }
            options.put(value, optionLangKey);
            return this;
        }

        /** The greyed-out hint shown inside the text area. Only meaningful for {@link SettingKind#LONG_TEXT}. */
        public Builder<T> hintLangKey(String hintLangKey) {
            this.hintLangKey = hintLangKey;
            return this;
        }

        /**
         * Declares a literal placeholder token (e.g. {@code "{player}"}), offered to the player as
         * a button in the text dialog. Repeatable; preserves declaration order. Only meaningful for
         * {@link SettingKind#LONG_TEXT}.
         */
        public Builder<T> placeholder(String token) {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "Setting '" + key + "' declares a blank placeholder.");
            }
            if (placeholders.contains(token)) {
                throw new IllegalArgumentException(
                        "Setting '" + key + "' already declares a placeholder '" + token + "'.");
            }
            placeholders.add(token);
            return this;
        }

        /**
         * Which stage scopes this field applies to. Both unless said otherwise — pass only
         * {@code StageScope.GLOBAL} for a field that cannot sensibly vary per player.
         */
        public Builder<T> supportedScopes(StageScope... scopes) {
            this.supportedScopes = EnumSet.noneOf(StageScope.class);
            Collections.addAll(this.supportedScopes, scopes);
            if (this.supportedScopes.isEmpty()) {
                throw new IllegalArgumentException("A setting that supports no scope can never be shown.");
            }
            return this;
        }

        public Setting<T> build() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("A setting key must not be blank.");
            }
            if (langKey == null) {
                throw new IllegalStateException("Setting '" + key + "' has no langKey.");
            }
            if (defaultValue == null) {
                throw new IllegalStateException("Setting '" + key + "' has no defaultValue.");
            }
            if (kind == SettingKind.INTEGER) {
                if (min == null || max == null) {
                    throw new IllegalStateException(
                            "Setting '" + key + "' is an integer setting with no range.");
                }
                if (min > max) {
                    throw new IllegalArgumentException("Setting '" + key + "' has an inverted range: min "
                            + min + " is greater than max " + max + ".");
                }
                int value = (Integer) defaultValue;
                if (value < min || value > max) {
                    throw new IllegalArgumentException("Setting '" + key + "' has a defaultValue of "
                            + value + ", outside its range [" + min + ", " + max + "].");
                }
            }
            if (kind == SettingKind.CHOICE) {
                if (options.isEmpty()) {
                    throw new IllegalStateException(
                            "Setting '" + key + "' is a choice setting with no options.");
                }
                if (!options.containsKey(defaultValue)) {
                    throw new IllegalArgumentException("Setting '" + key + "' has a defaultValue of '"
                            + defaultValue + "', which is not among its declared options " + options.keySet() + ".");
                }
            }
            return new Setting<>(this);
        }
    }
}
