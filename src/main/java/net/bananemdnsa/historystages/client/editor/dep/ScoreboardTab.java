package net.bananemdnsa.historystages.client.editor.dep;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import org.jetbrains.annotations.Nullable;

/**
 * The scoreboard requirement: an objective, a comparison, a value and optionally a holder.
 *
 * <p>The one built-in with no picker. Four fields cannot be chosen from a list, so Add opens the
 * scoreboard dialog instead — which the host does, because a tab has no screen to push onto.
 */
public final class ScoreboardTab extends AbstractDependencyTab {

    private final List<ScoreboardDep> deps = new ArrayList<>();
    private IntConsumer onEditRequested = index -> { };

    public ScoreboardTab(Requirement requirement, Runnable onChanged) {
        // No picker factory: there is nothing to pick from. openPicker is overridden below.
        super(requirement, (onSelect, alreadyAdded) -> null, onChanged);
    }

    /** What the host does when an entry needs authoring. {@code -1} means a new one. */
    public void setOnEditRequested(IntConsumer handler) {
        this.onEditRequested = handler;
    }

    @Nullable
    public ScoreboardDep at(int index) {
        return index >= 0 && index < deps.size() ? deps.get(index) : null;
    }

    /** Writes a confirmed dialog back. An index outside the list appends instead. */
    public void apply(int index, String objective, @Nullable String holder, String op, int value) {
        if (index >= 0 && index < deps.size()) {
            ScoreboardDep dep = deps.get(index);
            dep.setObjective(objective);
            dep.setScoreHolder(holder);
            dep.setOp(op);
            dep.setValue(value);
        } else {
            deps.add(new ScoreboardDep(objective, holder, op, value));
        }
        refreshRows();
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= deps.size()) return;
        deps.add(index + 1, deps.get(index).copy());
        refreshRows();
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= deps.size()) return;
        deps.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        onEditRequested.accept(-1);
    }

    @Override
    public void rebuildPicker() {
        // Nothing to rebuild — there is no picker.
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        deps.clear();
        for (ScoreboardDep dep : group.getScoreboard()) deps.add(dep.copy());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setScoreboard(new ArrayList<>(deps));
    }

    private void refreshRows() {
        rows().clear();
        for (ScoreboardDep dep : deps) {
            String holder = dep.isPlayerSelf() ? "" : " [" + dep.getScoreHolder() + "]";
            rows().add(dep.getObjective() + " " + dep.getOp() + " " + dep.getValue() + holder);
        }
    }
}
