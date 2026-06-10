package net.bananemdnsa.historystages.data.lock;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson adapter for List&lt;NamedLockEntry&gt; that supports both the compact string format
 * ("minecraft:swords") and the full object format with per-entry action overrides.
 *
 * <p>Write format: {@code { "id": "…", "unlock_actions": ["pickup"] }} — the list contains
 * the actions that are <em>not</em> locked. Absent field / plain string = all actions locked.
 *
 * <p>Read: accepts the current {@code unlock_actions} key and the legacy {@code lock_actions}
 * key (backwards-compatible). Legacy entries are converted on load.
 */
public class NamedLockEntryListAdapter extends TypeAdapter<List<NamedLockEntry>> {

    @Override
    public void write(JsonWriter out, List<NamedLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (NamedLockEntry entry : entries) {
            if (!entry.hasLockActions()) {
                // null = all actions locked → plain string, no field needed
                out.value(entry.getId());
            } else {
                // Compute unlock_actions = all known actions minus the locked ones
                List<String> locked = entry.getLockActions();
                List<String> unlocked = new ArrayList<>();
                for (String action : NamedLockEntry.ALL_ACTIONS) {
                    if (!locked.contains(action)) unlocked.add(action);
                }
                if (unlocked.isEmpty()) {
                    // All actions are locked — same as null, write as plain string
                    out.value(entry.getId());
                } else {
                    out.beginObject();
                    out.name("id").value(entry.getId());
                    out.name("unlock_actions");
                    out.beginArray();
                    for (String action : unlocked) {
                        out.value(action);
                    }
                    out.endArray();
                    out.endObject();
                }
            }
        }
        out.endArray();
    }

    @Override
    public List<NamedLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<NamedLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new NamedLockEntry(in.nextString()));
            } else {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
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
                entries.add(new NamedLockEntry(id, lockActions));
            }
        }
        in.endArray();
        return entries;
    }
}
