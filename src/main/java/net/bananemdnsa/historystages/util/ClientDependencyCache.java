package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.data.dependency.DependencyResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDependencyCache {
    private static final Map<String, DependencyResult> CACHE = new ConcurrentHashMap<>();

    private ClientDependencyCache() {
    }

    public static DependencyResult get(String stageId) {
        return CACHE.get(stageId);
    }

    public static void put(String stageId, DependencyResult result) {
        if (stageId != null && result != null) {
            CACHE.put(stageId, result);
        }
    }

    public static void remove(String stageId) {
        if (stageId != null) {
            CACHE.remove(stageId);
        }
    }

    public static void clear() {
        CACHE.clear();
    }
}
