package net.bananemdnsa.historystages.data;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        for (String structure : value.getStructures()) {
            out.value(structure);
        }
        out.endArray();
        if (!value.getModLinked().isEmpty()) {
            out.name("mod_linked");
            out.beginArray();
            for (String structure : value.getModLinked()) {
                out.value(structure);
            }
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

        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            result.setStructures(readStringArray(in));
            return result;
        }

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "structures" -> result.setStructures(readStringArray(in));
                case "mod_linked" -> result.setModLinked(readStringArray(in));
                default -> in.skipValue();
            }
        }
        in.endObject();
        return result;
    }

    private static List<String> readStringArray(JsonReader in) throws IOException {
        List<String> values = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            values.add(in.nextString());
        }
        in.endArray();
        return values;
    }
}
