package net.bananemdnsa.historystages.data.lock.category;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * How one category turns its entries into the JSON stored on a stage, and back.
 *
 * <p>An interface rather than a fixed serializer: {@link #gson} covers the ordinary case in one
 * line, and a category with an awkward shape can hand-roll instead. Reads must be forgiving —
 * stage files are hand-edited, and a malformed entry must not take stage loading down with it.
 *
 * @param <T> the category's entry type
 */
public interface CategoryStorage<T> {

    JsonElement write(List<T> entries);

    /** Never throws on malformed input; returns what it could read, or an empty list. */
    List<T> read(JsonElement element);

    /** Reads and writes a JSON array of {@code type} through a plain Gson binding. */
    static <T> CategoryStorage<T> gson(Class<T> type) {
        Gson gson = new Gson();
        return new CategoryStorage<>() {
            @Override
            public JsonElement write(List<T> entries) {
                JsonArray array = new JsonArray();
                for (T entry : entries) array.add(gson.toJsonTree(entry, type));
                return array;
            }

            @Override
            public List<T> read(JsonElement element) {
                if (element == null || !element.isJsonArray()) return List.of();
                List<T> entries = new ArrayList<>();
                for (JsonElement child : element.getAsJsonArray()) {
                    try {
                        T entry = gson.fromJson(child, type);
                        if (entry != null) entries.add(entry);
                    } catch (JsonParseException ignored) {
                        // One bad entry should cost that entry, not the whole stage.
                    }
                }
                return entries;
            }
        };
    }
}
