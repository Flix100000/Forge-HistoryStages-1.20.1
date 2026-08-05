package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache for editor data received from the server.
 */
public class EditorDataCache {
    private static Map<String, StageEntry> stages = new HashMap<>();
    /** Live unlock counts for global temporary stages (stageId → times unlocked). */
    private static Map<String, Integer> temporaryCounts = new HashMap<>();
    /** Remaining ticks until re-lock for currently-unlocked global temporary stages, as of the last sync. */
    private static Map<String, Long> temporaryActiveTicks = new HashMap<>();

    /**
     * Player the individual maps below describe, or null when none was requested.
     *
     * <p>Individual temporary state is per player, and only the editor's currently picked
     * player is synced — sending every online player's counts would scale with the player
     * count for data only one row column ever shows.
     */
    private static UUID individualTarget = null;
    private static Map<String, Integer> individualCounts = new HashMap<>();
    private static Map<String, Long> individualActiveTicks = new HashMap<>();

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

    /** Replaces the individual temporary state, together with the player it belongs to. */
    public static void setIndividualTemporary(UUID target, Map<String, Integer> counts,
                                              Map<String, Long> ticks) {
        EditorDataCache.individualTarget = target;
        EditorDataCache.individualCounts = counts != null ? counts : new HashMap<>();
        EditorDataCache.individualActiveTicks = ticks != null ? ticks : new HashMap<>();
    }

    /**
     * True while the cache holds data for {@code player}. The reply is a round trip behind
     * the request, so switching the picker briefly leaves the previous player's numbers in
     * here — callers must not attribute them to whoever is picked now.
     */
    private static boolean describes(UUID player) {
        return player != null && player.equals(individualTarget);
    }

    /**
     * How often {@code player} has unlocked an individual temporary stage, or -1 while no
     * synced data covers them. Unknown is distinct from zero on purpose: the first frames
     * after the picker moves have nothing yet, and showing "0/5" there would state a wrong
     * count rather than none. A player who really has zero unlocks is sent an explicit 0.
     */
    public static int getIndividualTemporaryCount(UUID player, String stageId) {
        return describes(player) ? individualCounts.getOrDefault(stageId, -1) : -1;
    }

    /** Remaining ticks until re-lock for {@code player}, or -1 if not active or unknown. */
    public static long getIndividualTemporaryActiveTicks(UUID player, String stageId) {
        return describes(player) ? individualActiveTicks.getOrDefault(stageId, -1L) : -1L;
    }

    public static void clear() {
        stages.clear();
        temporaryCounts.clear();
        temporaryActiveTicks.clear();
        individualTarget = null;
        individualCounts.clear();
        individualActiveTicks.clear();
    }
}
