package net.bananemdnsa.historystages.data.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a stage dependency map into grid coordinates.
 *
 * <p>Pure logic — no Minecraft types, no I/O, no statics that outlive a call. It runs
 * server-side only; clients receive the finished coordinates, because two clients computing
 * their own layout would show two different maps.
 *
 * <p>The result is a starting point, not a final answer. A pack author who dislikes it drags
 * the nodes and their positions win from then on. That is the whole reason this can stay
 * simple: an earlier attempt implemented the full Sugiyama pipeline and was still unreadable on
 * real packs, because readability across arbitrary pack structures cannot be guaranteed by an
 * algorithm.
 */
public final class GraphAutoLayout {

    /** Blank rows between two stacked components. */
    static final int COMPONENT_GAP = 2;
    /** Barycenter refinement passes. Alternates downward/upward; three is plenty. */
    static final int BARYCENTER_PASSES = 3;
    /** Width of the island block at the bottom, in columns. */
    static final int ISLAND_COLUMNS = 6;

    private GraphAutoLayout() {}

    /**
     * @param prerequisites node id -&gt; the ids it depends on. Every node must appear as a key,
     *                      including nodes with no prerequisites. Referenced ids that are not
     *                      keys are ignored (a dependency on a deleted stage).
     * @return one position per key of {@code prerequisites}
     */
    public static Map<String, GraphPos> compute(Map<String, Set<String>> prerequisites) {
        Map<String, Set<String>> prereq = sanitize(prerequisites);
        Map<String, Set<String>> dependents = invert(prereq);

        List<String> islands = new ArrayList<>();
        Set<String> connected = new TreeSet<>();
        for (String id : new TreeSet<>(prereq.keySet())) {
            if (prereq.get(id).isEmpty() && dependents.get(id).isEmpty()) islands.add(id);
            else connected.add(id);
        }

        Map<String, Integer> layer = assignLayers(connected, prereq);

        Map<String, GraphPos> out = new HashMap<>();
        int yCursor = 0;
        for (Set<String> component : components(connected, prereq, dependents)) {
            int height = placeComponent(component, layer, prereq, dependents, yCursor, out);
            yCursor += height + COMPONENT_GAP;
        }
        placeIslands(islands, yCursor, out);
        return out;
    }

    /** Drops references to ids that are not nodes, and guarantees every node is a key. */
    private static Map<String, Set<String>> sanitize(Map<String, Set<String>> in) {
        Map<String, Set<String>> out = new HashMap<>();
        for (String id : in.keySet()) out.put(id, new LinkedHashSet<>());
        for (Map.Entry<String, Set<String>> e : in.entrySet()) {
            if (e.getValue() == null) continue;
            for (String dep : e.getValue()) {
                if (out.containsKey(dep) && !dep.equals(e.getKey())) out.get(e.getKey()).add(dep);
            }
        }
        return out;
    }

    private static Map<String, Set<String>> invert(Map<String, Set<String>> prereq) {
        Map<String, Set<String>> out = new HashMap<>();
        for (String id : prereq.keySet()) out.put(id, new LinkedHashSet<>());
        for (Map.Entry<String, Set<String>> e : prereq.entrySet()) {
            for (String dep : e.getValue()) out.get(dep).add(e.getKey());
        }
        return out;
    }

    /**
     * Longest-path layering: {@code layer(n) = 1 + max(layer of prerequisites)}. Deliberately
     * not a BFS — with a BFS a stage reachable by a short and a long path lands left of its own
     * deepest prerequisite, and an edge then points backwards.
     */
    private static Map<String, Integer> assignLayers(Set<String> nodes, Map<String, Set<String>> prereq) {
        Map<String, Integer> layer = new HashMap<>();
        Set<String> inProgress = new HashSet<>();
        for (String id : nodes) depth(id, prereq, layer, inProgress);
        return layer;
    }

    private static int depth(String id, Map<String, Set<String>> prereq,
                             Map<String, Integer> layer, Set<String> inProgress) {
        Integer known = layer.get(id);
        if (known != null) return known;
        // Back edge: a cycle. Treat it as layer 0 so the walk terminates. The edge is still
        // drawn and StageManager.checkCircularDependencies() reports the cycle separately —
        // a skewed picture beats an infinite loop.
        if (!inProgress.add(id)) return 0;

        int max = -1;
        for (String dep : prereq.getOrDefault(id, Set.of())) {
            max = Math.max(max, depth(dep, prereq, layer, inProgress));
        }
        inProgress.remove(id);

        int result = max + 1;
        layer.put(id, result);
        return result;
    }

