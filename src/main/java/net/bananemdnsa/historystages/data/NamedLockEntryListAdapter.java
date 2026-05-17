package net.bananemdnsa.historystages.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
                out.value(entry.getId());
                continue;
            }

            List<String> unlocked = new ArrayList<>();
            for (String action : NamedLockEntry.ALL_ACTIONS) {
                if (!entry.getLockActions().contains(action)) {
                    unlocked.add(action);
                }
            }
            if (unlocked.isEmpty()) {
                out.value(entry.getId());
                continue;
            }

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
                continue;
            }

            JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            List<String> lockActions = null;
            if (obj.has("unlock_actions") && obj.get("unlock_actions").isJsonArray()) {
                List<String> unlocked = new ArrayList<>();
                for (JsonElement element : obj.getAsJsonArray("unlock_actions")) {
                    unlocked.add(element.getAsString());
                }
                lockActions = new ArrayList<>();
                for (String action : NamedLockEntry.ALL_ACTIONS) {
                    if (!unlocked.contains(action)) {
                        lockActions.add(action);
                    }
                }
            } else if (obj.has("lock_actions") && obj.get("lock_actions").isJsonArray()) {
                lockActions = new ArrayList<>();
                for (JsonElement element : obj.getAsJsonArray("lock_actions")) {
                    lockActions.add(element.getAsString());
                }
            }
            entries.add(new NamedLockEntry(id, lockActions));
        }
        in.endArray();
        return entries;
    }
}
