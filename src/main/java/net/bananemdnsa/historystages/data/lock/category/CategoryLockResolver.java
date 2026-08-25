package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageStateView;

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
            net.bananemdnsa.historystages.api.stage.StageScope scope) {
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
     * Whether this category on this stage gates {@code subject}.
     *
     * <p>Asks the category rather than looping its entries here, because two built-ins answer
     * from more than their own entries — see {@link LockCategory#gates}. Doing the loop in this
     * class instead would make those overrides unreachable.
     *
     * <p>The unchecked cast is safe: {@code gates} is declared on the category's own entry type
     * {@code T} and does not take one as a parameter, so nothing untyped crosses the call. This
     * method just isn't allowed to spell {@code T} out, having only {@code LockCategory<?>}.
     */
    @SuppressWarnings("unchecked")
    private static boolean gates(LockCategory<?> category, StageEntry stage, Object subject) {
        return ((LockCategory<Object>) category).gates(stage, subject);
    }

    /**
     * Every stage that gates {@code subject} through {@code category}, in {@code stages}'
     * iteration order — <em>without</em> looking at what anyone has unlocked.
     *
     * <p>That is the half a {@link net.bananemdnsa.historystages.data.lock.engine.StageLockEngine}
     * answers; pairing it with a viewer is the caller's job, through
     * {@link net.bananemdnsa.historystages.data.lock.engine.LockResolution}. The state-filtered
     * {@link #missingStages} exists for the other caller, {@link CategoryLocks}.
     */
    public static List<String> gatingStages(LockCategory<?> category, Object subject,
            Map<String, StageEntry> stages) {
        // Allocated on first hit, not up front: nearly every call finds nothing, and this one
        // runs on paths that fire per mob spawn and per tick.
        List<String> found = null;
        for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
            if (gates(category, stage.getValue(), subject)) {
                if (found == null) found = new ArrayList<>(1);
                found.add(stage.getKey());
            }
        }
        return found == null ? List.of() : found;
    }

    /**
     * The same question asked of several categories at once, restricted to {@code stageIds}.
     *
     * <p>Two things this shape buys, both behavioural rather than cosmetic. First, the categories
     * are OR-ed <em>per stage</em>, so a stage that gates an item both by id and by mod is
     * reported once — asking each category separately would report it twice, and in a different
     * order, and that order is user-visible in the "you still need" tooltip. Second,
     * {@code stageIds} lets the caller pass a pre-narrowed candidate list from the relevance
     * index instead of the whole stage map, which is what keeps the per-frame item path cheap.
     *
     * <p>An id that {@code stages} does not know is skipped: the index is a deliberate
     * over-approximation and may still name a stage that has been removed since.
     */
    public static List<String> gatingStages(List<? extends LockCategory<?>> categories, Object subject,
            Collection<String> stageIds, Map<String, StageEntry> stages) {
        List<String> found = null;
        for (String stageId : stageIds) {
            StageEntry stage = stages.get(stageId);
            if (stage == null) continue;
            for (LockCategory<?> category : categories) {
                if (gates(category, stage, subject)) {
                    if (found == null) found = new ArrayList<>(1);
                    found.add(stageId);
                    break;
                }
            }
        }
        return found == null ? List.of() : found;
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
