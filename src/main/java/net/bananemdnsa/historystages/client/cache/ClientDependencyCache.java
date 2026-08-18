package net.bananemdnsa.historystages.client.cache;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for dependency check results.
 * Updated when server sends SyncDependencyStatusPacket.
 *
 * <p>Keyed by {@link StageManager#graphKey(String, boolean)} rather than stage id alone: a
 * global and an individual stage may share an id (that is exactly why {@code graph_layout.json}
 * keeps separate {@code global} and {@code individual} sections), so a plain-id key would let
 * one tree's result overwrite the other's.
 */
public class ClientDependencyCache {
    private static final Map<String, DependencyResult> CACHE = new ConcurrentHashMap<>();

    public static void update(String stageId, boolean individual, DependencyResult result) {
        CACHE.put(StageManager.graphKey(stageId, individual), result);
    }

    public static DependencyResult get(String stageId, boolean individual) {
        return CACHE.get(StageManager.graphKey(stageId, individual));
    }

    public static boolean isFulfilled(String stageId, boolean individual) {
        DependencyResult result = get(stageId, individual);
        return result == null || result.isFulfilled();
    }

    public static void clear() {
        CACHE.clear();
    }

    public static void remove(String stageId, boolean individual) {
        CACHE.remove(StageManager.graphKey(stageId, individual));
    }
}
