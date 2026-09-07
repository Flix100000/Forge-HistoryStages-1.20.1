package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

/**
 * One gated profession, optionally narrowed to some of the merchant's five levels.
 *
 * <p>Without the narrowing this would be a bare id, and it was one until a pack author asked the
 * obvious question: "librarians, but only once they are past apprentice." Profession and level
 * are two separate gates that each take a merchant out on their own — a stage gating level 4 hides
 * every trade at level 4, farmer and fletcher alike — so "this profession from this level up"
 * could not be said at all.
 *
 * <p><strong>The levels here are the gated ones, not the free ones.</strong> That is the opposite
 * of the convention {@link ItemEntry} follows with {@code unlock_actions},
 * and it is deliberate: the {@code levels} list in the same {@code trades} block already means
 * "these levels are gated", and one word meaning two opposite things inside one object is a trap
 * for whoever edits the file by hand. The complement exists to keep old files honest when a new
 * action is added later, and a merchant will never grow a sixth level.
 *
 * <p>No list at all means every level, which is what a bare profession id in a stage file means
 * and what it has always meant.
 */
public class TradeProfessionEntry {

    /** Novice through master. A merchant cannot reach a sixth. */
    public static final List<String> ALL_LEVELS = List.of("1", "2", "3", "4", "5");

    private final String id;

    /** null or empty = every level is gated. Otherwise exactly the levels named here. */
    private final List<String> levels;

    public TradeProfessionEntry(String id) {
        this(id, null);
    }

    public TradeProfessionEntry(String id, List<String> levels) {
        this.id = id;
        this.levels = (levels != null && !levels.isEmpty()) ? new ArrayList<>(levels) : null;
    }

    public String getId() {
        return id;
    }

    /** Returns null when every level is gated, otherwise the explicit list. */
    public List<String> getLevels() {
        return levels;
    }

    /** Whether this entry names levels at all. */
    public boolean hasLevels() {
        return levels != null && !levels.isEmpty();
    }

    /**
     * Whether this entry gates the named profession at the given level.
     *
     * <p>The decision a reader of a stage file is reasoning about — "does my level 4-and-5 entry
     * catch this apprentice?" — and it needs no Minecraft, so a plain unit test can prove the
     * whole table.
     *
     * @param level the merchant's own level, 1 to 5. Anything a merchant reports outside that
     *              range still matches an entry that names no levels, because such an entry means
     *              "this profession, always".
     */
    public boolean gates(String professionId, int level) {
        if (!this.id.equals(professionId)) return false;
        if (levels == null) return true;
        return levels.contains(String.valueOf(level));
    }

    public TradeProfessionEntry copy() {
        return new TradeProfessionEntry(id, levels != null ? new ArrayList<>(levels) : null);
    }
}
