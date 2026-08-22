package net.bananemdnsa.historystages.client.editor.dep;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.data.dependency.IdCountEntry;
import org.jetbrains.annotations.Nullable;

/**
 * How an addon requirement gets a tab in the dependency editor.
 *
 * <p>Registering a requirement is enough to store and to gate; it is not enough to edit, because
 * HistoryStages cannot guess what a relic is nor which ones exist. That gap is exactly this
 * interface, and it is deliberately small — the addon says which ids exist and whether entries
 * carry an amount, and gets a tab that behaves like a built-in.
 *
 * <p><strong>The free tier has a fixed entry shape.</strong> It reads and writes
 * {@link IdCountEntry}, so a requirement using it must register with
 * {@code RequirementStorage.gson(IdCountEntry.class)}. That covers seven of the eight built-in
 * shapes — everything except scoreboard, with its comparison operator. A requirement whose
 * entries look different can still register and gate; it just has no tab yet. Giving it one means
 * a bespoke-tab seam the editor does not expose today, which is a design question of its own.
 */
public interface RequirementEditor {

    /** The requirement this editor belongs to. Must name a registered requirement. */
    String requirementId();

    /** Lang key for the picker's search hint. */
    String searchPlaceholderLangKey();

    /**
     * Lang key for the amount dialog's title, or null when entries are bare ids.
     *
     * <p>Null is what separates the two free-tier shapes: with an amount, a row reads "3x relic"
     * and clicking it reopens the dialog; without, a row is just the id.
     */
    @Nullable
    String amountLangKey();

    /** The ids a maintainer may pick from. Queried fresh each time the picker opens. */
    Collection<String> candidates();

    /**
     * Bare id rows — the shape of the stage requirements.
     *
     * <p>Entries are still stored as {@link IdCountEntry}, with a count of 1. That keeps one
     * storage shape behind both entry points rather than two that differ by a field.
     */
    static RequirementEditor ofIdList(String requirementId,
                                      String searchPlaceholderLangKey,
                                      Supplier<Collection<String>> candidates) {
        return of(requirementId, searchPlaceholderLangKey, null, candidates);
    }

    /**
     * Id plus an amount — the shape of items, kills and stats, and the commonest thing a
     * requirement expresses.
     *
     * @param amountLangKey title for the amount dialog, opened on add and on clicking a row
     */
    static RequirementEditor ofIdCount(String requirementId,
                                       String searchPlaceholderLangKey,
                                       String amountLangKey,
                                       Supplier<Collection<String>> candidates) {
        return of(requirementId, searchPlaceholderLangKey,
                Objects.requireNonNull(amountLangKey, "amountLangKey"), candidates);
    }

    private static RequirementEditor of(String requirementId, String searchPlaceholderLangKey,
                                        @Nullable String amountLangKey,
                                        Supplier<Collection<String>> candidates) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(searchPlaceholderLangKey, "searchPlaceholderLangKey");
        Objects.requireNonNull(candidates, "candidates");
        return new RequirementEditor() {
            @Override
            public String requirementId() {
                return requirementId;
            }

            @Override
            public String searchPlaceholderLangKey() {
                return searchPlaceholderLangKey;
            }

            @Override
            public String amountLangKey() {
                return amountLangKey;
            }

            @Override
            public Collection<String> candidates() {
                return candidates.get();
            }
        };
    }
}
