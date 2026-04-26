package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.data.dependency.DependencyResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for dependency check results.
 * Updated when server sends SyncDependencyStatusPacket.
 */
public class ClientDependencyCache {
    private static final Map<String, DependencyResult> CACHE = new ConcurrentHashMap<>();

    public static void update(String stageId, DependencyResult result) {
        CACHE.put(stageId, result);
    }

    public static DependencyResult get(String stageId) {
        return CACHE.get(stageId);
    }

    public static boolean isFulfilled(String stageId) {
        DependencyResult result = CACHE.get(stageId);
        return result == null || result.isFulfilled();
    }

    public static void clear() {
        CACHE.clear();
    }

    public static void remove(String stageId) {
        CACHE.remove(stageId);
    }
}
