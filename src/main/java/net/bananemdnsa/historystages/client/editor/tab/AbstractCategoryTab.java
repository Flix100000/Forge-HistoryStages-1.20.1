package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;
import org.jetbrains.annotations.Nullable;

/**
 * The half of a tab that is the same whatever the category stores: the row list, the picker and
 * its lifecycle, and the labels.
 *
 * <p>Subclasses supply only {@code load} and {@code store}, because that is the one thing that
 * genuinely differs — a category of bare ids reads straight through its {@code LockCategory},
 * while one with per-entry extras has to take its entries apart and put them back together.
 */
public abstract class AbstractCategoryTab implements CategoryTab {

    /**
     * Builds the picker, already configured. Configuration belongs to the factory rather than
     * here because it differs per category: dimensions wants multi-select, recipes wants to stay
     * open on select.
     */
    @FunctionalInterface
    public interface PickerFactory {
        PickerOverlay create(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAdded);
    }

    private final LockCategory<?> category;
    private final boolean availableForIndividualStages;
    private final List<String> edit = new ArrayList<>();
    private final PickerFactory pickerFactory;
    private final Runnable onChanged;
    private PickerOverlay picker;

    protected AbstractCategoryTab(LockCategory<?> category,
                                  boolean availableForIndividualStages,
                                  PickerFactory pickerFactory,
                                  Runnable onChanged) {
        this.category = category;
        this.availableForIndividualStages = availableForIndividualStages;
        this.pickerFactory = pickerFactory;
        this.onChanged = onChanged;
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
    public List<String> entries() {
        return edit;
    }

    @Override
    public void removeAt(int index) {
        if (index >= 0 && index < edit.size()) edit.remove(index);
    }

    @Override
    public void rebuildPicker() {
        picker = pickerFactory.create(id -> {
            if (!edit.contains(id)) edit.add(id);
            onChanged.run();
        }, () -> edit);
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
