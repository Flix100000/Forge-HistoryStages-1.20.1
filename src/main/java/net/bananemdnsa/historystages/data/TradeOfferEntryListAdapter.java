package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Reads and writes the {@code offers} list.
 *
 * <pre>
 * "offers": [
 *   { "merchant": "minecraft:librarian", "level": 2,
 *     "gives": "minecraft:bookshelf", "takes": ["minecraft:emerald"] }
 * ]
 * </pre>
 *
 * <p>The two prices are one {@code takes} array rather than two fields, because that is how a
 * trade reads: a thing costs these items. Most offers name one; none name more than two, and a
 * third would be ignored rather than fatal — a stage file is often hand-written, and the loader
 * naming the stage beats an exception thrown from inside Gson.
 *
 * <p>No counts. What makes a trade this trade is who offers it and which items change hands; the
 * numbers are rolled per villager. See {@link TradeOfferEntry}.
 */
public class TradeOfferEntryListAdapter extends TypeAdapter<List<TradeOfferEntry>> {

    @Override
    public void write(JsonWriter out, List<TradeOfferEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (TradeOfferEntry entry : entries) {
            if (entry == null || entry.givesId() == null) continue;
            out.beginObject();
            out.name("merchant").value(entry.merchantKey());
            out.name("level").value(entry.level());
            out.name("gives").value(entry.givesId());
            if (entry.takesAId() != null || entry.takesBId() != null) {
                out.name("takes");
                out.beginArray();
                if (entry.takesAId() != null) out.value(entry.takesAId());
                if (entry.takesBId() != null) out.value(entry.takesBId());
                out.endArray();
            }
            if (entry.hasNbt()) {
                out.name("nbt");
                writeJson(out, entry.nbt());
            }
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<TradeOfferEntry> read(JsonReader in) throws IOException {
        List<TradeOfferEntry> entries = new ArrayList<>();
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return entries;
        }
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() != JsonToken.BEGIN_OBJECT) {
                in.skipValue();
                continue;
            }
            JsonObject object = JsonParser.parseReader(in).getAsJsonObject();
            if (!object.has("merchant") || !object.has("gives")) continue;
            String merchant = object.get("merchant").getAsString();
            String gives = object.get("gives").getAsString();
            if (merchant.isEmpty() || gives.isEmpty()) continue;
            // A merchant with no levels is level 1, which is also what an absent field means.
            int level = object.has("level") ? object.get("level").getAsInt() : 1;
            String[] takes = readTakes(object.get("takes"));
            JsonObject nbt = object.has("nbt") && object.get("nbt").isJsonObject()
                    ? object.getAsJsonObject("nbt") : null;
            entries.add(new TradeOfferEntry(merchant, level, gives, takes[0], takes[1], nbt));
        }
        in.endArray();
        return entries;
    }

    /** Always two slots, either of which may be null. Anything past the second is dropped. */
    private static String[] readTakes(JsonElement element) {
        String[] takes = new String[2];
        if (element == null || !element.isJsonArray()) return takes;
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size() && i < 2; i++) {
            JsonElement value = array.get(i);
            if (value.isJsonPrimitive()) takes[i] = value.getAsString();
        }
        return takes;
    }

    private static void writeJson(JsonWriter out, JsonElement element) throws IOException {
        com.google.gson.internal.Streams.write(element, out);
    }
}
