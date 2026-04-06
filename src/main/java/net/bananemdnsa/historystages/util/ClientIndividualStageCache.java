package net.bananemdnsa.historystages.util;

import java.util.HashSet;
import java.util.Set;

public class ClientIndividualStageCache {
    private static Set<String> unlockedStages = new HashSet<>();

    public static void setUnlockedStages(Set<String> stages) {
        unlockedStages = new HashSet<>(stages);
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    public static void clear() {
        unlockedStages = new HashSet<>();
    }
}
