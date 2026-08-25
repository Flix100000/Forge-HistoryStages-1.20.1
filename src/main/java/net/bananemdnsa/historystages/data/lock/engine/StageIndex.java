package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stable number for every stage, so a set of stages can be a set of bits.
 *
 * <p>The numbering is derived, never stored. Both sides work it out from the stage ids they hold,
 * using the order the parent design fixed in §9: global stages first, then individual ones, and
 * within each group by name, case-insensitively. The same ids in any order produce the same
 * numbering, which is what lets a server and a client compare bits at all.
 *
 * <p><strong>A number must never be written to disk or put on the wire.</strong> Stage files and
 * sync packets carry ids, as they always have. That is not caution for its own sake: rename one
 * stage and every number after it shifts, so a stored number would quietly come back meaning a
 * different stage. Deriving it costs one sort per stage change and removes the whole class of
 * failure.
 *
 * <p>Sorting is also not optional. The stage maps are {@link java.util.concurrent.ConcurrentHashMap}s
 * and their iteration order is neither stable across runs nor equal between two machines holding
 * the same stages.
 */
public final class StageIndex {

    /** No stages at all — every lookup misses. */
    public static final StageIndex EMPTY = new StageIndex(Map.of(), new String[0]);

    private final Map<String, Integer> byId;
    private final String[] byNumber;

    private StageIndex(Map<String, Integer> byId, String[] byNumber) {
        this.byId = byId;
        this.byNumber = byNumber;
    }

    /**
     * Numbers the given stages. Globals come first so that adding an individual stage cannot
     * renumber the global ones — the common editing motion in a pack, and the one where a stale
     * mask would be most likely to survive unnoticed.
     */
    public static StageIndex of(Collection<String> globalIds, Collection<String> individualIds) {
        List<String> ordered = new ArrayList<>(globalIds.size() + individualIds.size());
        List<String> globals = new ArrayList<>(globalIds);
        List<String> individuals = new ArrayList<>(individualIds);
        globals.sort(Comparator.comparing(id -> id.toLowerCase(java.util.Locale.ROOT)));
        individuals.sort(Comparator.comparing(id -> id.toLowerCase(java.util.Locale.ROOT)));
        ordered.addAll(globals);
        ordered.addAll(individuals);

        Map<String, Integer> byId = new HashMap<>(ordered.size() * 2);
        for (int i = 0; i < ordered.size(); i++) {
            byId.put(ordered.get(i), i);
        }
        return new StageIndex(Map.copyOf(byId), ordered.toArray(new String[0]));
    }

    /** This stage's number, or -1 when the index has never heard of it. */
    public int numberOf(String stageId) {
        Integer number = byId.get(stageId);
        return number == null ? -1 : number;
    }

    /** The stage a number belongs to, or null when it is out of range. */
    public String stageAt(int number) {
        return number < 0 || number >= byNumber.length ? null : byNumber[number];
    }

    /** How many stages are numbered — the width a mask needs to cover. */
    public int size() {
        return byNumber.length;
    }
}
