package net.bananemdnsa.historystages.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson adapter for StructureLocks that handles both:
 * - Legacy flat array: "structures": ["mod:s1", "mod:s2"]
 * - Current object:   "structures": {"structures": [...], "mod_linked": [...]}
 *
 * Always writes the object format.
 */
public class StructureLocksAdapter extends TypeAdapter<StructureLocks> {

    @Override
    public void write(JsonWriter out, StructureLocks value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("structures");
        out.beginArray();
        for (String s : value.getStructures()) out.value(s);
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
    public StructureLocks read(JsonReader in) throws IOException {
        StructureLocks result = new StructureLocks();

        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return result;
        }

        // Legacy format: flat array
        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            List<String> structures = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) structures.add(in.nextString());
            in.endArray();
            result.setStructures(structures);
            return result;
        }

        // Current format: object
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "structures" -> {
                    List<String> list = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) list.add(in.nextString());
                    in.endArray();
                    result.setStructures(list);
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
