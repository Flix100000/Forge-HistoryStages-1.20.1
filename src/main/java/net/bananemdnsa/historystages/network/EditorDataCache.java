package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for editor data received from the server.
 */
public class EditorDataCache {
    private static Map<String, StageEntry> stages = new HashMap<>();
    /** Live unlock counts for global temporary stages (stageId → times unlocked). */
    private static Map<String, Integer> temporaryCounts = new HashMap<>();
    /** Remaining ticks until re-lock for currently-unlocked global temporary stages, as of the last sync. */
    private static Map<String, Long> temporaryActiveTicks = new HashMap<>();

    public static void setStages(Map<String, StageEntry> stages) {
        EditorDataCache.stages = stages != null ? stages : new HashMap<>();
    }

    public static Map<String, StageEntry> getStages() {
        return stages;
    }

    public static void setTemporaryCounts(Map<String, Integer> counts) {
        EditorDataCache.temporaryCounts = counts != null ? counts : new HashMap<>();
    }

    /** Returns how often a global temporary stage has been unlocked (0 if unknown). */
    public static int getTemporaryCount(String stageId) {
        return temporaryCounts.getOrDefault(stageId, 0);
    }

    public static void setTemporaryActiveTicks(Map<String, Long> ticks) {
        EditorDataCache.temporaryActiveTicks = ticks != null ? ticks : new HashMap<>();
    }

    /**
     * Remaining ticks until a global temporary stage re-locks, or -1 if not active.
     * Returns the raw last-synced value (no client-side interpolation) so a paused
     * integrated server simply shows a frozen value instead of jittering between the
     * interpolated countdown and the stale sync.
     */
    public static long getTemporaryActiveTicks(String stageId) {
        return temporaryActiveTicks.getOrDefault(stageId, -1L);
    }

    public static void clear() {
        stages.clear();
        temporaryCounts.clear();
        temporaryActiveTicks.clear();
    }
}
