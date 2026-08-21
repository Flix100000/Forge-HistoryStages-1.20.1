package net.bananemdnsa.historystages.data.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

/**
 * The values of one settings group on one stage.
 *
 * <p>Sits between the raw, un-parsed {@code addon_settings} block on a {@link
 * net.bananemdnsa.historystages.data.StageEntry} and the typed {@link Setting} field handles an
 * addon uses to read and write them. Reads are deliberately forgiving: a stage file is
 * hand-edited, so a value that is out of range, the wrong type, or not among a choice's declared
 * options must fall back to that field's default rather than take the whole group — or the whole
 * stage — down with it.
 *
 * <p>A key present in the source JSON that no field in the group claims is preserved verbatim and
 * written back out. This is what lets an older installed version of an addon load and re-save a
 * stage without silently discarding a newer version's data — the same problem the raw {@code
 * JsonElement} block one level up exists to prevent.
 */
public final class SettingsValues {

    private final Map<String, Object> values;
    private final Map<String, Object> defaults;
    private final Map<String, JsonElement> unknown;

    private SettingsValues(
            Map<String, Object> values, Map<String, Object> defaults, Map<String, JsonElement> unknown) {
        this.values = values;
        this.defaults = defaults;
        this.unknown = unknown;
    }

    /**
     * Reads the values of {@code fields} out of {@code element}. Never throws: a null element, or
     * one that is not a JSON object, yields every field at its default. A field whose value is
     * missing, out of range, the wrong type, or (for {@link SettingKind#CHOICE}) not among the
     * declared options falls back to that field's default. Any key in the object that no field
     * claims is preserved so it survives a later {@link #write()}.
     */
    public static SettingsValues read(List<Setting<?>> fields, @Nullable JsonElement element) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> defaults = new LinkedHashMap<>();
        Map<String, JsonElement> unknown = new LinkedHashMap<>();

        JsonObject object = (element != null && element.isJsonObject()) ? element.getAsJsonObject() : null;

        for (Setting<?> field : fields) {
            JsonElement raw = object != null ? object.get(field.key()) : null;
            values.put(field.key(), readOne(field, raw));
            defaults.put(field.key(), field.defaultValue());
        }

        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!values.containsKey(entry.getKey())) {
                    unknown.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return new SettingsValues(values, defaults, unknown);
    }

    private static Object readOne(Setting<?> field, @Nullable JsonElement raw) {
        if (raw == null || raw.isJsonNull() || !raw.isJsonPrimitive()) {
            return field.defaultValue();
        }
        JsonPrimitive primitive = raw.getAsJsonPrimitive();

        return switch (field.kind()) {
            case BOOL -> primitive.isBoolean() ? primitive.getAsBoolean() : field.defaultValue();
            case INTEGER -> {
                if (!primitive.isNumber()) yield field.defaultValue();
                int value = primitive.getAsInt();
                yield clamp(value, field.min(), field.max());
            }
            case TEXT -> primitive.isString() ? primitive.getAsString() : field.defaultValue();
            case LONG_TEXT -> primitive.isString() ? primitive.getAsString() : field.defaultValue();
            case CHOICE -> {
                if (!primitive.isString()) yield field.defaultValue();
                String value = primitive.getAsString();
                yield field.optionValues().contains(value) ? value : field.defaultValue();
            }
            case ITEM -> primitive.isString() ? primitive.getAsString() : field.defaultValue();
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Never null, never throws. Returns the field's current value, or its default. */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> field) {
        Object value = values.get(field.key());
        return (T) (value != null ? value : field.defaultValue());
    }

    /**
     * Sets {@code field} to {@code value}. An integer is clamped into {@code [min, max]}; a choice
     * value not among {@link Setting#optionValues()} falls back to the field's default.
     */
    public <T> void set(Setting<T> field, T value) {
        Object stored = value;
        if (field.kind() == SettingKind.INTEGER) {
            stored = clamp((Integer) value, field.min(), field.max());
        } else if (field.kind() == SettingKind.CHOICE) {
            if (!field.optionValues().contains(value)) {
                stored = field.defaultValue();
            }
        }
        values.put(field.key(), stored);
        defaults.put(field.key(), field.defaultValue());
    }

    /**
     * Writes only the values that differ from their field's default, plus every preserved unknown
     * key. Returns {@code null} when that would be empty, so the caller can drop the whole group
     * from the stage rather than store {@code {}}.
     */
    @Nullable
    public JsonElement write() {
        JsonObject object = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : unknown.entrySet()) {
            object.add(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value.equals(defaults.get(key))) continue;

            // Values are only ever Boolean, Integer or String, all handled by JsonObject#addProperty.
            if (value instanceof Boolean b) object.addProperty(key, b);
            else if (value instanceof Integer i) object.addProperty(key, i);
            else object.addProperty(key, value.toString());
        }

        return object.entrySet().isEmpty() ? null : object;
    }

    /** Independent state: mutating the returned copy does not affect this instance. */
    public SettingsValues copy() {
        return new SettingsValues(
                new LinkedHashMap<>(values), new LinkedHashMap<>(defaults), new LinkedHashMap<>(unknown));
    }
}
