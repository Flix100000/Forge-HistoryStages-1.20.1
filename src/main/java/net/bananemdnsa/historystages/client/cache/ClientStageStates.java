package net.bananemdnsa.historystages.client.cache;

import net.bananemdnsa.historystages.api.stage.StageStateView;

/**
 * Client-side {@link StageStateView} factories. Separate from
 * {@code data.lock.engine.StageLocks} on purpose — that class is reachable from server code,
 * and these two caches are client-only.
 */
public final class ClientStageStates {

    private ClientStageStates() {}

    public static StageStateView global() {
        return ClientStageCache::isStageUnlocked;
    }

    public static StageStateView individual() {
        return ClientIndividualStageCache::isStageUnlocked;
    }
}
