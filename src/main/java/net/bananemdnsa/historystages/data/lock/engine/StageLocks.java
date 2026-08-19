package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;

/**
 * Entry point for every lock question, and the single line a later phase changes to switch the
 * mod onto a BitSet engine.
 *
 * <p>Client-side {@link StageStateView} factories deliberately live in
 * {@code client.cache.ClientStageStates} instead of here: this class is reachable from
 * server-only code paths, and pulling client caches into it is how the crash fixed in
 * commit 0469f73 happened.
 */
public final class StageLocks {

    private static volatile StageLockEngine engine = new StringStageLockEngine();

    private StageLocks() {}

    public static StageLockEngine engine() {
        return engine;
    }

    public static void setEngine(StageLockEngine newEngine) {
        engine = newEngine == null ? new StringStageLockEngine() : newEngine;
    }

    /** Restores the default string-backed engine. Used by tests. */
    public static void resetEngine() {
        engine = new StringStageLockEngine();
    }

    /** The world's global unlocked set, server side. */
    public static StageStateView serverGlobal() {
        return StageData.SERVER_CACHE::contains;
    }

    /** One player's individual unlocked set, server side. */
    public static StageStateView serverIndividual(UUID playerUuid) {
        return stageId -> IndividualStageData.SERVER_CACHE
                .getOrDefault(playerUuid, Collections.<String>emptySet())
                .contains(stageId);
    }

    /** Snapshot variant for callers that already hold the player's set. */
    public static StageStateView serverIndividual(Set<String> playerStages) {
        return StageStateView.of(playerStages);
    }
}
