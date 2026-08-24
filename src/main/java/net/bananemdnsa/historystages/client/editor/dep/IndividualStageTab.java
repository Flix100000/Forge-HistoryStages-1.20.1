package net.bananemdnsa.historystages.client.editor.dep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bananemdnsa.historystages.client.editor.tab.TabInputContext;
import net.bananemdnsa.historystages.client.editor.tab.TabRenderContext;
import net.bananemdnsa.historystages.client.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.minecraft.network.chat.Component;

/**
 * The individual-stage requirement: a stage id plus whether it counts everyone online now or
 * everyone who ever had it.
 *
 * <p>Draws itself, because the mode is a button inside the row. That is the smallest case needing
 * {@code renderContent} at all, which is why it migrated before the harder two.
 */
public final class IndividualStageTab extends AbstractDependencyTab {

    private final EditorRowList rows = new EditorRowList();
    private final List<IndividualStageDep> deps = new ArrayList<>();

    public IndividualStageTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
        super(requirement, pickerFactory, onChanged);
    }

    public String idAt(int index) {
        return index >= 0 && index < deps.size() ? deps.get(index).getStageId() : "";
    }

    /** Which row the host's context menu should be about, or -1. */
    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, deps.size());
    }

    public void toggleMode(int index) {
        if (index < 0 || index >= deps.size()) return;
        IndividualStageDep dep = deps.get(index);
        dep.setMode(dep.isAllEver() ? "all_online" : "all_ever");
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= deps.size()) return;
        deps.add(index + 1, deps.get(index).copy());
        refreshRows();
        markChanged();
    }

    @Override
    public void onShown() {
        rows.resetSlideIn();
    }

    @Override
    public int contentHeight(int width) {
        return rows.heightForRows(deps.size());
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        rows.render(ctx, deps.size(), (row, i) -> {
            IndividualStageDep dep = deps.get(i);
            row.text(displayName(dep.getStageId()) + " §7(" + dep.getStageId() + ")");
            row.button(label(dep), tooltip(dep), () -> toggleMode(i));
        });
        return true;
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        return button == 0 && rows.mouseClicked(ctx);
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= deps.size()) return;
        deps.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    protected void onSelected(String id) {
        if (alreadyAddedIds().contains(id)) return;
        deps.add(new IndividualStageDep(id, "all_online"));
        refreshRows();
        markChanged();
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return deps.stream().map(IndividualStageDep::getStageId).toList();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        deps.clear();
        for (IndividualStageDep dep : group.getIndividualStages()) deps.add(dep.copy());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setIndividualStages(new ArrayList<>(deps));
    }

    /**
     * The row list draws from {@code deps}; {@code rows()} is kept in step because
     * {@code entries()} is what the host counts for the group list, whatever the tab draws.
     */
    private void refreshRows() {
        rows().clear();
        for (IndividualStageDep dep : deps) rows().add(dep.getStageId());
    }

    private static String label(IndividualStageDep dep) {
        return Component.translatable(dep.isAllEver()
                ? "editor.historystages.dep.ever"
                : "editor.historystages.dep.online").getString();
    }

    private static String tooltip(IndividualStageDep dep) {
        return Component.translatable(dep.isAllEver()
                ? "editor.historystages.dep.tooltip.mode_ever"
                : "editor.historystages.dep.tooltip.mode_online").getString();
    }

    private static String displayName(String stageId) {
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }
}
