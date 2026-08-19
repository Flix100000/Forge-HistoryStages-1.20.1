package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Set;

/**
 * The "what is already unlocked" half of a lock question, for exactly one viewer:
 * the server's global set, one player's individual set, or the corresponding client caches.
 *
 * <p>Deliberately free of Minecraft types so the resolution logic stays unit-testable, and
 * so Phase 10 can back this with a bitmask lookup without touching a single caller.
 */
@FunctionalInterface
public interface StageStateView {

    boolean isUnlocked(String stageId);

    /** A viewer with nothing unlocked. Useful as a fallback and in tests. */
    StageStateView NONE_UNLOCKED = stageId -> false;

    /**
     * Wraps a live set. The set is not copied — callers pass the running caches on purpose so
     * the view always reflects current state.
     */
    static StageStateView of(Set<String> unlocked) {
        Set<String> source = unlocked == null ? Set.of() : unlocked;
        return source::contains;
    }
}
