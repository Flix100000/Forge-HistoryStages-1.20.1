package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;

import java.util.ArrayList;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.lock.LockCategory;

/**
 * A tab for a category whose entries are bare ids and nothing else.
 *
 * <p>"Plain" is doing real work in that sentence. Dimensions, structures, biomes and recipes each
 * store a list of strings; items, tags and mods do not — they carry per-entry NBT, lock actions
 * and name and tooltip overrides, which is what {@link RichEntryCategoryTab} exists for.
 */
public class StringListCategoryTab extends AbstractCategoryTab {

    private final LockCategory<String> category;

    /**
     * @param onChanged what the editor wants to happen when an entry is added — marking the
     *                  screen dirty and recomputing its scroll extent, which the old per-tab
     *                  picker callbacks did inline
     */
    public StringListCategoryTab(LockCategory<String> category,
                                 PickerFactory pickerFactory,
                                 Runnable onChanged) {
        super(category, pickerFactory, onChanged);
        this.category = category;
    }

    @Override
    public void load(StageEntry stage) {
        entries().clear();
        entries().addAll(category.read(stage));
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(entries()));
    }
}
