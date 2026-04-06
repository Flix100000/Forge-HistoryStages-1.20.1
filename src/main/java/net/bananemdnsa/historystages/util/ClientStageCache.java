package net.bananemdnsa.historystages.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientStageCache {
    private static List<String> unlockedStages = new ArrayList<>();

    public static void setUnlockedStages(List<String> stages) {
        unlockedStages = new ArrayList<>(stages);
    }

    // Diese Methode wird jetzt vom Screen aufgerufen
    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    public static Set<String> snapshot() {
        return new HashSet<>(unlockedStages);
    }

}
