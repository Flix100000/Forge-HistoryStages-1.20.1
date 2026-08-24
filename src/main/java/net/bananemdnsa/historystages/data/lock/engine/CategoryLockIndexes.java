package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.LockRelevanceIndex;
import net.bananemdnsa.historystages.data.lock.category.DualPhaseIndex;
import net.minecraft.world.item.Item;

/**
 * The two structures a category-driven lock check derives from the stages, and the one place that
 * knows when they went stale.
 *
 * <p>Both are rebuilt from the stage maps, so both are invalidated by exactly one event: the
 * stages changed. {@link StageLocks#stagesChanged()} is that event. The stage store raises it and
 * knows nothing else about locking — which is what lets a later engine hang a different derived
 * structure, a bitmask bake, off the same signal.
 *
 * <p>The relevance index is rebuilt lazily on first use after a change, because loading a stage
 * tree makes hundreds of writes and eager rebuilding would repeat the work for every one of them.
 * The dual-phase index is rebuilt eagerly, because building it also produces the messages the
 * pack author sees in the loading report; deferring it would move those messages to whenever
 * something first happened to ask.
 */
public final class CategoryLockIndexes {

    private CategoryLockIndexes() {}

    private static final Object REBUILD_LOCK = new Object();

    // IMPORTANT: a stale relevance index reports a staged item as irrelevant and silently
    // unlocks it. Every write to the stage maps has to reach markRelevanceDirty().
    private static volatile boolean relevanceDirty = true;
    private static volatile LockRelevanceIndex global = LockRelevanceIndex.EMPTY;
    private static volatile LockRelevanceIndex individual = LockRelevanceIndex.EMPTY;

    private static volatile DualPhaseIndex dualPhase = DualPhaseIndex.empty();

    /** Marks the relevance index stale; the next query rebuilds it. */
    public static void markRelevanceDirty() {
        relevanceDirty = true;
    }

    /** Drops the dual-phase index. Used when the stage store is cleared before a reload. */
    public static void clearDualPhase() {
        dualPhase = DualPhaseIndex.empty();
    }

    /**
     * Rebuilds the dual-phase index and hands back the messages it produced, in order.
     *
     * <p>Returning the messages rather than logging them keeps this class free of Minecraft and
     * of the mod's logging sinks; the caller owns where they go.
     */
    public static List<String> rebuildDualPhase(Map<String, StageEntry> globalStages,
                                                Map<String, StageEntry> individualStages) {
        DualPhaseIndex index = DualPhaseIndex.build(globalStages, individualStages);
        dualPhase = index;
        return index.messages();
    }

    /** Dual-phase entries of one category on global stages, by id. Empty when it has none. */
    public static Map<String, Set<String>> dualPhaseGlobal(String categoryId) {
        return dualPhase.global(categoryId);
    }

    /** Individual-scope counterpart of {@link #dualPhaseGlobal}. */
    public static Map<String, Set<String>> dualPhaseIndividual(String categoryId) {
        return dualPhase.individual(categoryId);
    }

    /**
     * Global stages that could reference this item. Empty means no stage can match and the caller
     * may skip its scan; a returned stage still has to be checked properly.
     */
    public static Collection<String> globalCandidates(String itemId, String modId, Item item) {
        rebuildRelevanceIfDirty();
        return global.candidateStages(itemId, modId, item);
    }

    /** Individual-stage counterpart of {@link #globalCandidates}. */
    public static Collection<String> individualCandidates(String itemId, String modId, Item item) {
        rebuildRelevanceIfDirty();
        return individual.candidateStages(itemId, modId, item);
    }

    private static void rebuildRelevanceIfDirty() {
        if (!relevanceDirty) return;
        synchronized (REBUILD_LOCK) {
            if (!relevanceDirty) return;
            global = LockRelevanceIndex.build(StageManager.getStages());
            individual = LockRelevanceIndex.build(StageManager.getIndividualStages());
            relevanceDirty = false;
        }
    }
}
