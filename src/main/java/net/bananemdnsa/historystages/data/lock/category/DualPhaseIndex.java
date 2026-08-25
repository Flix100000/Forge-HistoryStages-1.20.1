package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;

/**
 * The result of scanning every stage for dual-phase overlaps — entries gated by a global stage
 * and an individual stage at the same time.
 *
 * <p>This class is pure: it takes the two stage maps and produces lookups plus the maintainer-
 * facing loading messages as plain strings. It does not log anything and does not know about
 * {@code StageManager}, which is what makes it independently testable. The caller (StageManager)
 * owns replaying {@link #messages()} through its own logging sinks.
 *
 * <p>Categories are walked in {@link LockCategories#all()} order (the editor's tab order) rather
 * than the historical hardcoded order, so the message list can come out in a different sequence
 * than the old {@code detectOverlaps()} produced. The message *set* is unchanged.
 */
public final class DualPhaseIndex {

    private static final DualPhaseIndex EMPTY = new DualPhaseIndex(Map.of(), Map.of(), List.of());

    private final Map<String, Map<String, Set<String>>> global;
    private final Map<String, Map<String, Set<String>>> individual;
    private final List<String> messages;

    private DualPhaseIndex(Map<String, Map<String, Set<String>>> global,
                            Map<String, Map<String, Set<String>>> individual,
                            List<String> messages) {
        this.global = global;
        this.individual = individual;
        this.messages = messages;
    }

    /** The state before any stages have ever loaded. */
    public static DualPhaseIndex empty() {
        return EMPTY;
    }

    /**
     * Scans {@code globalStages} and {@code individualStages} for overlaps, category by
     * category. A category whose {@link LockCategory#dualPhaseLabel()} is empty opted out of
     * dual-phase detection entirely and is skipped.
     */
    public static DualPhaseIndex build(Map<String, StageEntry> globalStages, Map<String, StageEntry> individualStages) {
        Map<String, Map<String, Set<String>>> global = new HashMap<>();
        Map<String, Map<String, Set<String>>> individual = new HashMap<>();
        List<String> messages = new ArrayList<>();

        for (LockCategory<?> category : LockCategories.all()) {
            if (category.dualPhaseLabel().isEmpty()) continue;

            Map<String, Set<String>> globalLookup = new HashMap<>();
            for (Map.Entry<String, StageEntry> entry : globalStages.entrySet()) {
                String gStageId = entry.getKey();
                for (String id : category.globalDualPhaseIds(entry.getValue())) {
                    globalLookup.computeIfAbsent(id, k -> new HashSet<>()).add(gStageId);
                }
            }
            if (globalLookup.isEmpty()) continue;

            Map<String, Set<String>> globalHits = new HashMap<>();
            Map<String, Set<String>> individualHits = new HashMap<>();

            for (Map.Entry<String, StageEntry> entry : individualStages.entrySet()) {
                String iStageId = entry.getKey();
                for (String id : category.individualDualPhaseIds(entry.getValue())) {
                    Set<String> globalStagesForEntry = globalLookup.get(id);
                    if (globalStagesForEntry == null) continue;

                    globalHits.computeIfAbsent(id, k -> new HashSet<>()).addAll(globalStagesForEntry);
                    individualHits.computeIfAbsent(id, k -> new HashSet<>()).add(iStageId);
                    messages.add("Individual stage '" + iStageId + "' " + category.dualPhaseLabel() + " '" + id
                            + "' also in global stage(s) " + globalStagesForEntry + " — dual-phase lock registered.");
                }
            }

            if (!globalHits.isEmpty()) global.put(category.id(), globalHits);
            if (!individualHits.isEmpty()) individual.put(category.id(), individualHits);
        }

        return new DualPhaseIndex(global, individual, List.copyOf(messages));
    }

    /** Entry id → global stage ids that gate it, for the given category. Never null. */
    public Map<String, Set<String>> global(String categoryId) {
        return global.getOrDefault(categoryId, Map.of());
    }

    /** Entry id → individual stage ids that gate it, for the given category. Never null. */
    public Map<String, Set<String>> individual(String categoryId) {
        return individual.getOrDefault(categoryId, Map.of());
    }

    /** The "dual-phase lock registered" loading messages, one per overlap, in detection order. */
    public List<String> messages() {
        return messages;
    }
}
