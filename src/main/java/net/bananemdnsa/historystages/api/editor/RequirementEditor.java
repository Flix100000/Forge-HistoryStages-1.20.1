package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.client.editor.dep.IdCountTab;

import net.bananemdnsa.historystages.api.editor.DependencyTab;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.api.editor.GenericIdPicker;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import org.jetbrains.annotations.Nullable;

/**
 * How an addon requirement gets a tab in the dependency editor.
 *
 * <p>Registering a requirement is enough to store and to gate; it is not enough to edit, because
 * HistoryStages cannot guess what a relic is nor which ones exist. That gap is exactly this
 * interface, and it is deliberately small — the addon says which ids exist and whether entries
 * carry an amount, and gets a tab that behaves like a built-in.
 *
 * <p><strong>The free tier has a fixed entry shape.</strong> {@link #ofIdList} and
 * {@link #ofIdCount} read and write {@link IdCountEntry}, so a requirement using either must
 * register with {@code RequirementStorage.gson(IdCountEntry.class)}. That covers every built-in
 * shape except scoreboard, with its comparison operator.
 *
 * <p><strong>Anything else implements {@link #createTab} directly</strong> and returns its own
 * {@link DependencyTab}, built on {@code AbstractDependencyTab} the way the built-ins are. A tab
 * supplies row text, a picker and load/store; the screen owns the drawing, so a tab that wants a
 * bespoke look opens its own screen from a row rather than painting inline.
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
     * Builds the tab.
     *
     * <p>Added rather than replacing the factories, so an addon that already registered through
     * {@code ofIdList} or {@code ofIdCount} needs no change: both produce an {@link IdCountTab}
     * from here.
     *
     * @param onChanged what the tab must call after changing anything, so the editor knows the
     *                  stage is dirty
     */
    DependencyTab createTab(Runnable onChanged);

    /**
     * Extra entries this requirement offers in a row right-click menu. Empty by default.
     *
     * <p>Appended after the built-in entries, so copy and remove stay where a maintainer expects
     * them and an addon adds to the menu rather than replacing it.
     */
    default java.util.List<net.bananemdnsa.historystages.api.editor.EntryAction> entryActions() {
        return java.util.List.of();
    }

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

            @Override
            public DependencyTab createTab(Runnable onChanged) {
                Requirement registered = RequirementTypes.byId(requirementId);
                if (registered == null) {
                    throw new IllegalStateException("No requirement registered under '"
                            + requirementId + "', so its editor has nothing to edit.");
                }
                return new IdCountTab(registered, amountLangKey,
                        (onSelect, alreadyAdded) -> {
                            GenericIdPicker picker = new GenericIdPicker(
                                    searchPlaceholderLangKey, candidates, onSelect, alreadyAdded);
                            // Multi-select only without an amount: with one, every pick opens the
                            // amount dialog, and a second pick behind an open dialog is lost.
                            picker.setMultiSelect(amountLangKey == null);
                            return picker;
                        },
                        onChanged);
            }
        };
    }
}
