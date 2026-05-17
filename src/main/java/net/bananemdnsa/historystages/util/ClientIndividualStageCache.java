package net.bananemdnsa.historystages.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ClientIndividualStageCache {
    private static volatile Set<String> unlockedStages = Collections.emptySet();

    private ClientIndividualStageCache() {
    }

    public static void setUnlockedStages(Set<String> stages) {
        unlockedStages = Collections.unmodifiableSet(new HashSet<>(stages));
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    public static void clear() {
        unlockedStages = Collections.emptySet();
    }
}
