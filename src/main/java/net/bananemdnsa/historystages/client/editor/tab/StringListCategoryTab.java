package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;
import org.jetbrains.annotations.Nullable;

/**
 * A tab for a category whose entries are bare ids and nothing else.
 *
 * <p>"Plain" is doing real work in that sentence. Dimensions, structures, biomes and recipes each
 * store a list of strings; items, tags and mods do not — they carry per-entry NBT, lock actions
 * and name/tooltip overrides in lists kept index-aligned beside the ids, and the entity tabs
 * carry source and action filters. Those need a richer tab than this one, and trying to stretch
 * this class to cover them is the wrong move: give them their own implementation.
 */
public class StringListCategoryTab implements CategoryTab {

    /**
     * Builds the picker in whatever concrete searchable list the category needs, already
     * configured. Configuration belongs to the factory rather than here because it genuinely
     * differs per category: dimensions wants multi-select, recipes wants to stay open on select.
     */
    @FunctionalInterface
    public interface PickerFactory {
        PickerOverlay create(Consumer<String> onSelect,
                             Supplier<Collection<String>> alreadyAdded);
    }

    private final LockCategory<String> category;
    private final boolean availableForIndividualStages;
    private final List<String> edit = new ArrayList<>();
    private final PickerFactory pickerFactory;
    private final Runnable onChanged;
    private PickerOverlay picker;

    /**
     * @param onChanged what the editor wants to happen when an entry is added — marking the
     *                  screen dirty and recomputing its scroll extent, which the old per-tab
     *                  picker callbacks did inline
     */
    public StringListCategoryTab(LockCategory<String> category,
                                 boolean availableForIndividualStages,
                                 PickerFactory pickerFactory,
                                 Runnable onChanged) {
        this.category = category;
        this.availableForIndividualStages = availableForIndividualStages;
        this.pickerFactory = pickerFactory;
        this.onChanged = onChanged;
    }

    @Override
    public void rebuildPicker() {
        picker = pickerFactory.create(id -> {
            if (!edit.contains(id)) edit.add(id);
            onChanged.run();
        }, () -> edit);
    }

    @Override
    public String categoryId() {
        return category.id();
    }

    @Override
    public String tabLangKey() {
        return category.tabLangKey();
    }

    @Override
    public String tooltipLangKey() {
        return category.tooltipLangKey();
    }

    @Override
    public boolean availableForIndividualStages() {
        return availableForIndividualStages;
    }

    @Override
    public void load(StageEntry stage) {
        edit.clear();
        edit.addAll(category.read(stage));
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(edit));
    }

    @Override
    public List<String> entries() {
        return edit;
    }

    @Override
    public void removeAt(int index) {
        if (index >= 0 && index < edit.size()) edit.remove(index);
    }

    @Override
    @Nullable
    public PickerOverlay picker() {
        return picker;
    }

    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        if (picker == null) return;
        picker.setFilter("");
        picker.show(centerX, centerY, parentWidth);
    }
}
