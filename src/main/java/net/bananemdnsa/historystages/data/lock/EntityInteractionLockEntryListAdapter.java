package net.bananemdnsa.historystages.data.lock;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson adapter for List&lt;EntityInteractionLockEntry&gt; that supports both the compact string
 * format ("minecraft:villager") and the full object format with per-entry action overrides.
 *
 * <p>Write format: {@code { "id": "…", "unlock_actions": ["trade"] }} — the list contains the
 * actions that are <em>not</em> locked. Absent field / plain string = all actions locked.
 *
 * <p>Read: accepts the current {@code unlock_actions} key and the legacy {@code lock_actions}
 * key (e.g. for hand-written configs). Plain string entries are kept as "all actions locked".
 */
public class EntityInteractionLockEntryListAdapter extends TypeAdapter<List<EntityInteractionLockEntry>> {

    @Override
    public void write(JsonWriter out, List<EntityInteractionLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (EntityInteractionLockEntry entry : entries) {
            // Compute unlocked actions (complement of locked), if any action filter is set.
            List<String> unlockedActions = new ArrayList<>();
            if (entry.hasLockActions()) {
                List<String> locked = entry.getLockActions();
                for (String action : EntityInteractionLockEntry.ALL_ACTIONS) {
                    if (!locked.contains(action)) unlockedActions.add(action);
                }
            }
            if (unlockedActions.isEmpty()) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            out.name("unlock_actions");
            out.beginArray();
            for (String action : unlockedActions) out.value(action);
            out.endArray();
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<EntityInteractionLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<EntityInteractionLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new EntityInteractionLockEntry(in.nextString()));
            } else {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                List<String> lockActions = null;
                if (obj.has("unlock_actions") && obj.get("unlock_actions").isJsonArray()) {
                    List<String> unlocked = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("unlock_actions")) {
                        unlocked.add(el.getAsString());
                    }
                    lockActions = new ArrayList<>();
                    for (String action : EntityInteractionLockEntry.ALL_ACTIONS) {
                        if (!unlocked.contains(action)) lockActions.add(action);
                    }
                } else if (obj.has("lock_actions") && obj.get("lock_actions").isJsonArray()) {
                    lockActions = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray("lock_actions")) {
                        lockActions.add(el.getAsString());
                    }
                }
                entries.add(new EntityInteractionLockEntry(id, lockActions));
            }
        }
        in.endArray();
        return entries;
    }
}
