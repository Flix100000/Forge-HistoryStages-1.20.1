package net.bananemdnsa.historystages.client.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side view of every online player's individual-stage set, used by the stage
 * editor to render per-player lock state. Distinct from {@link ClientIndividualStageCache},
 * which holds only the local player's own stages and drives the gameplay locks —
 * this one is editor display data and is refreshed by polling while the editor is open.
 */
public class ClientPlayerStageCache {

    private static Map<UUID, Set<String>> playerStages = new HashMap<>();

    public static void set(Map<UUID, Set<String>> stages) {
        playerStages = stages != null ? stages : new HashMap<>();
    }

    /** False for unknown players, so a not-yet-synced player reads as "does not have it". */
    public static boolean hasStage(UUID player, String stageId) {
        Set<String> stages = playerStages.get(player);
        return stages != null && stages.contains(stageId);
    }

    public static void clear() {
        playerStages.clear();
    }
}
