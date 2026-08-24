package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.EntityKillDep;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The entity-kill requirement: an entity and how many of it.
 *
 * <p>Draws itself for one reason — its rows are 32 pixels tall and carry a spinning model. That is
 * the proof that a tab may pick its own row height: the host's scrollbar sizes itself from
 * {@link #contentHeight}, not from an assumption about how tall a row is.
 */
public final class EntityKillTab extends AbstractDependencyTab {

    private static final int ROW_HEIGHT = 32;

    private final EditorRowList rows = new EditorRowList(ROW_HEIGHT);
    private final List<EntityKillDep> kills = new ArrayList<>();
    private Consumer<String> onCountNeeded = id -> { };

    public EntityKillTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
        super(requirement, pickerFactory, onChanged);
    }

    /** What the host does when a pick needs a count before it becomes an entry. */
    public void setOnCountNeeded(Consumer<String> handler) {
        this.onCountNeeded = handler;
    }

    public String idAt(int index) {
        return index >= 0 && index < kills.size() ? kills.get(index).getEntityId() : "";
    }

    public int countAt(int index) {
        return index >= 0 && index < kills.size() ? kills.get(index).getCount() : 1;
    }

    public void setCountAt(int index, int count) {
        if (index < 0 || index >= kills.size()) return;
        kills.get(index).setCount(count);
        refreshRows();
        markChanged();
    }

    public void addKill(String entityId, int count) {
        kills.add(new EntityKillDep(entityId, count));
        refreshRows();
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= kills.size()) return;
        kills.add(index + 1, kills.get(index).copy());
        refreshRows();
        markChanged();
    }

    /** Which row the host's context menu should be about, or -1. */
    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, kills.size());
    }

    @Override
    public void onShown() {
        rows.resetSlideIn();
    }

    @Override
    public int contentHeight(int width) {
        return rows.heightForRows(kills.size());
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        rows.render(ctx, kills.size(), (row, i) -> {
            EntityKillDep kill = kills.get(i);
            // Skipped while an overlay is up, or the model draws through it.
            LivingEntity model = ctx.inputBlocked()
                    ? null : EntityPreviewRenderer.getOrCreate(kill.getEntityId());
            if (model != null) {
                row.leading(25, (g, x, y, w, h) -> {
                    float angle = (System.currentTimeMillis() % 4000) / 4000.0f * 360.0f;
                    g.enableScissor(x, Math.max(y, ctx.clipTop()),
                            x + w, Math.min(y + h, ctx.clipBottom()));
                    EntityPreviewRenderer.renderSpinning(g, x + w / 2 - 3, y + h, 10, angle, model);
                    g.disableScissor();
                });
            }
            row.text(kill.getCount() + "x " + entityName(kill.getEntityId()));
        });
        return true;
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= kills.size()) return;
        kills.remove(index);
        refreshRows();
        markChanged();
    }

    @Override
    protected void onSelected(String id) {
        // Today a pick lands at 1 and the count is changed from the row's menu; opening a dialog
        // per pick would break the picker's multi-select, which this requirement uses.
        addKill(id, 1);
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return kills.stream().map(EntityKillDep::getEntityId).toList();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        kills.clear();
        for (EntityKillDep kill : group.getEntityKills()) kills.add(kill.copy());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setEntityKills(new ArrayList<>(kills));
    }

    private void refreshRows() {
        rows().clear();
        for (EntityKillDep kill : kills) rows().add(kill.getEntityId());
    }

    private static String entityName(String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return entityId;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        return type == null ? entityId : type.getDescription().getString();
    }
}
