package net.bananemdnsa.historystages.data;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.internal.Streams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ItemEntryListAdapter extends TypeAdapter<List<ItemEntry>> {

    @Override
    public void write(JsonWriter out, List<ItemEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (ItemEntry entry : entries) {
            if (!entry.hasNbt() && !entry.hasLockActions()) {
                out.value(entry.getId());
            } else {
                out.beginObject();
                out.name("id").value(entry.getId());
                if (entry.hasNbt()) {
                    out.name("nbt");
                    Streams.write(entry.getNbt(), out);
                }
                if (entry.hasLockActions()) {
                    // Compute unlock_actions = all known actions minus the locked ones
                    List<String> locked = entry.getLockActions();
                    List<String> unlocked = new ArrayList<>();
                    for (String action : NamedLockEntry.ALL_ACTIONS) {
                        if (!locked.contains(action)) unlocked.add(action);
                    }
                    if (!unlocked.isEmpty()) {
                        out.name("unlock_actions");
                        out.beginArray();
                        for (String action : unlocked) {
                            out.value(action);
                        }
                        out.endArray();
                    }
                }
                out.endObject();
            }
        }
        out.endArray();
    }

    @Override
    public List<ItemEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<ItemEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new ItemEntry(in.nextString()));
            } else {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                JsonObject nbt = obj.has("nbt") ? obj.getAsJsonObject("nbt") : null;
                List<String> lockActions = null;
                if (obj.has("unlock_actions") && obj.get("unlock_actions").isJsonArray()) {
                    // Current format: unlock_actions lists the NOT-locked actions → invert to get locked
                    List<String> unlocked = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("unlock_actions")) {
                        unlocked.add(el.getAsString());
                    }
                    lockActions = new ArrayList<>();
                    for (String action : NamedLockEntry.ALL_ACTIONS) {
                        if (!unlocked.contains(action)) lockActions.add(action);
                    }
                } else if (obj.has("lock_actions") && obj.get("lock_actions").isJsonArray()) {
                    // Legacy format: lock_actions lists the locked actions directly
                    lockActions = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("lock_actions")) {
                        lockActions.add(el.getAsString());
                    }
                }
                entries.add(new ItemEntry(id, nbt, lockActions));
            }
        }
        in.endArray();
        return entries;
    }
}
