package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The individual-stage requirement: a stage id plus whom the stage is demanded of — everyone
 * online, everyone the server has ever seen, or the player doing the research.
 *
 * <p>Draws itself, because the mode is a control inside the row. That is the smallest case needing
 * {@code renderContent} at all, which is why it migrated before the harder two.
 *
 * <p>The mode was a two-state toggle button while there were two modes. A third does not fit a
 * toggle — you would have to click past the one you want to find out what the third is — so the
 * slot is a dropdown, and the popup goes up as an overlay because the row list it lives in is
 * scrolled and scissored.
 */
public final class IndividualStageTab extends AbstractDependencyTab {

    private final EditorRowList rows = new EditorRowList();
    private final List<IndividualStageDep> deps = new ArrayList<>();

    /** Built on first use: it measures its labels against the font, which needs a running client. */
    @Nullable
    private DropdownOverlay modeOverlay;
    /** Which row the open mode popup belongs to, or -1. */
    private int modeRow = -1;

    /**
     * The modes this tab offers, which depends on the stage being edited rather than on the
     * requirement — see {@link IndividualStageDep#modesFor(boolean)}.
     */
    private final List<String> modes;

    public IndividualStageTab(Requirement requirement, PickerFactory pickerFactory,
                              Runnable onChanged, boolean individual) {
        super(requirement, pickerFactory, onChanged);
        this.modes = IndividualStageDep.modesFor(individual);
    }

    public String idAt(int index) {
        return index >= 0 && index < deps.size() ? deps.get(index).getStageId() : "";
    }

    /** Which row the host's context menu should be about, or -1. */
    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, deps.size());
    }

    /** Steps to the next mode. The context menu's way in, for a maintainer who never opens the popup. */
    public void cycleMode(int index) {
        if (index < 0 || index >= deps.size()) return;
        IndividualStageDep dep = deps.get(index);
        // Off the offered list, not off every mode there is: on a global stage this must walk the
        // same two the dropdown shows, or the menu puts back what the picker took away.
        int next = (modes.indexOf(dep.getMode()) + 1) % modes.size();
        dep.setMode(modes.get(next));
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
        closeModePicker();
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
            row.dropdown(modeLabel(dep.getMode()).getString(), tooltip(dep),
                    modeRow == i && modeOverlay != null && modeOverlay.isVisible(),
                    (x, y, w, h) -> openModePicker(i, x, y, h));
        });
        return true;
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        return button == 0 && rows.mouseClicked(ctx);
    }

    /**
     * The mode popup while it is up, the Add picker otherwise.
     *
     * <p>The popup is handed back for as long as it is still <em>drawing</em> rather than only
     * while it is open, so the host keeps rendering it through the roll-up after a pick. It stops
     * claiming input the moment it closes, because {@code isVisible()} is the open flag alone.
     */
    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        PickerOverlay picker = super.activeOverlay();
        if (picker != null && picker.isVisible()) return picker;
        if (modeOverlay != null && modeOverlay.isShowing()) return modeOverlay;
        return picker;
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= deps.size()) return;
        closeModePicker();
        deps.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    protected void onSelected(String id) {
        if (alreadyAddedIds().contains(id)) return;
        deps.add(new IndividualStageDep(id, IndividualStageDep.MODE_ALL_ONLINE));
        refreshRows();
        markChanged();
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return deps.stream().map(IndividualStageDep::getStageId).toList();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        closeModePicker();
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

    /** Opens the mode popup under the slot that was clicked. */
    private void openModePicker(int index, int slotX, int slotY, int slotHeight) {
        if (index < 0 || index >= deps.size()) return;
        if (modeOverlay == null) {
            modeOverlay = new DropdownOverlay(new EnumDropdown(modes,
                    deps.get(index).getMode(), 0, IndividualStageTab::modeLabel, this::applyMode));
        }
        modeRow = index;
        modeOverlay.dropdown().setValue(deps.get(index).getMode());
        modeOverlay.openAt(slotX, slotY, slotHeight);
    }

    private void closeModePicker() {
        if (modeOverlay != null) modeOverlay.hide();
        modeRow = -1;
    }

    /**
     * Writes a picked mode back to the row the popup was opened for.
     *
     * <p>Guarded on the row still existing: one dropdown instance serves every row, and the list
     * can shrink under it.
     */
    private void applyMode(String mode) {
        if (modeRow < 0 || modeRow >= deps.size()) return;
        deps.get(modeRow).setMode(mode);
        markChanged();
    }

    private static Component modeLabel(String mode) {
        if (IndividualStageDep.MODE_PLAYER.equals(mode)) {
            return Component.translatable("editor.historystages.dep.player");
        }
        return Component.translatable(IndividualStageDep.MODE_ALL_EVER.equals(mode)
                ? "editor.historystages.dep.ever"
                : "editor.historystages.dep.online");
    }

    private static String tooltip(IndividualStageDep dep) {
        if (dep.isPlayer()) {
            return Component.translatable("editor.historystages.dep.tooltip.mode_player").getString();
        }
        return Component.translatable(dep.isAllEver()
                ? "editor.historystages.dep.tooltip.mode_ever"
                : "editor.historystages.dep.tooltip.mode_online").getString();
    }

    private static String displayName(String stageId) {
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }
}
