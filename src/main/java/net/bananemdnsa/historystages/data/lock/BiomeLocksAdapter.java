package net.bananemdnsa.historystages.data.lock;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson adapter for BiomeLocks that handles both:
 * - Flat array:     "biomes": ["minecraft:desert", "#minecraft:is_forest"]
 * - Current object: "biomes": {"biomes": [...], "mod_linked": [...]}
 *
 * Always writes the object format.
 */
public class BiomeLocksAdapter extends TypeAdapter<BiomeLocks> {

    @Override
    public void write(JsonWriter out, BiomeLocks value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("biomes");
        out.beginArray();
        for (String s : value.getBiomes()) out.value(s);
        out.endArray();
        if (!value.getModLinked().isEmpty()) {
            out.name("mod_linked");
            out.beginArray();
            for (String s : value.getModLinked()) out.value(s);
            out.endArray();
        }
        out.endObject();
    }

    @Override
    public BiomeLocks read(JsonReader in) throws IOException {
        BiomeLocks result = new BiomeLocks();

        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return result;
        }

        // Hand-written packs may use a flat array instead of the object form
        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            List<String> biomes = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) biomes.add(in.nextString());
            in.endArray();
            result.setBiomes(biomes);
            return result;
        }

        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "biomes" -> {
                    List<String> list = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) list.add(in.nextString());
                    in.endArray();
                    result.setBiomes(list);
                }
                case "mod_linked" -> {
                    List<String> list = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) list.add(in.nextString());
                    in.endArray();
                    result.setModLinked(list);
                }
                default -> in.skipValue();
            }
        }
        in.endObject();
        return result;
    }
}
