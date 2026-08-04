package net.bananemdnsa.historystages.client.cache;

import java.util.HashSet;
import java.util.Set;

public class ClientIndividualStageCache {
    private static Set<String> unlockedStages = new HashSet<>();

    /** See {@link ClientStageCache#version()} — same purpose, for the individual set. */
    private static int version;

    public static void setUnlockedStages(Set<String> stages) {
        unlockedStages = new HashSet<>(stages);
        version++;
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    public static void clear() {
        unlockedStages = new HashSet<>();
        version++;
    }

    /** Changes whenever the unlocked set is replaced; compare against a previously read value. */
    public static int version() {
        return version;
    }
}
