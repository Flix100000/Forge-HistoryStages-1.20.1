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

    /**
     * One stage's hand-written data: a description, a style override, or both.
     *
     * <p>{@code style} applies in every state; {@code styles} layers on top of it per state.
     * Keeping the older key as the base is what lets a file written before per-state overrides
     * existed load unchanged — and it keeps "always a hexagon, red while locked" one line each
     * instead of three copies of the shape.
     *
     * <p>Also the wire form: {@code SaveStageGraphStylePacket} sends an {@code Entry} whose
     * {@code description} is null, so the fragment on the wire is a fragment of the file.
     */
    public static class Entry {
        /** Literal text or a translation key; null = none. */
        public String description;
        /** Applies in every state. May be null. */
        public StageStyle style;
        /** Per-state, layered over {@link #style}. May be null. */
        public StateStyles styles;

        public boolean isEmpty() {
            return (description == null || description.isBlank())
                    && (style == null || style.isEmpty())
                    && (styles == null || styles.isEmpty());
        }

        /**
         * True when either style block carries anything. Reads the fields rather than going
         * through {@link #copyStyles()}, because the sidebar asks this once per row per frame
         * and three objects per stage per frame is a lot of garbage for a 3×3 dot.
         */
        public boolean hasStyles() {
            return (style != null && !style.isEmpty()) || (styles != null && !styles.isEmpty());
        }

        /** The style halves only, deep-copied — for an edit buffer or the clipboard. */
        public Entry copyStyles() {
            Entry out = new Entry();
            out.style = style == null ? null : style.copy();
            out.styles = styles == null ? null : styles.copy();
            return out;
        }
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

        /**
         * Never null; an empty {@link StageStyle} when the stage has no override. A copy, so a
         * caller cannot write back into the loaded snapshot through the style it was handed.
         */
        public StageStyle style(String stageId, boolean individual) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null || entry.style == null) return new StageStyle();
            return entry.style.copy();
        }

        /**
         * The override that applies to one node: the all-states block with the per-state block
         * folded on top. Never null; an empty {@link StageStyle} when the stage has neither.
         */
        public StageStyle style(String stageId, boolean individual, NodeState state) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null) return new StageStyle();
            StageStyle perState = entry.styles == null ? null : entry.styles.get(state);
            return StageStyle.overlay(entry.style, perState);
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
            Entry updated = new Entry();
            updated.description = (text == null || text.isBlank()) ? null : text;
            updated.style = existing == null ? null : existing.style;
            updated.styles = existing == null ? null : existing.styles;

            if (updated.isEmpty()) {
                copy.remove(stageId);
            } else {
                copy.put(stageId, updated);
            }

            return individual
                    ? new Snapshot(global(), copy)
                    : new Snapshot(copy, individual());
        }

        /**
         * Returns a new snapshot with the given stage's style halves replaced by the ones in
         * {@code source}, keeping any description untouched. An entry left with nothing at all is
         * dropped, so the file does not accumulate empty objects.
         */
        public Snapshot withStyle(String stageId, boolean individual, Entry source) {
            Map<String, Entry> copy = new LinkedHashMap<>(tree(individual));

            Entry existing = copy.get(stageId);
            Entry updated = new Entry();
            updated.description = existing == null ? null : existing.description;
            updated.style = source == null || source.style == null || source.style.isEmpty()
                    ? null : source.style.copy();
            updated.styles = source == null || source.styles == null || source.styles.isEmpty()
                    ? null : source.styles.copy();

            if (updated.isEmpty()) {
                copy.remove(stageId);
            } else {
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

    /** One entry's style halves as JSON — the wire form of an override set. */
    public static String entryToJson(Entry entry) {
        Entry styleOnly = entry == null ? new Entry() : entry.copyStyles();
        return GSON.toJson(styleOnly);
    }

    /** Inverse of {@link #entryToJson}. Never null; an empty entry on anything unparseable. */
    public static Entry entryFromJson(String json) {
        try {
            Entry entry = GSON.fromJson(json, Entry.class);
            if (entry == null) return new Entry();
            // The wire form carries styles only. A description arriving here would be a client
            // writing a field this packet has no permission story for.
            entry.description = null;
            return entry;
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not parse a style override: " + e.getMessage());
            return new Entry();
        }
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
