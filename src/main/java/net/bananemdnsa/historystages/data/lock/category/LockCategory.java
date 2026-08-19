package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;

/**
 * One kind of thing a stage can gate — items, tags, mods, dimensions, and so on.
 *
 * <p>A category is a <em>view</em> over a {@link StageEntry}, not a store. The entries still
 * live in the same typed fields they always have, and the on-disk format is unchanged; the
 * category just makes them reachable without naming the field. That is what lets code stop
 * repeating itself eleven times, and what a later phase turns into a public registration point
 * for addons.
 *
 * <p>The unit is the <em>editor tab</em>, not the JSON key: attack, spawn and interaction locks
 * are three categories even though all three are stored inside one {@code entities} object.
 *
 * @param <T> the entry type this category stores — {@code String} for plain id lists,
 *            {@code ItemEntry} where NBT and lock actions are possible, and so on.
 */
public interface LockCategory<T> {

    /** Namespaced, stable, and used as a map key — never a display string. */
    String id();

    /** Lang key for the editor tab label. Built-ins reuse the keys the editor already ships. */
    String tabLangKey();

    /** Lang key for the editor tab tooltip. */
    String tooltipLangKey();

    /** This category's entries on the given stage. Never null; empty when unset. */
    List<T> read(StageEntry stage);

    /** Replaces this category's entries on the given stage. */
    void write(StageEntry stage, List<T> entries);

    /**
     * The entry ids this category contributes to dual-phase overlap detection — the check that
     * spots the same thing being gated by a global stage <em>and</em> an individual one.
     *
     * <p>Returning an empty list opts the category out, which is the right answer for
     * categories where an overlap is meaningless (mod exceptions) or was never tracked
     * (recipes, spawn locks). Some categories contribute ids that are not simply
     * {@link #read}'s entry ids — see the attack-lock implementation.
     */
    default List<String> dualPhaseIds(StageEntry stage) {
        return List.of();
    }
}
