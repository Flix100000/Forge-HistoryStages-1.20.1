package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.world.item.Item;

/**
 * Reverse lookup "which stages could reference this item?", built once per stage reload.
 *
 * <p>Without it, every lock check scans all stages and, inside each, all item entries with
 * {@code String.equals}. That scan short-circuits on a match, so a locked item is cheap — but an
 * item that matches nothing has to walk the whole thing, every call. Better Combat asks for the
 * held item's weapon attributes once per rendered entity per frame, which in a large pack turns
 * into thousands of string comparisons per frame for exactly the items that match nothing: items
 * that were never staged, and staged items whose stage is already unlocked.</p>
 *
 * <p>{@link #candidateStages} narrows that to the handful of stages that mention the item at all.
 * It is a deliberate over-approximation — mod exceptions and NBT criteria are not evaluated here,
 * so a returned stage may still turn out not to match. The caller therefore keeps running the
 * real per-stage check; the index only removes stages that cannot possibly match. It must never
 * do the opposite: a missing candidate would silently unlock an item.</p>
 *
 * <p>Tags are kept as their {@link NamedLockEntry} so the tag key stays lazily cached in the entry
 * and membership reads live tag data. A datapack tag reload therefore needs no rebuild — only a
 * change to the stages themselves does.</p>
 */
public final class LockRelevanceIndex {

    public static final LockRelevanceIndex EMPTY =
            new LockRelevanceIndex(Map.of(), Map.of(), List.of());

    /** A tag entry together with the stage that declared it. */
    private record TaggedStage(NamedLockEntry tag, String stageId) {}

    private final Map<String, List<String>> stagesByItemId;
    private final Map<String, List<String>> stagesByModId;
    private final List<TaggedStage> taggedStages;

    private LockRelevanceIndex(Map<String, List<String>> stagesByItemId,
                               Map<String, List<String>> stagesByModId,
                               List<TaggedStage> taggedStages) {
        this.stagesByItemId = stagesByItemId;
        this.stagesByModId = stagesByModId;
        this.taggedStages = taggedStages;
    }

    public static LockRelevanceIndex build(Map<String, StageEntry> stages) {
        if (stages == null || stages.isEmpty()) return EMPTY;

        Map<String, List<String>> byItem = new HashMap<>();
        Map<String, List<String>> byMod = new HashMap<>();
        List<TaggedStage> byTag = new ArrayList<>();

        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            String stageId = e.getKey();
            StageEntry stage = e.getValue();
            if (stage == null) continue;

            for (ItemEntry item : stage.getItemEntries()) {
                addStage(byItem, item.getId(), stageId);
            }
            for (NamedLockEntry mod : stage.getModEntries()) {
                addStage(byMod, mod.getId(), stageId);
            }
            for (NamedLockEntry tag : stage.getTagEntries()) {
                byTag.add(new TaggedStage(tag, stageId));
            }
        }

        if (byItem.isEmpty() && byMod.isEmpty() && byTag.isEmpty()) return EMPTY;
        return new LockRelevanceIndex(byItem, byMod, byTag);
    }

    /** A stage can list the same ID more than once; the candidate list stays free of duplicates. */
    private static void addStage(Map<String, List<String>> target, String key, String stageId) {
        List<String> stages = target.computeIfAbsent(key, k -> new ArrayList<>(1));
        if (!stages.contains(stageId)) stages.add(stageId);
    }

    public boolean isEmpty() {
        return stagesByItemId.isEmpty() && stagesByModId.isEmpty() && taggedStages.isEmpty();
    }

    /**
     * Stages that mention this item by ID, by mod, or through a tag it carries. An empty result
     * means no stage can match and the caller can skip its scan entirely.
     *
     * <p>The single-source cases return the stored list directly and allocate nothing — that is
     * the overwhelmingly common shape and the one the per-frame callers hit.</p>
     */
    public Collection<String> candidateStages(String itemId, String modId, Item item) {
        return merge(stagesByItemId.get(itemId), stagesByModId.get(modId), tagCandidates(item));
    }

    /**
     * ID and mod matches only, without consulting tags. Split out from
     * {@link #candidateStages} so it can be exercised without a live item registry.
     */
    public Collection<String> candidateStagesByIdOrMod(String itemId, String modId) {
        return merge(stagesByItemId.get(itemId), stagesByModId.get(modId), null);
    }

    /** Null lists mean "no hits from that source". Single-source results are returned as-is. */
    private static Collection<String> merge(List<String> byItem, List<String> byMod, List<String> byTag) {
        if (byMod == null && byTag == null) return byItem != null ? byItem : List.of();
        if (byItem == null && byTag == null) return byMod;
        if (byItem == null && byMod == null) return byTag;

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (byItem != null) merged.addAll(byItem);
        if (byMod != null) merged.addAll(byMod);
        if (byTag != null) merged.addAll(byTag);
        return merged;
    }

    /** Null when no tag entry matches — lets {@link #candidateStages} keep its allocation-free paths. */
    private List<String> tagCandidates(Item item) {
        if (taggedStages.isEmpty() || item == null) return null;
        List<String> found = null;
        for (TaggedStage tagged : taggedStages) {
            if (!item.builtInRegistryHolder().is(tagged.tag().getItemTagKey())) continue;
            if (found == null) found = new ArrayList<>(1);
            if (!found.contains(tagged.stageId())) found.add(tagged.stageId());
        }
        return found;
    }
}
