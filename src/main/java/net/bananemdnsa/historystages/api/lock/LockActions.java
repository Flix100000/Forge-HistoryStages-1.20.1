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
 * <p>Two vocabularies rather than one, because a category cannot honour what its subject
 * cannot do: a fluid is never worn, never swung and never mined, so offering it {@code equip}
 * would be a checkbox that does nothing. Which vocabulary applies is declared per category by
 * {@link LockCategory#lockActions()}.
 */
public final class LockActions {

    private LockActions() {}

    /**
     * The eleven actions items, tags and mods offer, in editor order.
     *
     * <p>{@code trade} sits beside {@code pickup} because both are ways of acquiring something,
     * and the actions popup groups by neighbour.
     *
     * <p><strong>Adding to this list is a behaviour change for files that already exist.</strong>
     * The complement is what gets stored, so an action no old file mentions counts as locked in
     * every one of them: an entry someone narrowed to "use only" gates trading as well from the
     * update onwards. When {@code trade} was added that was the intended effect, and it must not
     * be migrated away — a format marker that read a missing action as unlocked would be a
     * permanent special case bought for a one-off transition, and the next action would need it
     * again. It belongs in the changelog instead.
     */
    public static final List<String> ITEM = List.of(
            "equip", "attack", "place", "break", "pickup", "trade",
            "use", "loot", "recipe", "gui", "icon"
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
