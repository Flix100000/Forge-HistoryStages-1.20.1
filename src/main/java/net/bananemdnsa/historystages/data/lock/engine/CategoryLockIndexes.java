package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.LockRelevanceIndex;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;
import net.bananemdnsa.historystages.data.lock.category.DualPhaseIndex;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
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

    // categoryId -> key -> stage ids, per scope. Null while stale; rebuilt on first use after a
    // change, for the same reason the relevance index is: loading a stage tree makes hundreds of
    // writes and would otherwise rebuild once per write.
    private static volatile Map<String, Map<String, List<String>>> globalKeys = null;
    private static volatile Map<String, Map<String, List<String>>> individualKeys = null;

    // The stage numbering, and everything that speaks in bits. All of it is derived: the
    // numbering from the stage ids, the player masks from the unlocked sets, the gating masks
    // from the stages. Nothing here is ever written to disk or put on the wire.
    private static volatile StageIndex stageIndex = null;

    private static volatile StageMask globalMask = StageMask.EMPTY;
    private static volatile long globalMaskVersion = -1;
    private static volatile StageIndex globalMaskIndex = null;

    private static final Map<java.util.UUID, PlayerMask> playerMasks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** One player's unlocked set as bits, together with what it was built from. */
    private record PlayerMask(StageMask mask, long version, StageIndex index) {}

    // "Does any stage use this category at all" — per category id, filled on demand. Replaced
    // wholesale when the stages change rather than cleared in place: a reader that already holds
    // the old map keeps a consistent answer instead of racing a half-emptied one, and the next
    // reader gets the new one. A load that touches every stage costs one swap, not one per write.
    private static volatile Map<String, Boolean> categoryUsage = new java.util.concurrent.ConcurrentHashMap<>();

    /** Marks everything derived from the stages stale; the next query rebuilds what it needs. */
    public static void markRelevanceDirty() {
        relevanceDirty = true;
        categoryUsage = new java.util.concurrent.ConcurrentHashMap<>();
        globalKeys = null;
        individualKeys = null;
        stageIndex = null;
        itemGatingGlobal.clear();
        itemGatingIndividual.clear();
        // The player masks are keyed by the index they were built against, so they invalidate
        // themselves; clearing here only saves the memory.
        playerMasks.clear();
    }

    /**
     * The current stage numbering, rebuilt when the stages change.
     *
     * <p>Derived from the two stage maps, never stored. See {@link StageIndex} for why that
     * matters more than it looks.
     */
    public static StageIndex stageIndex() {
        StageIndex index = stageIndex;
        if (index == null) {
            index = StageIndex.of(StageManager.getStages().keySet(),
                    StageManager.getIndividualStages().keySet());
            stageIndex = index;
        }
        return index;
    }

    /** The world's unlocked global stages as bits, rebuilt when they change or the numbering does. */
    public static StageMask globalUnlocked() {
        StageIndex index = stageIndex();
        long version = net.bananemdnsa.historystages.data.saveddata.StageData.cacheVersion();
        if (globalMaskVersion != version || globalMaskIndex != index) {
            globalMask = StageMask.of(index,
                    net.bananemdnsa.historystages.data.saveddata.StageData.SERVER_CACHE);
            globalMaskVersion = version;
            globalMaskIndex = index;
        }
        return globalMask;
    }

    /** One player's unlocked individual stages as bits, on the same terms. */
    public static StageMask individualUnlocked(java.util.UUID playerUuid) {
        StageIndex index = stageIndex();
        long version =
                net.bananemdnsa.historystages.data.saveddata.IndividualStageData.cacheVersion();
        PlayerMask cached = playerMasks.get(playerUuid);
        if (cached != null && cached.version() == version && cached.index() == index) {
            return cached.mask();
        }
        StageMask mask = StageMask.of(index,
                net.bananemdnsa.historystages.data.saveddata.IndividualStageData.SERVER_CACHE
                        .getOrDefault(playerUuid, java.util.Set.of()));
        playerMasks.put(playerUuid, new PlayerMask(mask, version, index));
        return mask;
    }

    // ---- the memoised item answer -------------------------------------------------------

    /**
     * What gates one item, remembered.
     *
     * <p>{@code stages} is kept for the callers that print it — the "you still need" tooltip —
     * and {@code mask} is the same answer in bits, so a lock check is one pass over a few longs
     * rather than a lookup per stage. That is the shape a mod-tiered pack produces: twenty stages
     * each locking the same mod makes every item of that mod depend on all twenty.
     */
    public record ItemGating(List<String> stages, StageMask mask) {}

    private static final Map<String, ItemGating> itemGatingGlobal =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ItemGating> itemGatingIndividual =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The remembered answer for this item, or null when there is none to reuse. */
    @Nullable
    public static ItemGating rememberedItemGating(String itemId, StageScope scope) {
        return (scope == StageScope.GLOBAL ? itemGatingGlobal : itemGatingIndividual).get(itemId);
    }

    /**
     * Remembers what gates this item.
     *
     * <p>Only legal when the answer does not depend on the stack — no NBT criterion anywhere that
     * could match this item, no NBT mod exception. The engine decides that; getting it wrong here
     * would serve one enchanted sword's answer for every plain one.
     *
     * <p>Bounded, and cleared wholesale rather than evicted one at a time. A pack has tens of
     * thousands of items and something like JEI will ask about all of them; the cap keeps that
     * from growing without limit, and starting over costs one recomputation per item that is
     * still in use.
     */
    public static void rememberItemGating(String itemId, StageScope scope, ItemGating gating) {
        Map<String, ItemGating> memo =
                scope == StageScope.GLOBAL ? itemGatingGlobal : itemGatingIndividual;
        if (memo.size() >= MAX_REMEMBERED_ITEMS) memo.clear();
        memo.put(itemId, gating);
    }

    /** Generous: a large pack has tens of thousands of items, and each entry is a few dozen bytes. */
    private static final int MAX_REMEMBERED_ITEMS = 20_000;

    /**
     * The stages that could gate this key in this category, or null when the category does not
     * index itself and the caller has to scan.
     *
     * <p>A deliberate over-approximation, exactly like the relevance index in front of the item
     * scans: a named stage still has to pass {@link LockCategory#gates}. What it must never do is
     * leave one out — see the contract on {@link LockCategory#indexKeys}.
     *
     * <p>Empty list and null mean different things and both matter. Empty is "no stage can match,
     * skip the scan entirely", which is the common answer and the whole point. Null is "this
     * category is not indexed", which sends the caller to the full scan.
     */
    @Nullable
    public static List<String> candidates(String categoryId, StageScope scope, String key) {
        if (key == null) return null;
        Map<String, Map<String, List<String>>> byCategory = scope == StageScope.GLOBAL
                ? globalIndex() : individualIndex();
        Map<String, List<String>> byKey = byCategory.get(categoryId);
        if (byKey == null) return null;
        List<String> found = byKey.get(key);
        return found == null ? List.of() : found;
    }

    private static Map<String, Map<String, List<String>>> globalIndex() {
        Map<String, Map<String, List<String>>> index = globalKeys;
        if (index == null) {
            index = buildKeyIndex(StageManager.getStages());
            globalKeys = index;
        }
        return index;
    }

    private static Map<String, Map<String, List<String>>> individualIndex() {
        Map<String, Map<String, List<String>>> index = individualKeys;
        if (index == null) {
            index = buildKeyIndex(StageManager.getIndividualStages());
            individualKeys = index;
        }
        return index;
    }

    /**
     * One pass over the stages for every category that indexes itself. Categories that do not are
     * left out of the result entirely, which is how {@link #candidates} tells "no match" apart
     * from "not indexed".
     */
    private static Map<String, Map<String, List<String>>> buildKeyIndex(
            Map<String, StageEntry> stages) {
        Map<String, Map<String, List<String>>> index = new java.util.HashMap<>();
        for (LockCategory<?> category : LockCategories.all()) {
            Map<String, List<String>> byKey = null;
            for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
                for (String key : category.indexKeys(stage.getValue())) {
                    if (byKey == null) byKey = new java.util.HashMap<>();
                    List<String> ids = byKey.computeIfAbsent(key, k -> new java.util.ArrayList<>(1));
                    if (!ids.contains(stage.getKey())) ids.add(stage.getKey());
                }
            }
            // An indexed category with no entries anywhere still belongs in the map: its answer
            // is "no stage can match", and that is worth far more than falling back to a scan.
            if (byKey != null || indexesItself(category)) {
                index.put(category.id(), byKey == null ? Map.of() : byKey);
            }
        }
        return index;
    }

    /** Whether the category opts into the index at all, asked without a stage to hand. */
    private static boolean indexesItself(LockCategory<?> category) {
        return !category.indexKeys(PROBE).isEmpty() || category.lookupKey(PROBE_KEY) != null;
    }

    /** An empty stage and a bare id, used only to ask a category whether it indexes at all. */
    private static final StageEntry PROBE = new StageEntry();
    private static final String PROBE_KEY = "historystages:probe";

    /**
     * Whether any stage in either scope carries an entry of this category.
     *
     * <p>Drives the per-tick fast-outs in the structure and biome handlers, which ask twenty
     * times a second, forever. Walking both stage maps to answer costs 1.2us per scope at three
     * hundred stages — and worst when the answer is "no", because then there is no early hit to
     * stop at, which is exactly the case for a pack that gates neither. The answer can only
     * change when the stages do, so it is worked out once and kept.
     */
    public static boolean anyStageUses(String categoryId) {
        Map<String, Boolean> usage = categoryUsage;
        Boolean known = usage.get(categoryId);
        if (known != null) return known;

        LockCategory<?> category = LockCategories.byId(categoryId);
        boolean used = category != null
                && (scan(category, StageManager.getStages())
                        || scan(category, StageManager.getIndividualStages()));
        usage.put(categoryId, used);
        return used;
    }

    private static boolean scan(LockCategory<?> category, Map<String, StageEntry> stages) {
        for (StageEntry stage : stages.values()) {
            if (!category.read(stage).isEmpty()) return true;
        }
        return false;
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
        return globalCandidates(itemId, modId, item, null);
    }

    /**
     * The same, narrowed also by the fluid the stack is carrying.
     *
     * <p>A stage that gates only a fluid names no item, so without this it would never appear as
     * a candidate and its gate would never fire.
     */
    public static Collection<String> globalCandidates(String itemId, String modId, Item item,
                                                      String fluidId) {
        rebuildRelevanceIfDirty();
        return global.candidateStages(itemId, modId, item, fluidId);
    }

    /** Individual-stage counterpart of {@link #globalCandidates}. */
    public static Collection<String> individualCandidates(String itemId, String modId, Item item) {
        return individualCandidates(itemId, modId, item, null);
    }

    /** Individual-stage counterpart of {@link #globalCandidates(String, String, Item, String)}. */
    public static Collection<String> individualCandidates(String itemId, String modId, Item item,
                                                          String fluidId) {
        rebuildRelevanceIfDirty();
        return individual.candidateStages(itemId, modId, item, fluidId);
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
