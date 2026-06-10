package net.bananemdnsa.historystages.client.cache;

import java.util.ArrayList;
import java.util.List;

public class ClientStageCache {
    private static List<String> unlockedStages = new ArrayList<>();

    public static void setUnlockedStages(List<String> stages) {
        unlockedStages = stages;
    }

    // Diese Methode wird jetzt vom Screen aufgerufen
    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }


}