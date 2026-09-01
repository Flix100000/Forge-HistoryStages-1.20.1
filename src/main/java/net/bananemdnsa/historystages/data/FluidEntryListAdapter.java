package net.bananemdnsa.historystages.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import net.bananemdnsa.historystages.api.lock.LockActions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes a stage's fluid list.
 *
 * <p>Shaped after {@link ItemEntryListAdapter}, with one difference that matters: the
 * {@code unlock_actions} key stores the <em>complement</em> of the locked actions, and the
 * complement here is taken against {@link LockActions#FLUID}, not the item vocabulary. Against
 * the item list a fully locked fluid would be written out as
 * {@code unlock_actions: ["equip","attack","break","gui"]} — four actions it never had — and
 * would come back on the next load as an entry that gates almost nothing.
 */
public class FluidEntryListAdapter extends TypeAdapter<List<FluidEntry>> {

    @Override
    public void write(JsonWriter out, List<FluidEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (FluidEntry entry : entries) {
            boolean hasOverride = entry.hasNameTextOverride() || entry.hasTooltipTextOverride();
            if (!entry.hasLockActions() && !hasOverride) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            if (entry.hasLockActions()) {
                List<String> locked = entry.getLockActions();
                List<String> unlocked = new ArrayList<>();
                for (String action : LockActions.FLUID) {
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
            if (entry.hasNameTextOverride()) {
                out.name("name_text").value(entry.getNameTextOverride());
            }
            if (entry.hasTooltipTextOverride()) {
                out.name("tooltip_text").value(entry.getTooltipTextOverride());
            }
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<FluidEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<FluidEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new FluidEntry(in.nextString()));
                continue;
            }
            JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            List<String> lockActions = null;
            if (obj.has("unlock_actions") && obj.get("unlock_actions").isJsonArray()) {
                // Current format: unlock_actions lists the NOT-locked actions → invert to get
                // the locked ones, against the fluid vocabulary.
                List<String> unlocked = new ArrayList<>();
                for (JsonElement el : obj.getAsJsonArray("unlock_actions")) {
                    unlocked.add(el.getAsString());
                }
                lockActions = new ArrayList<>();
                for (String action : LockActions.FLUID) {
                    if (!unlocked.contains(action)) lockActions.add(action);
                }
            } else if (obj.has("lock_actions") && obj.get("lock_actions").isJsonArray()) {
                // Legacy format, kept for the same reason ItemEntryListAdapter keeps it: a file
                // written by hand must still load.
                lockActions = new ArrayList<>();
                for (JsonElement el : obj.getAsJsonArray("lock_actions")) {
                    lockActions.add(el.getAsString());
                }
            }
            String nameText = obj.has("name_text") ? obj.get("name_text").getAsString() : null;
            String tooltipText = obj.has("tooltip_text")
                    ? obj.get("tooltip_text").getAsString() : null;
            entries.add(new FluidEntry(id, lockActions, nameText, tooltipText));
        }
        in.endArray();
        return entries;
    }
}