    /**
     * Connected components over the undirected view, ordered largest first and then by lowest
     * member id, so the stacking order never changes between two runs on the same data.
     */
    private static List<Set<String>> components(Set<String> nodes, Map<String, Set<String>> prereq,
                                                Map<String, Set<String>> dependents) {
        List<Set<String>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String start : nodes) {
            if (!seen.add(start)) continue;
            Set<String> component = new TreeSet<>();
            List<String> queue = new ArrayList<>();
            queue.add(start);
            component.add(start);
            while (!queue.isEmpty()) {
                String cur = queue.remove(queue.size() - 1);
                for (String n : neighbours(cur, prereq, dependents)) {
                    if (nodes.contains(n) && seen.add(n)) {
                        component.add(n);
                        queue.add(n);
                    }
                }
            }
            out.add(component);
        }
        out.sort(Comparator.<Set<String>>comparingInt(c -> -c.size())
                .thenComparing(c -> c.iterator().next()));
        return out;
    }

    private static Set<String> neighbours(String id, Map<String, Set<String>> prereq,
                                          Map<String, Set<String>> dependents) {
        Set<String> out = new TreeSet<>(prereq.getOrDefault(id, Set.of()));
        out.addAll(dependents.getOrDefault(id, Set.of()));
        return out;
    }

    /**
     * Places one component starting at {@code yOffset}. Returns its height in rows.
     *
     * <p>Rows inside a layer are ordered by barycenter — each node moves towards the mean row of
     * its neighbours — which is the cheap part of Sugiyama and the part that removes crossings.
     */
    private static int placeComponent(Set<String> component, Map<String, Integer> layer,
                                      Map<String, Set<String>> prereq,
                                      Map<String, Set<String>> dependents,
                                      int yOffset, Map<String, GraphPos> out) {
        Map<Integer, List<String>> byLayer = new TreeMap<>();
        for (String id : component) {
            byLayer.computeIfAbsent(layer.getOrDefault(id, 0), k -> new ArrayList<>()).add(id);
        }
        for (List<String> rows : byLayer.values()) Collections.sort(rows);

        Map<String, Integer> row = new HashMap<>();
        for (List<String> rows : byLayer.values()) {
            for (int i = 0; i < rows.size(); i++) row.put(rows.get(i), i);
        }

        for (int pass = 0; pass < BARYCENTER_PASSES; pass++) {
            boolean downward = pass % 2 == 0;
            List<Integer> order = new ArrayList<>(byLayer.keySet());
            if (!downward) Collections.reverse(order);

            for (int index : order) {
                List<String> rows = byLayer.get(index);
                Map<String, Double> bary = new HashMap<>();
                for (String id : rows) {
                    Set<String> refs = downward
                            ? prereq.getOrDefault(id, Set.of())
                            : dependents.getOrDefault(id, Set.of());
                    bary.put(id, barycenter(refs, row, row.get(id)));
                }
                rows.sort(Comparator.comparingDouble((String id) -> bary.get(id))
                        .thenComparing(Comparator.naturalOrder()));
                for (int i = 0; i < rows.size(); i++) row.put(rows.get(i), i);
            }
        }

        int height = 0;
        for (List<String> rows : byLayer.values()) height = Math.max(height, rows.size());

        for (Map.Entry<Integer, List<String>> e : byLayer.entrySet()) {
            List<String> rows = e.getValue();
            int pad = (height - rows.size()) / 2;   // centre the layer inside the component
            for (int i = 0; i < rows.size(); i++) {
                out.put(rows.get(i), new GraphPos(e.getKey(), yOffset + pad + i));
            }
        }
        return height;
    }

    /** Mean row of {@code refs}, or {@code fallback} when there are none. */
    private static double barycenter(Set<String> refs, Map<String, Integer> row, int fallback) {
        int count = 0;
        double sum = 0;
        for (String ref : refs) {
            Integer r = row.get(ref);
            if (r == null) continue;     // neighbour lives in another component
            sum += r;
            count++;
        }
        return count == 0 ? fallback : sum / count;
    }

    /** Stages with neither prerequisites nor dependents, in a block below everything else. */
    private static void placeIslands(List<String> islands, int yOffset, Map<String, GraphPos> out) {
        for (int i = 0; i < islands.size(); i++) {
            out.put(islands.get(i), new GraphPos(i % ISLAND_COLUMNS, yOffset + i / ISLAND_COLUMNS));
        }
    }
}
