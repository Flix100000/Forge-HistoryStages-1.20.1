package net.bananemdnsa.historystages.client.editor.dep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import org.jetbrains.annotations.Nullable;

/**
 * The half of a dependency tab that is the same whatever the requirement stores: the row list, the
 * picker and its lifecycle, and the labels.
 *
 * <p>The twin of {@code AbstractCategoryTab}, deliberately down to the method order, so that what
 * is learned about one transfers to the other. Subclasses supply only {@link #readFrom} and
 * {@code store}, because that is the one thing that genuinely differs — a requirement of bare ids
 * reads straight through, while one with per-entry extras has to take its entries apart and put
 * them back together.
 *
 * <p>One thing this does <em>not</em> share with the category side: a stage has one container and
 * a stage has up to five dependency groups, so a tab here is bound to whichever group it was last
 * loaded from and writes every change straight back into it. See {@link #markChanged}.
 */
public abstract class AbstractDependencyTab implements DependencyTab {

    /**
     * Builds the picker, already configured. Configuration belongs to the factory rather than here
     * because it differs per requirement: an amount-carrying one wants single select, since every
     * pick opens the amount dialog.
     */
    @FunctionalInterface
    public interface PickerFactory {
        PickerOverlay create(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAdded);
    }

    private final Requirement requirement;
    private final List<String> edit;
    private final PickerFactory pickerFactory;
    private final Runnable onChanged;
    private PickerOverlay picker;
    private boolean rebuildPickerOnOpen;
    /** The group this tab was last loaded from; every change is written straight back into it. */
    private DependencyGroup boundGroup;

    protected AbstractDependencyTab(Requirement requirement,
                                    PickerFactory pickerFactory,
                                    Runnable onChanged) {
        this(requirement, pickerFactory, onChanged, new ArrayList<>());
    }

    protected AbstractDependencyTab(Requirement requirement,
                                    PickerFactory pickerFactory,
                                    Runnable onChanged,
                                    List<String> rows) {
        this.edit = rows;
        this.requirement = requirement;
        this.pickerFactory = pickerFactory;
        this.onChanged = onChanged;
    }

    /** For subclasses that keep their own typed entries and derive the display rows from them. */
    protected List<String> rows() {
        return edit;
    }

    /**
     * Binds the tab to a group and reads its entries out of it.
     *
     * <p>Final on purpose. Remembering the group is what lets every later change write itself
     * back, and a subclass that overrode this and forgot to bind would lose edits on the next
     * group switch — silently, with nothing able to test it.
     */
    @Override
    public final void load(DependencyGroup group) {
        this.boundGroup = group;
        readFrom(group);
    }

    /** Reads this tab's entries out of the group. Called by {@link #load}. */
    protected abstract void readFrom(DependencyGroup group);

    /**
     * Call after changing anything.
     *
     * <p>Writes straight back into the bound group as well as marking the stage dirty. Storing
     * only on a group switch would be enough in theory and a trap in practice: every future caller
     * that moves the selection would have to remember to store first, and the left-hand group list
     * would show a stale entry count until it did.
     */
    protected void markChanged() {
        if (boundGroup != null) store(boundGroup);
        onChanged.run();
    }

    @Override
    public String requirementId() {
        return requirement.id();
    }

    @Override
    public String tabLangKey() {
        return requirement.tabLangKey();
    }

    @Override
    public String tooltipLangKey() {
        return requirement.tooltipLangKey();
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
            onSelected(id);
            onChanged.run();
        }, this::alreadyAddedIds);
    }

    /**
     * The ids the picker should treat as already added.
     *
     * <p>Defaults to the display rows, which is correct only while a row <em>is</em> the id. A tab
     * whose rows read "3x thing" must override this, or the picker's hide-already-added toggle
     * compares a label against an id and matches nothing.
     */
    protected Collection<String> alreadyAddedIds() {
        return edit;
    }

    /**
     * What a pick means. The default appends the id as its own row, which is right for a tab whose
     * entries are bare ids; a tab that needs more — an amount, a dialog — overrides it.
     */
    protected void onSelected(String id) {
        if (!edit.contains(id)) edit.add(id);
    }

    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        return picker;
    }

    /**
     * Say that this picker has to be rebuilt every time it opens, because its contents depend on
     * state that changes while the editor is open.
     */
    public void setRebuildPickerOnOpen(boolean rebuild) {
        this.rebuildPickerOnOpen = rebuild;
    }

    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        if (rebuildPickerOnOpen || picker == null) rebuildPicker();
        picker.setFilter("");
        picker.show(centerX, centerY, parentWidth);
    }
}
