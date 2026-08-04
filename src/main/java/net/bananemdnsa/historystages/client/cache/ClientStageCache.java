package net.bananemdnsa.historystages.client.cache;

import java.util.ArrayList;
import java.util.List;

public class ClientStageCache {
    private static List<String> unlockedStages = new ArrayList<>();

    /**
     * Bumped on every replacement. Screens that derive state from this cache — the stage graph
     * builds a whole node/edge model from it — have no other way to notice an unlock arriving
     * mid-view, and would otherwise keep drawing a stale picture until reopened.
     */
    private static int version;

    public static void setUnlockedStages(List<String> stages) {
        unlockedStages = stages;
        version++;
    }

    /** Changes whenever the unlocked set is replaced; compare against a previously read value. */
    public static int version() {
        return version;
    }

    // Diese Methode wird jetzt vom Screen aufgerufen
    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }


}