package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Reads and writes the {@code professions} list, in either of its two shapes.
 *
 * <p>A profession that gates every level is still written as a bare string, exactly as before this
 * entry type existed. Only one that names levels becomes an object, so a stage file that never
 * needed the narrowing does not change at all when it is saved by a newer version — and an older
 * version reading a narrowed entry still finds a profession id where it expects one.
 *
 * <pre>
 * "professions": [
 *   "minecraft:cleric",
 *   { "id": "minecraft:librarian", "levels": [4, 5] }
 * ]
 * </pre>
 *
 * <p>Levels are written as numbers, the way {@link net.bananemdnsa.historystages.data.lock.TradeLevelListAdapter}
 * writes the block's own level list — the two say the same kind of thing and must not look
 * different in the same file. Both spellings are accepted on the way in.
 *
 * <p>A malformed entry is skipped rather than fatal. Stage files are hand-edited, and the loader
 * names the stage when it complains, which beats an exception thrown from inside Gson.
 */
public class TradeProfessionEntryListAdapter extends TypeAdapter<List<TradeProfessionEntry>> {

    @Override
    public void write(JsonWriter out, List<TradeProfessionEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (TradeProfessionEntry entry : entries) {
            if (entry == null || entry.getId() == null || entry.getId().isEmpty()) continue;
            if (!entry.hasLevels()) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            out.name("levels");
            out.beginArray();
            for (String level : entry.getLevels()) {
                if (level == null) continue;
                try {
                    out.value(Long.parseLong(level.trim()));
                } catch (NumberFormatException notANumber) {
                    // Written back as it was: throwing away what the maintainer typed is worse
                    // than letting the loader complain about it by name.
                    out.value(level);
                }
            }
            out.endArray();
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<TradeProfessionEntry> read(JsonReader in) throws IOException {
        List<TradeProfessionEntry> entries = new ArrayList<>();
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return entries;
        }
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new TradeProfessionEntry(in.nextString()));
                continue;
            }
            if (in.peek() != JsonToken.BEGIN_OBJECT) {
                in.skipValue();
                continue;
            }
            JsonObject object = JsonParser.parseReader(in).getAsJsonObject();
            if (!object.has("id")) continue;
            String id = object.get("id").getAsString();
            if (id.isEmpty()) continue;
            entries.add(new TradeProfessionEntry(id, readLevels(object.get("levels"))));
        }
        in.endArray();
        return entries;
    }

    /** Null for an absent or unusable list, which is what "every level" is stored as. */
    private static List<String> readLevels(JsonElement element) {
        if (element == null || !element.isJsonArray()) return null;
        List<String> levels = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive()) levels.add(value.getAsString());
        }
        return levels.isEmpty() ? null : levels;
    }
}
