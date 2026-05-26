package net.bananemdnsa.historystages.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientStageCache {
    private static volatile Set<String> unlockedStages = Collections.emptySet();

    private ClientStageCache() {
    }

    public static void setUnlockedStages(List<String> stages) {
        unlockedStages = Collections.unmodifiableSet(new HashSet<>(stages));
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    public static void clear() {
        unlockedStages = Collections.emptySet();
    }
}
