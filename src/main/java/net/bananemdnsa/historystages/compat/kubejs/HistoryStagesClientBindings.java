package net.bananemdnsa.historystages.compat.kubejs;

import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;

/**
 * The read-only half, bound as {@code HistoryStages} in {@code client_scripts}.
 *
 * <p>Answers from the synced snapshot the client already holds, which is the point: a pack that
 * wants its own tooltip or HUD element can ask without a round trip. Nothing here can change
 * state — on the client there would be nothing to change.
 */
public final class HistoryStagesClientBindings {

    private HistoryStagesClientBindings() {}

    public static boolean isUnlocked(String stageId) {
        return ClientStageCache.isStageUnlocked(stageId);
    }

    public static boolean isUnlockedIndividually(String stageId) {
        return ClientIndividualStageCache.isStageUnlocked(stageId);
    }

    /** Either scope, which is what a tooltip usually means by "does the player have this". */
    public static boolean hasStage(String stageId) {
        return ClientIndividualStageCache.isStageUnlocked(stageId)
                || ClientStageCache.isStageUnlocked(stageId);
    }
}
