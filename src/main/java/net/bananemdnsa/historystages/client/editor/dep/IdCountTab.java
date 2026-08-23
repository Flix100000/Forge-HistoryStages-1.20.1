package net.bananemdnsa.historystages.client.editor.dep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.IdCountEntry;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The free tier as a tab: entries that are an id and, optionally, an amount.
 *
 * <p>Both {@code RequirementEditor.ofIdList} and {@code ofIdCount} produce one of these, so there
 * is one storage shape behind both entry points rather than two that differ by a field. An addon
 * whose entries look different supplies its own {@link DependencyTab} instead.
 *
 * <p>The typed entries are the truth here and the display rows are derived from them. Keeping it
 * the other way round would mean parsing "3x thing" back apart, which breaks the moment an id
 * contains an x.
 */
public final class IdCountTab extends AbstractDependencyTab {

    private static final RequirementStorage<IdCountEntry> STORAGE =
            RequirementStorage.gson(IdCountEntry.class);

    private final String requirementId;
    private final String amountLangKey;
    private final List<IdCountEntry> items = new ArrayList<>();
    private Consumer<String> onAmountNeeded = id -> { };

    IdCountTab(Requirement requirement, @Nullable String amountLangKey,
               PickerFactory pickerFactory, Runnable onChanged) {
        super(requirement, pickerFactory, onChanged);
        this.requirementId = requirement.id();
        this.amountLangKey = amountLangKey;
    }

    /** Whether rows carry an amount, which is what separates the two free-tier shapes. */
    public boolean hasAmount() {
        return amountLangKey != null;
    }

    /** Lang key for the amount dialog's title, or null when rows are bare ids. */
    @Nullable
    public String amountLangKey() {
        return amountLangKey;
    }

    public String idAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).id() : "";
    }

    public int amountAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).count() : 1;
    }

    public void setAmountAt(int index, int amount) {
        if (index < 0 || index >= items.size()) return;
        items.set(index, new IdCountEntry(items.get(index).id(), amount));
        refreshRows();
        markChanged();
    }

    public void addEntry(String id, int amount) {
        items.add(new IdCountEntry(id, amount));
        refreshRows();
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= items.size()) return;
        items.add(index + 1, items.get(index));
        refreshRows();
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= items.size()) return;
        items.remove(index);
        refreshRows();
        markChanged();
    }

    /**
     * What the host does when a pick needs an amount before it becomes an entry.
     *
     * <p>The tab cannot open the dialog itself: dialogs are screens, and a tab has no screen to
     * push one onto. So it says what it needs and the host does it.
     */
    public void setOnAmountNeeded(Consumer<String> handler) {
        this.onAmountNeeded = handler;
    }

    @Override
    protected void onSelected(String id) {
        // Without an amount a pick is the whole entry. With one, the host opens the amount dialog
        // and calls addEntry when it is confirmed — adding here too would produce two rows.
        if (hasAmount()) onAmountNeeded.accept(id);
        else addEntry(id, 1);
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return items.stream().map(IdCountEntry::id).toList();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        items.clear();
        items.addAll(STORAGE.read(group.addonEntries(requirementId)));
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setAddonEntries(requirementId, items.isEmpty() ? null : STORAGE.write(items));
    }

    private void refreshRows() {
        rows().clear();
        for (IdCountEntry item : items) {
            rows().add(hasAmount() ? item.count() + "x " + item.id() : item.id());
        }
    }
}
