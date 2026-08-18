package net.bananemdnsa.historystages.data.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes {@code settings/graph_stages.json} — the per-stage descriptions and style
 * overrides the pack author hand-writes.
 *
 * <p>This is deliberately a different file from {@code graph_layout.json}
 * ({@link GraphLayoutData}) even though both are keyed by stage id: the two have different
 * owners. The layout file is written by the machine (the auto-layout algorithm, and dragging in
 * the editor), so "re-arrange" is a plain delete of that file. If the hand-written descriptions
 * lived alongside it, resetting the layout would become a selective key removal — and a bug
 * there would cost a pack author their texts. Keeping them apart makes "re-arrange" trivially
 * safe.
 */
public final class GraphStageData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Snapshot current = Snapshot.empty();

    private GraphStageData() {}

    /** One stage's hand-written data: a description, a style override, or both. */
    public static class Entry {
        /** Literal text or a translation key; null = none. */
        public String description;
        /** May be null. */
        public StageStyle style;
    }

    /** Immutable pair of entry maps, one per stage tree. */
    public record Snapshot(Map<String, Entry> global, Map<String, Entry> individual) {

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }

        public Map<String, Entry> tree(boolean individual) {
            return individual ? individual() : global();
        }

        /** Null when the stage has no entry, or its description is absent or blank. */
        public String description(String stageId, boolean individual) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null || entry.description == null || entry.description.isBlank()) {
                return null;
            }
            return entry.description;
        }

        /** Never null; an empty {@link StageStyle} when the stage has no override. */
        public StageStyle style(String stageId, boolean individual) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null || entry.style == null) return new StageStyle();
            return entry.style;
        }

        /**
         * Returns a new snapshot with the given stage's description replaced. A null or blank
         * {@code text} removes the description; if the entry is then left with neither a
         * description nor a style, it is dropped entirely so the file does not accumulate empty
         * objects.
         */
        public Snapshot withDescription(String stageId, boolean individual, String text) {
            Map<String, Entry> copy = new LinkedHashMap<>(tree(individual));

            Entry existing = copy.get(stageId);
            StageStyle existingStyle = existing != null ? existing.style : null;
            String trimmed = (text == null || text.isBlank()) ? null : text;

            if (trimmed == null && (existingStyle == null || existingStyle.isEmpty())) {
                copy.remove(stageId);
            } else {
                Entry updated = new Entry();
                updated.description = trimmed;
                updated.style = existingStyle;
                copy.put(stageId, updated);
            }

            return individual
                    ? new Snapshot(global(), copy)
                    : new Snapshot(copy, individual());
        }
    }

    // --- pure conversion, unit-tested ---

    public static Snapshot fromJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return Snapshot.empty();
            JsonObject obj = root.getAsJsonObject();
            return new Snapshot(section(obj, "global"), section(obj, "individual"));
        } catch (Exception e) {
            // A hand-edited file with a typo must not take the graph down; the author sees no
            // descriptions/styles for now and can fix the file.
            DebugLogger.error("Stage Graph", "Could not parse graph_stages.json: " + e.getMessage());
            return Snapshot.empty();
        }
    }

    private static Map<String, Entry> section(JsonObject root, String name) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (!root.has(name) || !root.get(name).isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet()) {
            try {
                Entry entry = GSON.fromJson(e.getValue(), Entry.class);
                if (entry != null) out.put(e.getKey(), entry);
            } catch (Exception ex) {
                // Skip one malformed entry; the rest of the file must still load.
            }
        }
        return out;
    }

    public static String toJson(Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.add("global", writeSection(snapshot.global()));
        root.add("individual", writeSection(snapshot.individual()));
        return GSON.toJson(root);
    }

    private static JsonObject writeSection(Map<String, Entry> entries) {
        JsonObject obj = new JsonObject();
        // Sorted so a re-save produces a stable diff for pack authors keeping this in git.
        for (Map.Entry<String, Entry> e : new TreeMap<>(entries).entrySet()) {
            obj.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        }
        return obj;
    }

    // --- file access + in-memory state ---

    public static Snapshot get() {
        return current;
    }

    public static void set(Snapshot snapshot) {
        current = snapshot;
    }

    public static void load() {
        File file = GraphSettingsPaths.file(GraphSettingsPaths.STAGES_FILE);
        if (!file.exists()) {
            current = Snapshot.empty();
            return;
        }
        try {
            current = fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not read graph_stages.json: " + e.getMessage());
            current = Snapshot.empty();
        }
    }

    public static void save() {
        File file = GraphSettingsPaths.file(GraphSettingsPaths.STAGES_FILE);
        try {
            Files.write(file.toPath(), toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not write graph_stages.json: " + e.getMessage());
        }
    }
}
