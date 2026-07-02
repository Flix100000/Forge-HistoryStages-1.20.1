package net.bananemdnsa.historystages.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson adapter for List&lt;EntitySpawnLockEntry&gt; that supports both the compact string format
 * ("minecraft:zombie") and the full object format with per-entry source overrides.
 *
 * <p>Write format: {@code { "id": "…", "unlock_sources": ["spawner"] }} — the list contains
 * the sources that are <em>not</em> locked. Absent field / plain string = all sources locked.
 *
 * <p>Read: accepts the current {@code unlock_sources} key and the legacy {@code lock_sources}
 * key (e.g. for hand-written configs). Plain string entries are kept as "all sources locked".
 */
public class EntitySpawnLockEntryListAdapter extends TypeAdapter<List<EntitySpawnLockEntry>> {

    @Override
    public void write(JsonWriter out, List<EntitySpawnLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (EntitySpawnLockEntry entry : entries) {
            // Compute unlocked sources (complement of locked), if any source filter is set.
            List<String> unlockedSources = new ArrayList<>();
            if (entry.hasLockSources()) {
                List<String> locked = entry.getLockSources();
                for (String src : EntitySpawnLockEntry.ALL_SOURCES) {
                    if (!locked.contains(src)) unlockedSources.add(src);
                }
            }
            boolean hasSourceFilter = !unlockedSources.isEmpty();
            boolean hasDimFilter = entry.hasUnlockDimensions();

            if (!hasSourceFilter && !hasDimFilter) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            if (hasSourceFilter) {
                out.name("unlock_sources");
                out.beginArray();
                for (String src : unlockedSources) out.value(src);
                out.endArray();
            }
            if (hasDimFilter) {
                out.name("unlock_dimensions");
                out.beginArray();
                for (String dim : entry.getUnlockDimensions()) out.value(dim);
                out.endArray();
            }
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<EntitySpawnLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<EntitySpawnLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new EntitySpawnLockEntry(in.nextString()));
            } else {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                List<String> lockSources = null;
                if (obj.has("unlock_sources") && obj.get("unlock_sources").isJsonArray()) {
                    List<String> unlocked = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("unlock_sources")) {
                        unlocked.add(el.getAsString());
                    }
                    lockSources = new ArrayList<>();
                    for (String src : EntitySpawnLockEntry.ALL_SOURCES) {
                        if (!unlocked.contains(src)) lockSources.add(src);
                    }
                } else if (obj.has("lock_sources") && obj.get("lock_sources").isJsonArray()) {
                    lockSources = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("lock_sources")) {
                        lockSources.add(el.getAsString());
                    }
                }
                List<String> unlockDimensions = null;
                if (obj.has("unlock_dimensions") && obj.get("unlock_dimensions").isJsonArray()) {
                    unlockDimensions = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("unlock_dimensions")) {
                        unlockDimensions.add(el.getAsString());
                    }
                }
                entries.add(new EntitySpawnLockEntry(id, lockSources, unlockDimensions));
            }
        }
        in.endArray();
        return entries;
    }
}
