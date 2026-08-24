package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.Requirement;

/** The stat requirement: an id and a minimum, shown as {@code id >= n}. */
public final class StatTab extends AbstractDependencyTab {

    private final List<StatDep> stats = new ArrayList<>();

    public StatTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
        super(requirement, pickerFactory, onChanged);
    }

    public String idAt(int index) {
        return index >= 0 && index < stats.size() ? stats.get(index).getStatId() : "";
    }

    public int minimumAt(int index) {
        return index >= 0 && index < stats.size() ? stats.get(index).getMinValue() : 1;
    }

    public void setMinimumAt(int index, int minimum) {
        if (index < 0 || index >= stats.size()) return;
        stats.get(index).setMinValue(minimum);
        refreshRows();
        markChanged();
    }

    public void addStat(String id, int minimum) {
        stats.add(new StatDep(id, minimum));
        refreshRows();
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= stats.size()) return;
        stats.add(index + 1, stats.get(index).copy());
        refreshRows();
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= stats.size()) return;
        stats.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    protected void onSelected(String id) {
        // A pick lands at 1 and the minimum is changed from the row's menu afterwards. Opening a
        // dialog per pick would break the picker's multi-select, which this requirement uses.
        addStat(id, 1);
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return stats.stream().map(StatDep::getStatId).toList();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        stats.clear();
        // Copies, not the group's own objects: store() writes the whole list back, so sharing
        // instances would make an unstored edit visible in the group anyway.
        for (StatDep stat : group.getStats()) stats.add(stat.copy());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setStats(new ArrayList<>(stats));
    }

    private void refreshRows() {
        rows().clear();
        for (StatDep stat : stats) rows().add(stat.getStatId() + " >= " + stat.getMinValue());
    }
}
