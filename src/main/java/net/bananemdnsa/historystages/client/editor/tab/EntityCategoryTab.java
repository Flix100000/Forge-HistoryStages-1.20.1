package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.lock.LockCategory;

/**
 * One of the three entity tabs — attack, spawn or interaction locks.
 *
 * <p>All three are views onto the same {@link EntityTabsState}, because the data underneath them
 * is one object: loading or storing any of them means loading or storing all three. What differs
 * per tab is only which list it shows and which extras hang off its rows.
 */
public final class EntityCategoryTab extends AbstractCategoryTab {

    private final EntityTabsState state;
    private final List<String> rows;

    public EntityCategoryTab(LockCategory<?> category,
                             PickerFactory pickerFactory,
                             Runnable onChanged,
                             EntityTabsState state,
                             List<String> rows) {
        super(category, pickerFactory, onChanged, rows);
        this.state = state;
        this.rows = rows;
    }

    @Override
    public void load(StageEntry stage) {
        state.load(stage);
    }

    @Override
    public void store(StageEntry stage) {
        state.store(stage);
    }

    @Override
    public void removeAt(int index) {
        state.removeFrom(rows, index);
    }
}
