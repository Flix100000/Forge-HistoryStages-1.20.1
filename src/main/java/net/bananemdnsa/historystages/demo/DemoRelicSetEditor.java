package net.bananemdnsa.historystages.demo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.dep.AbstractDependencyTab;
import net.bananemdnsa.historystages.client.editor.dep.DependencyTab;
import net.bananemdnsa.historystages.client.editor.dep.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditor;
import net.bananemdnsa.historystages.client.editor.tab.EntryAction;
import net.bananemdnsa.historystages.client.editor.tab.GenericIdPicker;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementStorage;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * The stand-in addon's own editor tab — the case the free tier cannot serve.
 *
 * <p>{@link RelicSetDep} has two fields and neither is a count, so
 * {@code RequirementEditor.ofIdCount} does not fit. This is what an addon writes instead:
 * a {@link RequirementEditor} that builds its own {@link DependencyTab} on
 * {@code AbstractDependencyTab}, which supplies the row list, the picker and its lifecycle.
 *
 * <p>What the addon has to write itself is small on purpose: how its entries turn into rows, what
 * a pick means, and how they are read and written. Everything else — drawing, hovering, scrolling,
 * the tab strip, copy and remove — comes from the host.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DemoRelicSetEditor {

    private DemoRelicSetEditor() {}

    @SubscribeEvent
    public static void onRegisterRequirementEditors(RegisterRequirementEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;
        event.register(new RelicSetEditor());
    }

    /**
     * Holds the tab it built so its declared action can reach it.
     *
     * <p>Both objects belong to the addon, so closing over one from the other is ordinary — the
     * host only ever asks this editor for a tab and for its actions.
     */
    private static final class RelicSetEditor implements RequirementEditor {

        private RelicSetTab tab;

        @Override
        public String requirementId() {
            return DemoRequirement.RELIC_SET_ID;
        }

        @Override
        public String searchPlaceholderLangKey() {
            return "editor.historystages.demo.search.relics";
        }

        @Override
        @Nullable
        public String amountLangKey() {
            return null; // entries carry a rarity, not an amount
        }

        @Override
        public Collection<String> candidates() {
            return DemoAddonCategory.candidateRelics();
        }

        @Override
        public DependencyTab createTab(Runnable onChanged) {
            Requirement requirement = RequirementTypes.byId(DemoRequirement.RELIC_SET_ID);
            tab = new RelicSetTab(requirement,
                    (onSelect, alreadyAdded) -> {
                        GenericIdPicker picker = new GenericIdPicker(
                                "editor.historystages.demo.search.relics",
                                DemoAddonCategory::candidateRelics, onSelect, alreadyAdded);
                        picker.setMultiSelect(true);
                        return picker;
                    },
                    onChanged);
            return tab;
        }

        @Override
        public List<EntryAction> entryActions() {
            // The rarity is the field the free tier had no room for, so this is how it is set.
            return List.of(EntryAction.of("editor.historystages.demo.context.cycle_rarity",
                    (index, onChanged) -> {
                        if (tab != null) tab.cycleRarity(index);
                    }));
        }
    }

    /** Rows read "epic amber_pendant", with the rarity repeated as the badge on the right. */
    private static final class RelicSetTab extends AbstractDependencyTab {

        private static final RequirementStorage<RelicSetDep> STORAGE =
                RequirementStorage.gson(RelicSetDep.class);

        private final List<RelicSetDep> items = new ArrayList<>();

        RelicSetTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
            super(requirement, pickerFactory, onChanged);
        }

        void cycleRarity(int index) {
            if (index < 0 || index >= items.size()) return;
            items.set(index, items.get(index).withNextRarity());
            refreshRows();
            markChanged();
        }

        @Override
        protected void onSelected(String relic) {
            items.add(new RelicSetDep(relic, RelicSetDep.RARITIES.get(0)));
            refreshRows();
            markChanged();
        }

        @Override
        protected Collection<String> alreadyAddedIds() {
            return items.stream().map(RelicSetDep::relic).toList();
        }

        @Override
        public void removeAt(int index) {
            if (index < 0 || index >= items.size()) return;
            items.remove(index);
            refreshRows();
            markChanged();
        }

        @Override
        @Nullable
        public String badgeText(int index) {
            return index >= 0 && index < items.size() ? "§b" + items.get(index).rarity() : null;
        }

        @Override
        protected void readFrom(DependencyGroup group) {
            items.clear();
            items.addAll(STORAGE.read(group.addonEntries(DemoRequirement.RELIC_SET_ID)));
            refreshRows();
        }

        @Override
        public void store(DependencyGroup group) {
            group.setAddonEntries(DemoRequirement.RELIC_SET_ID,
                    items.isEmpty() ? null : STORAGE.write(items));
        }

        private void refreshRows() {
            rows().clear();
            for (RelicSetDep item : items) rows().add(item.relic());
        }
    }
}
