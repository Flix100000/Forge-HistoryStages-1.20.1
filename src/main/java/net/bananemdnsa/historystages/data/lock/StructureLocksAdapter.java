package net.bananemdnsa.historystages.data.lock;

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
 *
 * <p>{@code block_generation} entries come in two shapes: a bare string (the legacy "block
 * entirely" rule) or an object with {@code id}/{@code phase}/{@code max}/{@code reset_on_relock}
 * (a counted limit). Entries that are still exactly the legacy rule are written back as bare
 * strings so files that never touch limits stay byte-for-byte in the old shape.
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
        List<StructureGenerationRule> rules = value.getGenerationRules();
        if (!rules.isEmpty()) {
            out.name("block_generation");
            out.beginArray();
            for (StructureGenerationRule r : rules) {
                if (r.isLegacyBlock()) {
                    out.value(r.id());
                    continue;
                }
                out.beginObject();
                out.name("id").value(r.id());
                out.name("phase").value(r.phase().serialize());
                out.name("max").value(r.max());
                if (r.resetOnRelock()) out.name("reset_on_relock").value(true);
                out.endObject();
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
                case "block_generation" -> {
                    List<StructureGenerationRule> rules = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        if (in.peek() == JsonToken.STRING) {
                            rules.add(StructureGenerationRule.blockEntirely(in.nextString()));
                            continue;
                        }
                        rules.add(readRule(in));
                    }
                    in.endArray();
                    rules.removeIf(r -> r == null || r.id() == null || r.id().isEmpty());
                    result.setGenerationRules(rules);
                }
                default -> in.skipValue();
            }
        }
        in.endObject();
        return result;
    }

    /** Reads one object-shaped rule. Unknown keys are skipped so future fields don't break loading. */
    private static StructureGenerationRule readRule(JsonReader in) throws IOException {
        String id = null;
        String phase = null;
        int max = 0;
        boolean reset = false;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id" -> id = in.nextString();
                case "phase" -> phase = in.nextString();
                case "max" -> max = in.nextInt();
                case "reset_on_relock" -> reset = in.nextBoolean();
                default -> in.skipValue();
            }
        }
        in.endObject();
        return id == null ? null : new StructureGenerationRule(id, GenerationPhase.parse(phase), max, reset);
    }
}
