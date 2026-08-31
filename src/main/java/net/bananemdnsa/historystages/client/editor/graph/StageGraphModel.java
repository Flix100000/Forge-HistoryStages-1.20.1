package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.graph.GraphReachability;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The set of stage nodes and dependency edges a graph view is allowed to show right now.
 *
 * <p>Pure data, built once per view open (or whenever the backing config/state changes). The
 * canvas, sidebar and detail panel all read from this rather than re-deriving visibility
 * themselves, so drawing code never has to reason about {@link GraphViewFilter} directly.</p>
 */
public final class StageGraphModel {

    /** One drawable stage. */
    public record Node(String stageId, boolean individual, GraphPos pos,
                       NodeState state, boolean anonymous, String label, String icon) {}

    /** Both endpoints are guaranteed present in {@link #nodes()}. */
    public record Edge(String fromKey, String toKey, boolean satisfied, boolean orGroup) {}

    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private final List<Node> unplaced;

    private StageGraphModel(Map<String, Node> nodes, List<Edge> edges, List<Node> unplaced) {
        this.nodes = nodes;
        this.edges = edges;
        this.unplaced = unplaced;
    }

    /** Keyed by {@link StageManager#graphKey(String, boolean)}. */
    public Map<String, Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    /** Non-empty only for a frozen tree in editor view; see {@link #buildNodes} for why. */
    public List<Node> unplaced() {
        return unplaced;
    }

    /**
     * Builds the model. {@code filter} decides which stages survive at all;
     * {@code editorView} additionally controls whether stages missing a stored position are
     * surfaced via {@link #unplaced()} (only the editor's authoring UI can act on that list) —
     * either way such a stage cannot be drawn and is left out of {@link #nodes()}.
     */
    public static StageGraphModel build(GraphViewFilter filter, boolean editorView) {
        Map<String, List<StageManager.StageDepGroup>> prereqs = StageManager.graphDependencyGroups();
        GraphLayoutData.Snapshot layout = GraphLayoutData.get();

        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Node> unplaced = new ArrayList<>();

        buildNodes(StageManager.getStages(), false, filter, prereqs, layout, editorView, nodes, unplaced);
        buildNodes(StageManager.getIndividualStages(), true, filter, prereqs, layout, editorView, nodes, unplaced);

        List<Edge> edges = buildEdges(nodes, prereqs);

        return new StageGraphModel(nodes, edges, unplaced);
    }

    private static void buildNodes(Map<String, StageEntry> stages, boolean individual,
                                   GraphViewFilter filter,
                                   Map<String, List<StageManager.StageDepGroup>> prereqs,
                                   GraphLayoutData.Snapshot layout, boolean editorView,
                                   Map<String, Node> nodes, List<Node> unplaced) {
        boolean frozen = layout.isFrozen(individual);
        Map<String, GraphPos> positions = layout.tree(individual);

        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            String id = e.getKey();
            if (!filter.showsStage(id, individual)) continue;

            StageEntry entry = e.getValue();
            String key = StageManager.graphKey(id, individual);
            NodeState state = GraphReachability.resolve(key, prereqs, GraphUnlocks::isUnlocked);
            boolean anonymous = filter.anonymizes(id, individual, entry);
            String label = anonymous ? hiddenLabel() : entry.getDisplayName();
            String icon = anonymous ? defaultIcon() : resolveIcon(entry);

            GraphPos pos = positions.get(id);
            if (pos == null) {
                // A frozen tree keeps whatever the author placed; a stage added since then has
                // no entry. An unfrozen tree computes a position for every known stage up front
                // (StageManager.recomputeGraphLayout), so this branch should not occur there —
                // treat it defensively the same way rather than trust the invariant blindly.
                if (frozen && editorView) {
                    unplaced.add(new Node(id, individual, GraphPos.of(0, 0), state, anonymous, label, icon));
                }
                continue;
            }
            nodes.put(key, new Node(id, individual, pos, state, anonymous, label, icon));
        }
    }

    /**
     * An edge is emitted only when both {@code fromKey} (the prerequisite) and {@code toKey}
     * (the dependent) survived into {@code nodes}. A stage the filter removed, or one still
     * waiting to be placed in a frozen tree, was never added to {@code nodes} above — so every
     * dependency touching it is skipped here too, and no dangling line or placeholder "???" node
     * is ever produced.
     *
     * <p>{@code satisfied} needs the same restraint the node states do: an edge whose group
     * cannot be evaluated from here — an individual stage demanded of everyone — is drawn open
     * rather than met, however much of it the viewer personally holds.
     *
     * <p>{@code orGroup} comes from the group the reference sits in, so the canvas can draw
     * alternatives differently ({@code [edges] orGroupStyle}). If the same prerequisite appears in
     * more than one group, the first occurrence wins and the duplicate is dropped — two lines
     * between the same pair of nodes would only look like a rendering fault.
     */
    private static List<Edge> buildEdges(Map<String, Node> nodes,
                                         Map<String, List<StageManager.StageDepGroup>> prereqs) {
        List<Edge> edges = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (Map.Entry<String, List<StageManager.StageDepGroup>> e : prereqs.entrySet()) {
            String toKey = e.getKey();
            if (!nodes.containsKey(toKey)) continue;
            for (StageManager.StageDepGroup group : e.getValue()) {
                for (String fromKey : group.stageKeys()) {
                    Node fromNode = nodes.get(fromKey);
                    if (fromNode == null) continue;
                    if (!seen.add(fromKey + "->" + toKey)) continue;
                    boolean satisfied = group.checkableKeys().contains(fromKey)
                            && fromNode.state() == NodeState.UNLOCKED;
                    edges.add(new Edge(fromKey, toKey, satisfied, group.or()));
                }
            }
        }
        return edges;
    }

    /** Localized placeholder shown instead of a name the filter has anonymised. */
    private static String hiddenLabel() {
        return Component.translatable("editor.historystages.graph.hidden_stage").getString();
    }

    private static String defaultIcon() {
        return Config.VISUAL.defaultStageIcon.get();
    }

    private static String resolveIcon(StageEntry entry) {
        return entry.getIcon().isEmpty() ? defaultIcon() : entry.getIcon();
    }
}
