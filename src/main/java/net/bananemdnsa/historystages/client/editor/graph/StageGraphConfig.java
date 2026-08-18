package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.bananemdnsa.historystages.data.graph.StageStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side facade that resolves a node's final style across the three layers: the built-in
 * default baked into {@code graph.toml}'s spec, the pack's current {@code graph.toml} values, and
 * the per-stage override in {@code settings/graph_stages.json}.
 *
 * <p>This class (unlike {@link ResolvedStyle}) is free to depend on Minecraft/Forge types —
 * {@link GraphConfig} pulls in {@code ForgeConfigSpec} — because only the pure merge needs to run
 * under plain JUnit.
 *
 * <p>Results are cached per stage id + collection + state since the canvas asks for a style once
 * per node per frame. Call {@link #invalidateCache()} whenever the backing data changes; the
 * call sites are {@code SyncGraphConfigPacket.apply}, {@code SyncStageDefinitionsPacket.handle}
 * and the two optimistic local writes, {@code StageStyleScreen.save} and
 * {@code StageGraphScreen.applyPaste}.
 */
public final class StageGraphConfig {

    private static final Map<String, ResolvedStyle> CACHE = new ConcurrentHashMap<>();

    private StageGraphConfig() {}

    /** The style for one node, with the per-stage override already applied. */
    public static ResolvedStyle styleFor(String stageId, boolean individual, NodeState state) {
        String key = stageId + '|' + individual + '|' + state;
        return CACHE.computeIfAbsent(key, k -> resolve(stageId, individual, state));
    }

    private static ResolvedStyle resolve(String stageId, boolean individual, NodeState state) {
        GraphConfig.StyleBlock block = block(individual, state);
        ResolvedStyle base = new ResolvedStyle(
                block.shape.get().name(),
                block.size.get(),
                block.cornerRadius.get(),
                ResolvedStyle.parseColor(block.border.get(), 0),
                block.borderWidth.get(),
                ResolvedStyle.parseColor(block.fill.get(), 0),
                block.fillOpacity.get(),
                block.label.get().name(),
                ResolvedStyle.parseColor(block.labelColor.get(), 0),
                block.checkmark.get());

        // Both layers of the per-stage file at once: the all-states block with the per-state
        // block folded on top. The cache key already carries the state, so nothing else changes.
        StageStyle override = GraphStageData.get().style(stageId, individual, state);
        return ResolvedStyle.merge(base, override);
    }

    private static GraphConfig.StyleBlock block(boolean individual, NodeState state) {
        GraphConfig.Graph graph = GraphConfig.GRAPH;
        return switch (state) {
            case UNLOCKED -> individual ? graph.individualUnlocked : graph.globalUnlocked;
            case REACHABLE -> individual ? graph.individualReachable : graph.globalReachable;
            case LOCKED -> individual ? graph.individualLocked : graph.globalLocked;
        };
    }

    /** Drops every cached style. Must be called whenever graph.toml or the stage data changes. */
    public static void invalidateCache() {
        CACHE.clear();
    }
}
