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
     * <p>A fact about the data, not about the editor: spawn locks are global-only because there
     * is no per-player spawn gate to write to, and that is equally true for a lock check as it is
     * for a tab. Both scopes unless a category says otherwise.
     */
    default java.util.Set<StageScope> supportedScopes() {
        return java.util.EnumSet.allOf(StageScope.class);
    }

    /**
     * The action vocabulary an entry of this category may narrow itself to.
     *
     * <p>A property of the subject, not of the editor: a fluid is never worn and never mined, so
     * offering it {@code equip} or {@code break} would be a checkbox nothing could honour. The
     * editor reads the list from here rather than knowing it, which is also what lets an addon
     * declare actions of its own.
     *
     * <p>Defaults to {@link LockActions#ITEM} — the ten the mod has always offered — so a
     * category written before this method existed keeps behaving exactly as it did.
     */
    default List<String> lockActions() {
        return LockActions.ITEM;
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

    /**
     * The ids under which this stage should be filed in the reverse index, so a lock check can
     * skip the stages that cannot possibly match instead of asking all of them.
     *
     * <p>Optional. A category that returns nothing is simply never indexed and stays on the full
     * scan, which is correct — only slower. Implementing it is worth it once a pack runs a few
     * hundred stages: the scan is linear in stage count and costs about four microseconds at
     * three hundred, against roughly fifty nanoseconds through the index.
     *
     * <p><strong>It must over-approximate, never under-approximate.</strong> List every id that
     * {@link #gates} could possibly answer "yes" to on this stage, including ones whose real
     * answer depends on something else — an NBT criterion, a spawn source, a held item. The exact
     * check still runs afterwards on the candidates, so a key too many costs a comparison. A key
     * too few means the stage is never asked, and the thing it gates is silently unlocked. Note
     * what that implies for a category whose {@code gates} reads a neighbouring category on the
     * same stage: the ids from that neighbour belong here too.
     */
    default List<String> indexKeys(StageEntry stage) {
        return List.of();
    }

    /**
     * The id to look this subject up under in that index, or null to skip the index and scan.
     *
     * <p>The counterpart to {@link #indexKeys}: whatever that method files a stage under, this
     * one has to produce from the runtime object. A category that implements one without the
     * other gains nothing.
     */
    default String lookupKey(Object subject) {
        return null;
    }
}
