package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the stage file's NBT criteria object and the flat list of criteria the editor
 * edits.
 *
 * <p>The split it draws mirrors {@code NbtMatcher.buildMatchTag} exactly, because that is the only
 * thing that decides whether a criterion can ever match: {@code Enchantments} and
 * {@code StoredEnchantments} are synthesised, {@code components} is encoded from the stack's
 * components, and every other top-level key is looked up in {@code custom_data}.
 */
public final class NbtCriteriaCodec {

    private static final String ENCHANTMENTS = "Enchantments";
    private static final String STORED_ENCHANTMENTS = "StoredEnchantments";
    private static final String COMPONENTS = "components";

    private NbtCriteriaCodec() {}

    public static List<NbtCriterion> load(JsonObject nbt) {
        List<NbtCriterion> out = new ArrayList<>();
        if (nbt == null) return out;

        for (var entry : nbt.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if ((ENCHANTMENTS.equals(key) || STORED_ENCHANTMENTS.equals(key)) && value.isJsonArray()) {
                out.add(loadEnchantments(key, value.getAsJsonArray()));
            } else if (COMPONENTS.equals(key) && value.isJsonObject()) {
                loadComponents(value.getAsJsonObject(), out);
            } else {
                out.add(new CustomDataCriterion(key, asText(value), NbtPresets.isLegacyKey(key)));
            }
        }
        return out;
    }

    public static JsonObject write(List<NbtCriterion> criteria) {
        JsonObject out = new JsonObject();
        JsonObject components = new JsonObject();

        for (NbtCriterion criterion : criteria) {
            if (criterion.isEmpty()) continue;

            if (criterion instanceof EnchantmentListCriterion ench) {
                JsonArray array = new JsonArray();
                for (EnchantmentListCriterion.Line line : ench.lines) {
                    if (line.id.isBlank()) continue;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", line.id);
                    if (line.level.matches("\\d+-\\d+")) {
                        obj.addProperty("lvl", line.level);
                    } else {
                        try {
                            obj.addProperty("lvl", Integer.parseInt(line.level.trim()));
                        } catch (NumberFormatException e) {
                            obj.addProperty("lvl", 1);
                        }
                    }
                    array.add(obj);
                }
                if (!array.isEmpty()) out.add(ench.key(), array);

            } else if (criterion instanceof TextListCriterion lore) {
                JsonArray array = new JsonArray();
                for (String line : lore.lines) {
                    if (!line.isBlank()) array.add(line);
                }
                if (!array.isEmpty()) components.add(lore.componentId(), array);

            } else if (criterion instanceof ComponentCriterion comp) {
                JsonElement parsed = parseOrNull(comp.valueJson);
                if (parsed != null) components.add(comp.componentId(), parsed);

            } else if (criterion instanceof CustomDataCriterion custom) {
                out.add(custom.key, typedValue(custom.valueText));
            }
        }

        if (!components.isEmpty()) out.add(COMPONENTS, components);
        return out;
    }

    private static EnchantmentListCriterion loadEnchantments(String key, JsonArray array) {
        EnchantmentListCriterion criterion = new EnchantmentListCriterion(key);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            String lvl = obj.has("lvl") ? obj.get("lvl").getAsString() : "";
            criterion.lines.add(new EnchantmentListCriterion.Line(id, lvl));
        }
        return criterion;
    }

    private static void loadComponents(JsonObject components, List<NbtCriterion> out) {
        for (var entry : components.entrySet()) {
            String id = entry.getKey();
            JsonElement value = entry.getValue();
            NbtPresets.Preset preset = NbtPresets.byComponentId(id);
            String presetName = preset == null ? null : preset.nameKey();

            if (NbtPresets.LORE_COMPONENT.equals(id) && value.isJsonArray()) {
                TextListCriterion lore = new TextListCriterion(id, presetName);
                for (JsonElement line : value.getAsJsonArray()) {
                    lore.lines.add(asText(line));
                }
                out.add(lore);
            } else {
                out.add(new ComponentCriterion(id, value.toString(), presetName));
            }
        }
    }

    /** Primitives come back bare so the editor shows {@code main_01}, not {@code "main_01"}. */
    private static String asText(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    /**
     * Keeps the JSON type of a hand-typed custom value. Writing {@code 5} back as the string "5"
     * would make it unmatchable: {@code NbtMatcher} only compares a string criterion against a
     * {@code StringTag}, or against a number when it reads as a "1-4" range.
     */
    private static JsonElement typedValue(String text) {
        String trimmed = text.trim();
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonPrimitive() || parsed.isJsonObject() || parsed.isJsonArray()) {
                return parsed;
            }
        } catch (Exception ignored) {
            // not JSON — a plain string is exactly what was meant
        }
        return new JsonPrimitive(trimmed);
    }

    /** Component values may be any JSON type; mod components are not always objects. */
    private static JsonElement parseOrNull(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            return parsed.isJsonNull() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }
}
