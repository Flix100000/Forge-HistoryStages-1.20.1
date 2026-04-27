package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for editor data received from the server.
 */
public class EditorDataCache {
    private static Map<String, StageDefinition> stages = new HashMap<>();

    public static void setStages(Map<String, StageDefinition> stages) {
        EditorDataCache.stages = stages != null ? stages : new HashMap<>();
    }

    public static Map<String, StageDefinition> getStages() {
        return stages;
    }

    public static void clear() {
        stages.clear();
    }
}
