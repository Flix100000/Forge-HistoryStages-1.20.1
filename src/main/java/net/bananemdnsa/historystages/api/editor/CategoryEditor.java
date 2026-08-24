package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.GenericIdPicker;

import net.bananemdnsa.historystages.api.editor.StringListCategoryTab;

import net.bananemdnsa.historystages.api.editor.EntryAction;

import net.bananemdnsa.historystages.api.editor.CategoryTab;

import java.util.Collection;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.api.lock.LockCategory;

/**
 * How an addon category gets a tab in the stage editor.
 *
 * <p>Registering a category is enough to store and to gate; it is not enough to edit, because
 * HistoryStages cannot guess what a villager trade or a spell is, nor which ones exist. That gap
 * is exactly this interface, and it is deliberately small: for a category that gates things
 * identified by an id, {@link #ofIdList} closes it in one call and the addon writes no UI at all.
 *
 * <p>Anything richer — per-entry settings, a bespoke layout — implements {@link #createTab}
 * directly and builds on the same tab types the built-ins use.
 */
public interface CategoryEditor {

    /** The category this editor belongs to. Must name a registered category. */
    String categoryId();

    /**
     * Builds the tab.
     *
     * @param onChanged what the tab must call after changing anything, so the editor knows the
     *                  stage is dirty and can recompute its scroll extent
     */
    CategoryTab createTab(Runnable onChanged);

    /**
     * Extra entries this category offers in a row right-click menu. Empty by default.
     *
     * <p>Appended after the built-in entries, so copy and remove stay where a maintainer expects
     * them and an addon adds to the menu rather than replacing it.
     */
    default java.util.List<EntryAction> entryActions() {
        return java.util.List.of();
    }

    /**
     * The free tier: a searchable picker over the ids the addon offers, and a tab that behaves
     * like any built-in id list.
     *
     * <p>Only valid for a category whose entries are bare ids — one registered with
     * {@code CategoryStorage.gson(String.class)} or equivalent. A category storing a richer entry
     * type has to implement {@link #createTab} itself, because there is no sensible way to show
     * one of its entries as a single row.
     *
     * @param searchPlaceholderLangKey lang key for the picker's search hint
     * @param candidates               the ids a maintainer may pick from
     */
    static CategoryEditor ofIdList(String categoryId,
                                   String searchPlaceholderLangKey,
                                   Supplier<Collection<String>> candidates) {
        return new CategoryEditor() {
            @Override
            public String categoryId() {
                return categoryId;
            }

            @Override
            public CategoryTab createTab(Runnable onChanged) {
                LockCategory<?> registered = LockCategories.byId(categoryId);
                if (registered == null) {
                    throw new IllegalStateException("No lock category registered under '" + categoryId
                            + "', so its editor has nothing to edit.");
                }
                @SuppressWarnings("unchecked")
                LockCategory<String> idCategory = (LockCategory<String>) registered;
                return new StringListCategoryTab(idCategory,
                        (onSelect, alreadyAdded) -> {
                            GenericIdPicker picker = new GenericIdPicker(
                                    searchPlaceholderLangKey, candidates, onSelect, alreadyAdded);
                            picker.setMultiSelect(true);
                            return picker;
                        },
                        onChanged);
            }
        };
    }
}
