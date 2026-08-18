package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.graph.GraphReachability;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which stages and node categories a graph view may show.
 *
 * <p>The admin view uses {@link #passThrough()} and sees everything. The player view uses
 * {@link #fromConfig()}, which applies the server's {@code graph.toml} settings.</p>
 *
 * <p>This is a display filter, not a security boundary: every client already holds the full
 * stage definitions in memory. It stops a player from trivially spoiling themselves — which
 * is why the settings are server-owned — not a determined one.</p>
 */
public class GraphViewFilter {

    private final boolean passThrough;
    private final GraphConfig.GraphVisibility visibility;
    private final boolean respectHiddenDisplay;
    private final boolean showStageElements;
    private final boolean showTriggers;
    private final boolean showIndividualStages;

    /**
     * Namespaced keys of the stages this filter admits. Computed once on construction — the
     * unlock state it is derived from cannot change while a single view is being built.
     * Null when passThrough.
     */
    private final Set<String> visible;

    private GraphViewFilter(boolean passThrough,
                            GraphConfig.GraphVisibility visibility,
                            boolean respectHiddenDisplay,
                            boolean showStageElements,
                            boolean showTriggers,
                            boolean showIndividualStages) {
        this.passThrough = passThrough;
        this.visibility = visibility;
        this.respectHiddenDisplay = respectHiddenDisplay;
        this.showStageElements = showStageElements;
        this.showTriggers = showTriggers;
        this.showIndividualStages = showIndividualStages;
        this.visible = passThrough ? null : computeVisible();
    }

    /** Admin view: everything, ignoring all config. */
    public static GraphViewFilter passThrough() {
        return new GraphViewFilter(true, GraphConfig.GraphVisibility.ALL,
                false, true, true, true);
    }

    /** Player view: reads the synced server config and the client's unlock caches. */
    public static GraphViewFilter fromConfig() {
        // showStageElements has no single equivalent in graph.toml any more — it fanned out
        // into the six [panel] item/xp/advancement/kill/stat/scoreboard toggles. This legacy
        // filter still gates all detail satellites together, so it shows them if any is on.
        boolean showStageElements = GraphConfig.GRAPH.showItems.get()
                || GraphConfig.GRAPH.showXp.get()
                || GraphConfig.GRAPH.showAdvancements.get()
                || GraphConfig.GRAPH.showKills.get()
                || GraphConfig.GRAPH.showStats.get()
                || GraphConfig.GRAPH.showScoreboard.get();

        return new GraphViewFilter(false,
                GraphConfig.GRAPH.visibilityMode.get(),
                GraphConfig.GRAPH.respectHiddenDisplay.get(),
                showStageElements,
                GraphConfig.GRAPH.showTriggers.get(),
                GraphConfig.GRAPH.showIndividualStages.get());
    }

    // --- Public queries ---------------------------------------------------

    /** True when this stage may appear at all. */
    public boolean showsStage(String stageId, boolean isIndividual) {
        if (passThrough) return true;
        if (isIndividual && !showIndividualStages) return false;
        return visible.contains(StageManager.graphKey(stageId, isIndividual));
    }

    /** True when DETAIL satellites (items/XP/kills/...) may be drawn. */
    public boolean showsDetails() {
        return passThrough || showStageElements;
    }

    /** True when TRIGGER satellites may be drawn. */
    public boolean showsTriggers() {
        return passThrough || showTriggers;
    }


    /**
     * True when this stage's name must be replaced by an anonymous placeholder.
     *
     * <p>Two conditions, both mirroring {@code HiddenDisplayResolver}:</p>
     * <ul>
     *   <li><b>Name mode only.</b> Only {@code getNameMode()} is consulted — never
     *       {@code isNoop()}, which also fires on {@code tooltip_mode} and
     *       {@code show_lock_hints}. Those two axes are deliberately independent of the
     *       name (see issue #96), so folding them in here would anonymise the name of a
     *       stage whose author only turned off lock hints. Note that
     *       {@code getHiddenDisplay()} never returns null: the "no config" sentinel is
     *       {@code nameMode == OFF}, not a null config.</li>
     *   <li><b>Locked only.</b> {@code HiddenDisplayConfig}'s effects apply only while the
     *       subject is locked for the viewing player, so an unlocked stage keeps its real
     *       name — hiding one the player has demonstrably already seen helps nobody.</li>
     * </ul>
     */
    public boolean anonymizes(String stageId, boolean isIndividual, StageEntry entry) {
        if (passThrough || !respectHiddenDisplay || entry == null) return false;
        if (GraphUnlocks.isUnlocked(StageManager.graphKey(stageId, isIndividual))) return false;
        return entry.getHiddenDisplay().getNameMode() != DisplayMode.OFF;
    }

    // --- Visibility computation -------------------------------------------

    /**
     * The admitted set, in namespaced graph keys.
     *
     * <p>{@code PROGRESSIVE} shows the unlocked stages, their direct neighbours in both
     * directions — what led here, what comes next — and on top of that everything that is
     * researchable right now, wherever it sits. That last part is what also surfaces the roots
     * of branches the player has not touched at all.</p>
     *
     * <p>{@code PROGRESSIVE_STRICT} drops exactly that third part and keeps the neighbourhood
     * around what the player owns. Note that this does <em>not</em> hide the researchable
     * stages the player could tackle next: those are successors of an unlocked stage and come
     * in as neighbours. What disappears is the researchable stages hanging off nothing the
     * player has — free-standing roots, and stages whose only requirements are items or XP.</p>
     *
     * <p>In both modes the neighbour ring is drawn around the UNLOCKED stages only, never
     * around the researchable ones as well. Doing the latter revealed a further layer of locked
     * stages up to two hops from anything the player actually owned, which is what made
     * {@code PROGRESSIVE} feel like it leaked half the map on a pack with many roots.</p>
     */
    private Set<String> computeVisible() {
        Map<String, List<StageManager.StageDepGroup>> prereqs = StageManager.graphDependencyGroups();
        Set<String> unlocked = new HashSet<>();
        for (String key : prereqs.keySet()) {
            if (GraphUnlocks.isUnlocked(key)) unlocked.add(key);
        }

        Set<String> out = new HashSet<>();
        switch (visibility) {
            case ALL -> out.addAll(prereqs.keySet());
            case UNLOCKED_ONLY -> out.addAll(unlocked);
            case PROGRESSIVE_STRICT -> out.addAll(withNeighbours(unlocked, prereqs));
            case PROGRESSIVE -> {
                out.addAll(withNeighbours(unlocked, prereqs));
                for (String key : prereqs.keySet()) {
                    if (GraphReachability.isOpen(key, prereqs, GraphUnlocks::isUnlocked)) out.add(key);
                }
            }
        }
        return out;
    }

    /** The given stages plus everything one dependency edge away, in either direction. */
    private static Set<String> withNeighbours(Set<String> seeds,
                                              Map<String, List<StageManager.StageDepGroup>> prereqs) {
        Set<String> out = new HashSet<>(seeds);
        for (String key : seeds) {
            out.addAll(prereqIdsOf(key, prereqs));
        }
        for (String key : prereqs.keySet()) {
            for (String dep : prereqIdsOf(key, prereqs)) {
                if (seeds.contains(dep)) {
                    out.add(key);
                    break;
                }
            }
        }
        return out;
    }

    /** All stage keys this stage references as prerequisites, group boundaries discarded. */
    private static Set<String> prereqIdsOf(String key,
                                           Map<String, List<StageManager.StageDepGroup>> prereqs) {
        Set<String> out = new HashSet<>();
        for (StageManager.StageDepGroup g : prereqs.getOrDefault(key, List.of())) {
            out.addAll(g.stageKeys());
        }
        return out;
    }
}
