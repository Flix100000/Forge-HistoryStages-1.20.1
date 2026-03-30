package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for editor data received from the server.
 */
public class EditorDataCache {
    private static Map<String, StageEntry> stages = new HashMap<>();

    public static void setStages(Map<String, StageEntry> stages) {
        EditorDataCache.stages = stages != null ? stages : new HashMap<>();
    }

    public static Map<String, StageEntry> getStages() {
        return stages;
    }

    public static void clear() {
        stages.clear();
    }
}
