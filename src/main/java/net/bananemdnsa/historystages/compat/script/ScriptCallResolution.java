package net.bananemdnsa.historystages.compat.script;

import net.bananemdnsa.historystages.api.stage.StageScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything a script call decides before Minecraft gets involved: is this stage id known, is it
 * known in the scope the caller asked for, is this category id real, and has this exact mistake
 * already been logged.
 *
 * <p>Minecraft-free on purpose. The test source set has no Minecraft on its classpath, so a class
 * that touches a game type cannot be unit tested at all — and these are the branches worth
 * testing. Same split {@code CategoryLockResolver} has against {@code CategoryLocks}.
 */
public final class ScriptCallResolution {

    private ScriptCallResolution() {}

    /** Outcome of one check: ok, or not ok with the sentence to log. */
    public record Check(boolean ok, String message) {

        // Named pass()/fail() rather than ok()/fail(): a static ok() would clash with the record
        // accessor of the same name, and javac rejects that outright.
        static Check pass() {
            return new Check(true, null);
        }

        static Check fail(String message) {
            return new Check(false, message);
        }
    }

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Resolves a stage id against the scope the caller used. A stage that exists in the other
     * scope gets its own message, because "unknown stage" would send the author looking for a
     * typo that is not there.
     */
    public static Check stage(String stageId, StageScope wanted,
                              Set<String> globalIds, Set<String> individualIds) {
        if (stageId == null || stageId.isBlank()) {
            return Check.fail("HistoryStages: a script passed an empty stage id");
        }

        Set<String> wantedIds = wanted == StageScope.GLOBAL ? globalIds : individualIds;
        if (wantedIds.contains(stageId)) return Check.pass();

        Set<String> otherIds = wanted == StageScope.GLOBAL ? individualIds : globalIds;
        if (otherIds.contains(stageId)) {
            String other = wanted == StageScope.GLOBAL ? "individual" : "global";
            String use = wanted == StageScope.GLOBAL
                    ? "unlockFor/lockFor/isUnlockedFor"
                    : "unlock/lock/isUnlocked";
            return Check.fail("HistoryStages: stage '" + stageId + "' is an " + other
                    + " stage, but a script asked for it in the " + wanted.name().toLowerCase()
                    + " scope — use " + use + " instead");
        }

        return Check.fail("HistoryStages: a script used unknown stage id '" + stageId
                + "'. Known " + wanted.name().toLowerCase() + " stages: " + sorted(wantedIds));
    }

    /** The namespace the built-in lock categories are registered under. */
    private static final String OWN_NAMESPACE = "historystages:";

    /** Resolves a lock-category id, e.g. {@code items} or an addon's own. */
    public static Check category(String categoryId, Collection<String> knownIds) {
        if (categoryId == null || categoryId.isBlank()) {
            return Check.fail("HistoryStages: a script passed an empty category id");
        }
        if (knownIds.contains(canonicalCategoryId(categoryId, knownIds))) return Check.pass();
        return Check.fail("HistoryStages: a script used unknown lock category '" + categoryId
                + "'. Known categories: " + sorted(knownIds));
    }

    /**
     * Turns what a script wrote into the id the registry is keyed by.
     *
     * <p>Category ids carry a namespace — {@code historystages:items}, {@code hsdemo:relics} —
     * and making a pack author type that for the eleven built-ins would be noise, so a bare name
     * falls back to this mod's namespace the way a bare item id falls back to {@code minecraft:}.
     * An addon category still has to be named in full: resolving a bare {@code relics} into
     * somebody else's namespace would be guessing, and it would break the day two addons pick the
     * same short name.
     *
     * <p>Returns the input unchanged when nothing matches, so the failure message quotes what the
     * author actually typed rather than a guess at what they meant.
     */
    public static String canonicalCategoryId(String categoryId, Collection<String> knownIds) {
        if (categoryId == null || categoryId.isBlank()) return categoryId;
        if (knownIds.contains(categoryId)) return categoryId;
        if (categoryId.indexOf(':') < 0) {
            String namespaced = OWN_NAMESPACE + categoryId;
            if (knownIds.contains(namespaced)) return namespaced;
        }
        return categoryId;
    }

    /**
     * True the first time it sees a key, false afterwards. A script calling a query inside a tick
     * handler would otherwise write the same warning to the log sixty times a second.
     */
    public static boolean shouldWarn(String key) {
        return WARNED.add(key);
    }

    /** Called on script reload so a corrected script is allowed to complain again. */
    public static void resetWarnings() {
        WARNED.clear();
    }

    private static String sorted(Collection<String> ids) {
        List<String> out = new ArrayList<>(ids);
        out.sort(String::compareTo);
        return String.join(", ", out);
    }
}
