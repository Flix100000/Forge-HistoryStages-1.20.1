package net.bananemdnsa.historystages.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientStageCache {
    private static volatile List<String> unlockedStages = new CopyOnWriteArrayList<>();

    public static void setUnlockedStages(List<String> stages) {
        unlockedStages = new CopyOnWriteArrayList<>(stages);
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }
}