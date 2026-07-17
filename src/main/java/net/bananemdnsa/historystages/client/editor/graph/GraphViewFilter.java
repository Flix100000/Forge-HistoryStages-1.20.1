package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.display.DisplayMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which stages and node categories a graph view may show.
 *
 * <p>The admin view uses {@link #passThrough()} and sees everything. The player view uses
 * {@link #fromConfig()}, which applies the server's {@code [graph]} settings.</p>
 *
 * <p>This is a display filter, not a security boundary: every client already holds the full
 * stage definitions in memory. It stops a player from trivially spoiling themselves — which
 * is why the settings are server-owned — not a determined one.</p>
 */
public class GraphViewFilter {

    private final boolean passThrough;
    private final Config.Common.GraphVisibility visibility;
    private final boolean respectHiddenDisplay;
    private final boolean showStageElements;
    private final boolean showTriggers;
    private final boolean showIndividualStages;

    private final Map<String, StageEntry> global;
    private final Map<String, StageEntry> individual;
    private final Set<String> unlockedGlobal;
    private final Set<String> unlockedIndividual;

    /** Ids of stages this filter admits. Computed once on construction. Null when passThrough. */
    private final Set<String> visible;

    private GraphViewFilter(boolean passThrough,
                            Config.Common.GraphVisibility visibility,
                            boolean respectHiddenDisplay,
                            boolean showStageElements,
                            boolean showTriggers,
                            boolean showIndividualStages,
                            Map<String, StageEntry> global,
                            Map<String, StageEntry> individual,
                            Set<String> unlockedGlobal,
                            Set<String> unlockedIndividual) {
        this.passThrough = passThrough;
        this.visibility = visibility;
        this.respectHiddenDisplay = respectHiddenDisplay;
        this.showStageElements = showStageElements;
        this.showTriggers = showTriggers;
        this.showIndividualStages = showIndividualStages;
        this.global = global;
        this.individual = individual;
        this.unlockedGlobal = unlockedGlobal;
        this.unlockedIndividual = unlockedIndividual;
        this.visible = passThrough ? null : computeVisible();
    }

    /** Admin view: everything, ignoring all config. */
    public static GraphViewFilter passThrough() {
        return new GraphViewFilter(true, Config.Common.GraphVisibility.ALL,
                false, true, true, true,
                new HashMap<>(), new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    /** Player view: reads the synced server config and the client's unlock caches. */
    public static GraphViewFilter fromConfig() {
        Map<String, StageEntry> global = StageManager.getStages();
        Map<String, StageEntry> individual = StageManager.getIndividualStages();

        Set<String> unlockedGlobal = new HashSet<>();
        for (String id : global.keySet()) {
            if (ClientStageCache.isStageUnlocked(id)) unlockedGlobal.add(id);
        }
        Set<String> unlockedIndividual = new HashSet<>();
        for (String id : individual.keySet()) {
            if (ClientIndividualStageCache.isStageUnlocked(id)) unlockedIndividual.add(id);
        }

        return new GraphViewFilter(false,
                Config.COMMON.graphVisibility.get(),
                Config.COMMON.graphRespectHiddenDisplay.get(),
                Config.COMMON.graphShowStageElements.get(),
                Config.COMMON.graphShowTriggers.get(),
                Config.COMMON.graphShowIndividualStages.get(),
                global, individual, unlockedGlobal, unlockedIndividual);
    }

    // --- Public queries ---------------------------------------------------

    /** True when this stage may appear at all. */
    public boolean showsStage(String stageId, boolean isIndividual) {
        if (passThrough) return true;
        if (isIndividual && !showIndividualStages) return false;
        return visible.contains(stageId);
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
    public boolean anonymizes(String stageId, StageEntry entry) {
        if (passThrough || !respectHiddenDisplay || entry == null) return false;
        if (isUnlocked(stageId)) return false;
        return entry.getHiddenDisplay().getNameMode() != DisplayMode.OFF;
    }

    // --- Visibility computation -------------------------------------------

    private Set<String> computeVisible() {
        Set<String> out = new HashSet<>();
        switch (visibility) {
            case ALL -> {
                out.addAll(global.keySet());
                out.addAll(individual.keySet());
            }
            case UNLOCKED_ONLY -> {
                out.addAll(unlockedGlobal);
                out.addAll(unlockedIndividual);
            }
            case PROGRESSIVE -> {
                // Base: unlocked, plus everything currently researchable (roots included).
                Set<String> base = new HashSet<>();
                base.addAll(unlockedGlobal);
                base.addAll(unlockedIndividual);
                for (String id : allIds()) {
                    if (stagePrereqsSatisfied(id)) base.add(id);
                }
                out.addAll(base);
                // One step of lookahead: direct neighbours of the base, both directions.
                for (String id : base) {
                    out.addAll(stagePrereqIdsOf(id));
                    for (String other : allIds()) {
                        if (stagePrereqIdsOf(other).contains(id)) out.add(other);
                    }
                }
            }
        }
        return out;
    }

    private Set<String> allIds() {
        Set<String> ids = new HashSet<>(global.keySet());
        ids.addAll(individual.keySet());
        return ids;
    }

    private StageEntry entryOf(String id) {
        StageEntry e = global.get(id);
        return e != null ? e : individual.get(id);
    }

    private boolean isUnlocked(String id) {
        return unlockedGlobal.contains(id) || unlockedIndividual.contains(id);
    }

    /** All stage ids this stage references as prerequisites (global + individual). */
    private Set<String> stagePrereqIdsOf(String id) {
        Set<String> out = new HashSet<>();
        StageEntry e = entryOf(id);
        if (e == null) return out;
        for (DependencyGroup g : e.getDependencies()) {
            if (g.isEmpty()) continue;
            out.addAll(g.getStages());
            for (IndividualStageDep d : g.getIndividualStages()) {
                if (d.getStageId() != null) out.add(d.getStageId());
            }
        }
        return out;
    }

    /**
     * True when every dependency group's STAGE requirements are met — i.e. the stage is
     * researchable right now as far as stages go.
     *
     * <p>Only stage requirements are considered. Item/XP/kill requirements are deliberately
     * ignored: the client cannot evaluate them reliably, and "you still need to gather the
     * iron" does not make a stage unreachable — it is exactly what the player wants to see.</p>
     *
     * <p>Groups are AND-combined (matching {@code DependencyChecker.checkAll}); within a
     * group, {@code isOr()} selects OR.</p>
     */
    private boolean stagePrereqsSatisfied(String id) {
        StageEntry e = entryOf(id);
        if (e == null) return false;
        for (DependencyGroup g : e.getDependencies()) {
            if (g.isEmpty()) continue;
            if (!groupStageReqsSatisfied(g)) return false;
        }
        return true;
    }

    private boolean groupStageReqsSatisfied(DependencyGroup g) {
        Set<String> refs = new HashSet<>(g.getStages());
        for (IndividualStageDep d : g.getIndividualStages()) {
            if (d.getStageId() != null) refs.add(d.getStageId());
        }
        // No stage requirements in this group — nothing here can block it.
        if (refs.isEmpty()) return true;

        if (g.isOr()) {
            // An OR group can also be satisfied through its non-stage requirements, so a
            // locked stage ref does not block it if any other requirement exists.
            if (hasNonStageReqs(g)) return true;
            for (String r : refs) {
                if (isUnlocked(r)) return true;
            }
            return false;
        }
        for (String r : refs) {
            if (!isUnlocked(r)) return false;
        }
        return true;
    }

    private boolean hasNonStageReqs(DependencyGroup g) {
        return !g.getItems().isEmpty()
                || !g.getAdvancements().isEmpty()
                || g.getXpLevel() != null
                || !g.getEntityKills().isEmpty()
                || !g.getStats().isEmpty()
                || !g.getScoreboard().isEmpty();
    }
}
