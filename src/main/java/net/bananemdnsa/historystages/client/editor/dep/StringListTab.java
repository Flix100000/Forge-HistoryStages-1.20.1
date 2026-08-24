package net.bananemdnsa.historystages.client.editor.dep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.Requirement;

/**
 * The two requirements that are a bare list of ids on the group: global stages and advancements.
 *
 * <p>One class rather than two, because they differ in exactly two things — which accessor they
 * read and whether a row resolves a display name — and both are constructor arguments.
 *
 * <p>The ids are the truth and the rows are derived from them. Reading them back out of the row
 * text would work for advancements, whose row <em>is</em> the id, and break for stages, whose row
 * is "name (id)".
 */
public final class StringListTab extends AbstractDependencyTab {

    private final Function<DependencyGroup, List<String>> accessor;
    private final UnaryOperator<String> rowText;
    private final List<String> ids = new ArrayList<>();

    public StringListTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged,
                         Function<DependencyGroup, List<String>> accessor,
                         UnaryOperator<String> rowText) {
        super(requirement, pickerFactory, onChanged);
        this.accessor = accessor;
        this.rowText = rowText;
    }

    public String idAt(int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : "";
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= ids.size()) return;
        ids.add(index + 1, ids.get(index));
        refreshRows();
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= ids.size()) return;
        ids.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    protected void onSelected(String id) {
        if (ids.contains(id)) return;
        ids.add(id);
        refreshRows();
        markChanged();
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return List.copyOf(ids);
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        ids.clear();
        ids.addAll(accessor.apply(group));
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        List<String> target = accessor.apply(group);
        target.clear();
        target.addAll(ids);
    }

    private void refreshRows() {
        rows().clear();
        for (String id : ids) rows().add(rowText.apply(id));
    }
}
