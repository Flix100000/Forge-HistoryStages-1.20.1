package net.bananemdnsa.historystages.api.lock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The action vocabularies a lock entry can narrow itself to.
 *
 * <p>An entry with no action list gates everything its category offers; one with a list gates
 * only what the list names. On disk the <em>complement</em> is stored — see
 * {@code ItemEntryListAdapter} — which makes these lists a compatibility surface rather than a
 * label. Reordering is harmless, renaming is not, and dropping an entry rewrites every stage
 * file that narrowed itself to it.
 *
 * <p>Two vocabularies rather than one, because a category cannot honour what its subject cannot
 * do: a fluid is never worn, never swung and never mined, so offering it {@code equip} would be
 * a checkbox that does nothing. Which vocabulary applies is declared per category by
 * {@link LockCategory#lockActions()}.
 */
public final class LockActions {

    private LockActions() {}

    /** The ten actions items, tags and mods have always offered, in editor order. */
    public static final List<String> ITEM = List.of(
            "equip", "attack", "place", "break", "pickup", "use", "loot", "recipe", "gui", "icon"
    );

    /**
     * The seven a fluid can answer for.
     *
     * <p>{@code ingredient} — gate the recipes that <em>consume</em> this fluid, as opposed to
     * {@code recipe}, which gates the ones producing it — exists only here.
     */
    public static final List<String> FLUID = List.of(
            "use", "place", "pickup", "recipe", "ingredient", "loot", "icon"
    );

    /**
     * Every action any category recognises.
     *
     * <p>Read by the loader to spot a typo in a hand-edited stage file, and therefore the union:
     * validating an item entry against the fluid list, or the other way round, would report a
     * perfectly good file as broken.
     */
    public static final Set<String> KNOWN = knownActions();

    private static Set<String> knownActions() {
        Set<String> all = new LinkedHashSet<>(ITEM);
        all.addAll(FLUID);
        return Set.copyOf(all);
    }
}
