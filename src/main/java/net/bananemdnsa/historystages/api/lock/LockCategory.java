package net.bananemdnsa.historystages.api.lock;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageScope;

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

    /**
     * Which stage scopes this category means anything in.
     *
     * <p>A fact about the data, not about the editor: recipes and spawn locks are global-only
     * because there is no per-player recipe or spawn gate to write to, and that is equally true
     * for a lock check as it is for a tab. Both scopes unless a category says otherwise.
     */
    default java.util.Set<StageScope> supportedScopes() {
        return java.util.EnumSet.allOf(StageScope.class);
    }

    /** Lang key for the editor tab tooltip. */
    String tooltipLangKey();

    /** This category's entries on the given stage. Never null; empty when unset. */
    List<T> read(StageEntry stage);

    /** Replaces this category's entries on the given stage. */
    void write(StageEntry stage, List<T> entries);

    /**
     * The entry ids this category contributes when scanning <em>global</em> stages for
     * dual-phase overlaps — the check that spots the same thing being gated by a global stage
     * and an individual one at once.
     *
     * <p>Returning an empty list opts the category out, which is the right answer where an
     * overlap is meaningless (mod exceptions) or was never tracked (recipes, spawn locks).
     */
    default List<String> globalDualPhaseIds(StageEntry stage) {
        return List.of();
    }

    /**
     * The same, for <em>individual</em> stages.
     *
     * <p>Separate from {@link #globalDualPhaseIds} because the two sides are not always
     * symmetric: a spawn lock that blocks every source implies an attack lock globally, but the
     * individual side has never counted it. Categories where both sides agree return the same
     * list from both methods.
     */
    default List<String> individualDualPhaseIds(StageEntry stage) {
        return List.of();
    }

    /**
     * How this category is named in the "dual-phase lock registered" loading message, e.g.
     * {@code "item"}. These strings are shown to the maintainer on load, so the wording is a
     * compatibility surface, not a label to tidy up.
     */
    default String dualPhaseLabel() {
        return "";
    }

    /**
     * Whether this entry gates the given subject.
     *
     * <p>Defaults to "no", which is right for the built-ins: they are queried through their own
     * typed paths on the lock engine, not through this generic one. Addon categories override it
     * with the matcher they registered.
     */
    default boolean matches(T entry, Object subject) {
        return false;
    }

    /**
     * Whether this stage gates the given subject through this category.
     *
     * <p>The default is the obvious loop over this category's own entries, and that is the whole
     * answer for nearly every category — including every addon one. Override it only when the
     * answer depends on something the entries themselves cannot see: a mod lock is vetoed by the
     * exception list on the <em>stage</em>, and an attack lock can be implied by a spawn lock in
     * a <em>different</em> category on the same stage.
     *
     * <p>An addon never needs this. It supplies {@link #matches} and gets the loop for free.
     */
    default boolean gates(StageEntry stage, Object subject) {
        for (T entry : read(stage)) {
            if (matches(entry, subject)) return true;
        }
        return false;
    }
}
