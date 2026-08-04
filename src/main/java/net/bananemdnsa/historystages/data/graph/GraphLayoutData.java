package net.bananemdnsa.historystages.data.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes {@code settings/graph_layout.json} — the stage positions the pack author
 * owns.
 *
 * <p>A tree with a non-empty section is <em>frozen</em>: the author dragged something, the
 * whole auto-layout was written out at that moment, and the algorithm no longer touches it.
 * Re-arranging clears the section, which is a plain delete rather than a selective key removal —
 * that is exactly why the hand-written descriptions live in a different file
 * ({@link GraphStageData}). A bug in a selective removal would cost the author their texts.
 */
public final class GraphLayoutData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Snapshot current = Snapshot.empty();

    private GraphLayoutData() {}

    /**
     * Immutable positions plus, per tree, whether those positions are the author's or the
     * algorithm's.
     *
     * <p>The frozen flag is carried explicitly rather than derived from "the map is non-empty",
     * which is what an earlier version did and got wrong: {@code recomputeGraphLayout()} fills the
     * map of an <em>unfrozen</em> tree with computed positions on every load, so by the time
     * anyone can look, emptiness no longer distinguishes "the author placed these" from "we just
     * worked these out". Frozen-ness is a question of provenance, and provenance has to be
     * recorded, not inferred.
     *
     * <p>On disk it still is inferred — a tree only has a section in {@code graph_layout.json} once
     * it has been frozen, so {@link #fromJson} reads a non-empty section as frozen. The flag exists
     * for the in-memory and over-the-wire snapshots, where computed positions also live.
     */
    public record Snapshot(Map<String, GraphPos> global, Map<String, GraphPos> individual,
                           boolean globalFrozen, boolean individualFrozen) {

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), false, false);
        }

        public Map<String, GraphPos> tree(boolean individual) {
            return individual ? individual() : global();
        }

        /** True when the author owns this tree's layout — not merely when it has positions. */
        public boolean isFrozen(boolean individual) {
            return individual ? individualFrozen() : globalFrozen();
        }

        /** Replaces one tree's positions, keeping the other tree and both flags untouched. */
        public Snapshot withPositions(boolean individual, Map<String, GraphPos> positions) {
            return individual
                    ? new Snapshot(global(), positions, globalFrozen(), individualFrozen())
                    : new Snapshot(positions, individual(), globalFrozen(), individualFrozen());
        }
    }

    // --- pure conversion, unit-tested ---

    public static Snapshot fromJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return Snapshot.empty();
            JsonObject obj = root.getAsJsonObject();
            Map<String, GraphPos> global = section(obj, "global");
            Map<String, GraphPos> individual = section(obj, "individual");
            // A tree only gets a section in the file once it has been frozen, so on disk a
            // non-empty section is exactly the frozen ones.
            return new Snapshot(global, individual, !global.isEmpty(), !individual.isEmpty());
        } catch (Exception e) {
            // A hand-edited file with a typo must not take the graph down; the author sees an
            // unfrozen map and can re-arrange.
            DebugLogger.error("Stage Graph", "Could not parse graph_layout.json: " + e.getMessage());
            return Snapshot.empty();
        }
    }

    private static Map<String, GraphPos> section(JsonObject root, String name) {
        Map<String, GraphPos> out = new LinkedHashMap<>();
        if (!root.has(name) || !root.get(name).isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet()) {
            GraphPos pos = parsePos(e.getValue());
            if (pos != null) out.put(e.getKey(), pos);
        }
        return out;
    }

    private static GraphPos parsePos(JsonElement el) {
        if (el == null || !el.isJsonArray()) return null;
        JsonArray arr = el.getAsJsonArray();
        if (arr.size() != 2) return null;
        try {
            return new GraphPos(arr.get(0).getAsInt(), arr.get(1).getAsInt());
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.add("global", writeSection(snapshot.global()));
        root.add("individual", writeSection(snapshot.individual()));
        return GSON.toJson(root);
    }

    private static JsonObject writeSection(Map<String, GraphPos> positions) {
        JsonObject obj = new JsonObject();
        // Sorted so a re-save produces a stable diff for pack authors keeping this in git.
        for (Map.Entry<String, GraphPos> e : new TreeMap<>(positions).entrySet()) {
            JsonArray arr = new JsonArray();
            arr.add(e.getValue().x());
            arr.add(e.getValue().y());
            obj.add(e.getKey(), arr);
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
        File file = GraphSettingsPaths.file(GraphSettingsPaths.LAYOUT_FILE);
        if (!file.exists()) {
            current = Snapshot.empty();
            return;
        }
        try {
            current = fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not read graph_layout.json: " + e.getMessage());
            current = Snapshot.empty();
        }
    }

    public static void save() {
        File file = GraphSettingsPaths.file(GraphSettingsPaths.LAYOUT_FILE);
        try {
            Files.write(file.toPath(), toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not write graph_layout.json: " + e.getMessage());
        }
    }

    /** Freezes one tree: replaces its section wholesale and persists. */
    public static void freeze(boolean individual, Map<String, GraphPos> positions) {
        Map<String, GraphPos> copy = new HashMap<>(positions);
        current = individual
                ? new Snapshot(current.global(), copy, current.globalFrozen(), true)
                : new Snapshot(copy, current.individual(), true, current.individualFrozen());
        save();
    }

    /** Discards one tree's layout so the algorithm owns it again. */
    public static void clear(boolean individual) {
        current = individual
                ? new Snapshot(current.global(), Map.of(), current.globalFrozen(), false)
                : new Snapshot(Map.of(), current.individual(), false, current.individualFrozen());
        save();
    }

    /** Client-side entry point used by the definition sync. */
    public static void setFromSync(Map<String, GraphPos> global, Map<String, GraphPos> individual,
                                   boolean globalFrozen, boolean individualFrozen) {
        current = new Snapshot(new HashMap<>(global), new HashMap<>(individual),
                globalFrozen, individualFrozen);
    }
}
