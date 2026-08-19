package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Combines "which stages gate this subject" with "what this viewer has unlocked".
 *
 * <p>Two policies exist, and which one applies is a caller decision, not an engine one:
 * STRICT (the mod's default — one missing gating stage is enough to lock) and LENIENT
 * (used by JEI/EMI hiding when {@code Config.CLIENT.lockedItemMultiStagePolicy == LENIENT}
 * — a subject counts as available as soon as any one of its gating stages is unlocked).
 */
public final class LockResolution {

    private LockResolution() {}

    /** STRICT, single scope: locked as soon as one gating stage is still locked. */
    public static boolean isLocked(Collection<String> gatingStages, StageStateView state) {
        for (String stage : gatingStages) {
            if (!state.isUnlocked(stage)) return true;
        }
        return false;
    }

    /** STRICT across both scopes. Equivalent to OR-ing the two single-scope answers. */
    public static boolean isLocked(Collection<String> globalGating, StageStateView globalState,
                                   Collection<String> individualGating, StageStateView individualState) {
        return isLocked(globalGating, globalState) || isLocked(individualGating, individualState);
    }

    /**
     * LENIENT across both scopes: an ungated subject is never locked, and a gated one is locked
     * only when none of its gating stages — in either scope — is unlocked.
     */
    public static boolean isLockedLenient(Collection<String> globalGating, StageStateView globalState,
                                          Collection<String> individualGating, StageStateView individualState) {
        if (globalGating.isEmpty() && individualGating.isEmpty()) return false;
        for (String stage : globalGating) {
            if (globalState.isUnlocked(stage)) return false;
        }
        for (String stage : individualGating) {
            if (individualState.isUnlocked(stage)) return false;
        }
        return true;
    }

    /** The still-locked gating stages, in the order the engine returned them. */
    public static List<String> missingStages(Collection<String> gatingStages, StageStateView state) {
        List<String> missing = new ArrayList<>();
        for (String stage : gatingStages) {
            if (!state.isUnlocked(stage)) missing.add(stage);
        }
        return missing;
    }
}
