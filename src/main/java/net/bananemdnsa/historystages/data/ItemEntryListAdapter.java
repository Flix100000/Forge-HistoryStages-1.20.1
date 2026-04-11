package net.bananemdnsa.historystages.data;

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
            if (!entry.hasNbt()) {
                out.value(entry.getId());
            } else {
                out.beginObject();
                out.name("id").value(entry.getId());
                out.name("nbt");
                Streams.write(entry.getNbt(), out);
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
                entries.add(new ItemEntry(id, nbt));
            }
        }
        in.endArray();
        return entries;
    }
}
