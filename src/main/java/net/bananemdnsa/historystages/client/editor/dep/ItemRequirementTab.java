package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.gson.JsonObject;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.ItemTagResolution;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * The built-in item requirement as a tab.
 *
 * <p>Migrated first on purpose: it is the hardest of the eight, with a per-entry NBT payload, a
 * count, an icon and a badge. A seam that carries this carries what an addon will want.
 *
 * <p>The NBT used to live in a side table on the screen, keyed by row index, which every reorder
 * and removal had to shift by hand in three separate places. It lives on the entries here, so a
 * moved row takes its NBT with it and there is nothing left to keep in step.
 *
 * <p><strong>This class is two tabs.</strong> Items and item tags are the same row in every way
 * the editor cares about — an id, a count, an icon, an optional NBT criterion — and differ only
 * in which list of the group they belong to and which picker fills them. So the group access is a
 * constructor argument, the way {@code StringListTab} takes {@code DependencyGroup::getStages},
 * and the screen builds two instances. A copy of this file would have been a second place to fix
 * every future bug in it.
 */
public final class ItemRequirementTab extends AbstractDependencyTab {

    private final List<DependencyItem> items = new ArrayList<>();
    private final Function<DependencyGroup, List<DependencyItem>> reader;
    private final BiConsumer<DependencyGroup, List<DependencyItem>> writer;
    private BiConsumer<Integer, String> onEditNbt = (index, itemId) -> { };
    private java.util.function.Consumer<String> onCountNeeded = id -> { };

    /** The plain-item tab: reads and writes the group's {@code items}. */
    public ItemRequirementTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
        this(requirement, pickerFactory, onChanged,
                DependencyGroup::getItems, DependencyGroup::setItems);
    }

    public ItemRequirementTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged,
                              Function<DependencyGroup, List<DependencyItem>> reader,
                              BiConsumer<DependencyGroup, List<DependencyItem>> writer) {
        super(requirement, pickerFactory, onChanged);
        this.reader = reader;
        this.writer = writer;
    }

    /**
     * What the host does when a row's NBT should be edited. The tab cannot open the editor itself:
     * it is a screen, and a tab has no screen to push one onto.
     */
    public void setOnEditNbt(BiConsumer<Integer, String> handler) {
        this.onEditNbt = handler;
    }

    public void requestNbtEdit(int index) {
        if (index >= 0 && index < items.size()) onEditNbt.accept(index, items.get(index).getId());
    }

    public String idAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).getId() : "";
    }

    public int countAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).getCount() : 1;
    }

    public void setCountAt(int index, int count) {
        if (index < 0 || index >= items.size()) return;
        items.get(index).setCount(count);
        refreshRows();
        markChanged();
    }

    public void addItem(String id, int count) {
        items.add(new DependencyItem(id, count));
        refreshRows();
        markChanged();
    }

    @Nullable
    public JsonObject nbtAt(int index) {
        if (index < 0 || index >= items.size()) return null;
        DependencyItem item = items.get(index);
        return item.hasNbt() ? item.getNbt().deepCopy() : null;
    }

    public void setNbtAt(int index, @Nullable JsonObject nbt) {
        if (index < 0 || index >= items.size()) return;
        items.get(index).setNbt(nbt);
        refreshRows();
        markChanged();
    }

    public void duplicateAt(int index) {
        if (index < 0 || index >= items.size()) return;
        // copy() carries the NBT, which is the whole reason the side table could go.
        items.add(index + 1, items.get(index).copy());
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

    /** What the host does when a pick needs a count before it becomes an entry. */
    public void setOnCountNeeded(java.util.function.Consumer<String> handler) {
        this.onCountNeeded = handler;
    }

    @Override
    protected void onSelected(String id) {
        // A pick needs a count before it is an entry, and only the host can open that dialog.
        // Adding here as well would produce a row of one alongside the one the dialog creates.
        onCountNeeded.accept(id);
    }

    @Override
    protected Collection<String> alreadyAddedIds() {
        return items.stream().map(DependencyItem::getId).toList();
    }

    @Override
    @Nullable
    public String iconItemId(int index) {
        return index >= 0 && index < items.size() ? items.get(index).getId() : null;
    }

    @Override
    @Nullable
    public String badgeText(int index) {
        return index >= 0 && index < items.size() && items.get(index).hasNbt()
                ? "§6[NBT]" : null;
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        items.clear();
        // Copies, not the group own objects: the tab edits these freely and store() writes the
        // whole list back, so sharing instances would make an unstored edit visible anyway.
        for (DependencyItem item : reader.apply(group)) items.add(item.copy());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        writer.accept(group, new ArrayList<>(items));
    }

    private void refreshRows() {
        rows().clear();
        for (DependencyItem item : items) {
            rows().add(item.getCount() + "x " + displayName(item.getId()));
        }
    }

    /**
     * The row label. A tag has no name of its own, so it borrows one from whichever member the
     * cycle is on — the same member the icon beside it is showing, because both read the clock.
     */
    private static String displayName(String itemId) {
        if (ItemTagResolution.isTag(itemId)) {
            return ItemTagResolution.displayName(itemId, null, System.currentTimeMillis());
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return itemId;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == null ? itemId : item.getDescription().getString();
    }
}
