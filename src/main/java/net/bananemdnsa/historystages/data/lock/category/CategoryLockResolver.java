package net.bananemdnsa.historystages.data.lock.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageStateView;

/**
 * Answers "which stages gate this subject, and are any of them still locked?" for a
 * {@link LockCategory} — the part an addon should never have to reimplement.
 *
 * <p>An addon supplies only {@link LockCategory#matches}: does one stored entry gate one runtime
 * object. Everything else — walking every stage, reading that category's entries off each one,
 * skipping stages the player already has, deduplicating — happens here, once, correctly.
 *
 * <p>Deliberately free of Minecraft: no {@code StageManager}, no caches, no logging through
 * Mojang or NeoForge types. Every input arrives as an argument, which is what keeps this class on
 * the test runtime classpath, where only JUnit and Gson are available — see the Phase 0 lock
 * engine split, which this mirrors.
 */
public final class CategoryLockResolver {

    /** Whether asking this category about that scope means anything at all. */
    public static boolean supports(LockCategory<?> category,
            net.bananemdnsa.historystages.data.lock.engine.StageScope scope) {
        return category.supportedScopes().contains(scope);
    }


    private CategoryLockResolver() {}

    /**
     * The ids of the stages that gate {@code subject} through {@code category} and are not yet
     * unlocked, in {@code stages}' iteration order, each id at most once.
     */
    public static List<String> missingStages(LockCategory<?> category, Object subject,
            Map<String, StageEntry> stages, StageStateView state) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
            String stageId = stage.getKey();
            if (state.isUnlocked(stageId)) continue;
            if (gates(category, stage.getValue(), subject)) {
                missing.add(stageId);
            }
        }
        return missing;
    }

    /**
     * Whether {@code subject} is currently locked by {@code category} on any of {@code stages}.
     * Short-circuits on the first still-locked gating stage instead of collecting the full list.
     */
    public static boolean isLocked(LockCategory<?> category, Object subject,
            Map<String, StageEntry> stages, StageStateView state) {
        for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
            String stageId = stage.getKey();
            if (state.isUnlocked(stageId)) continue;
            if (gates(category, stage.getValue(), subject)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any entry this category reads off {@code stage} gates {@code subject}.
     *
     * <p>The unchecked cast is safe: {@code category.read(stage)} and {@code category.matches}
     * both operate on the same category's own entry type {@code T}, so the list handed back from
     * {@code read} is exactly what {@code matches} expects — this method just isn't allowed to
     * spell {@code T} out itself since it only has {@code LockCategory<?>}.
     */
    @SuppressWarnings("unchecked")
    private static boolean gates(LockCategory<?> category, StageEntry stage, Object subject) {
        LockCategory<Object> erased = (LockCategory<Object>) category;
        for (Object entry : erased.read(stage)) {
            if (erased.matches(entry, subject)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combines the global and individual halves of a query, global first. Returns the input list
     * itself when the other side is empty, so the common "gated in one scope only" case allocates
     * nothing.
     */
    public static List<String> join(List<String> global, List<String> individual) {
        if (individual.isEmpty()) return global;
        if (global.isEmpty()) return individual;
        List<String> all = new ArrayList<>(global);
        all.addAll(individual);
        return all;
    }
}
