package net.bananemdnsa.historystages.demo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;
import net.bananemdnsa.historystages.api.editor.DependencyTab;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RequirementEditor;
import net.bananemdnsa.historystages.api.editor.EntryAction;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.NumberStepper;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementStorage;
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
            tab = new RelicSetTab(DemoRequirement.relicSet(),
                    // Tier three: not one of the mod's lists, not a subclass of one, but a
                    // panel this addon draws itself. It exists because a relic entry needs a relic
                    // and a rarity, and a list of ids can only hand back one string.
                    (onSelect, alreadyAdded) -> new RelicPickerOverlay(
                            DemoAddonCategory::candidateRelics, alreadyAdded, onSelect,
                            rarity -> tab.setPendingRarity(rarity)),
                    onChanged);
            return tab;
        }

        @Override
        public List<EntryAction> entryActions() {
            // The rarity is the field the free tier had no room for, so this is how it is set.
            return List.of(
                    EntryAction.of("editor.historystages.demo.context.cycle_rarity",
                            ctx -> {
                                if (tab != null) tab.cycleRarity(ctx.index());
                            }),
                    // One of the mod's own popups, asked for rather than rebuilt.
                    EntryAction.dimensionFilter(
                            index -> tab == null ? "" : tab.relicAt(index),
                            index -> tab == null ? List.<String>of() : tab.dimensionsAt(index),
                            (index, allowed) -> {
                                if (tab != null) tab.setDimensionsAt(index, allowed);
                            }));
        }
    }

    /**
     * A tab that draws itself, and the demo of everything Phase 3b opened.
     *
     * <p>Taller rows than the mod draws, a rarity swatch painted into the leading box, a button in
     * the row that cycles the rarity, and below the rows a number stepper for the selected entry's
     * count. That last part is what needs the input hooks: its arrows are clicks, but typing a
     * value into it is a key press, and without keyPressed and charTyped it would draw and then
     * refuse to be typed into.
     */
    private static final class RelicSetTab extends AbstractDependencyTab {

        private static final RequirementStorage<RelicSetDep> STORAGE =
                RequirementStorage.gson(RelicSetDep.class);

        private static final int ROW_HEIGHT = 28;
        private static final int STEPPER_TOP_GAP = 8;
        private static final int STEPPER_HEIGHT = 20;

        /** One colour per rarity, in the order RARITIES lists them. */
        private static final int[] RARITY_COLOURS = { 0xFF9E9E9E, 0xFF4FA3FF, 0xFFC77DFF };

        private final EditorRowList rows = new EditorRowList(ROW_HEIGHT);
        private final List<RelicSetDep> items = new ArrayList<>();
        private final NumberStepper countStepper;
        /**
         * Where the built-in dimension filter puts what it collects.
         *
         * <p>The addon's own map, not something HistoryStages stores: the factory lends the popup,
         * it does not take over the data behind it.
         */
        private final java.util.Map<Integer, List<String>> dimensions = new java.util.HashMap<>();
        /** The rarity the picker chose, read by the next {@link #onSelected}. */
        private String pendingRarity = RelicSetDep.RARITIES.get(0);
        private int selected = 0;

        RelicSetTab(Requirement requirement, PickerFactory pickerFactory, Runnable onChanged) {
            super(requirement, pickerFactory, onChanged);
            this.countStepper = new NumberStepper(1, 999, 1, 1, this::applyCount);
        }

        @Override
        public void onShown() {
            rows.resetSlideIn();
        }

        @Override
        public int contentHeight(int width) {
            return rows.heightForRows(items.size()) + STEPPER_TOP_GAP + STEPPER_HEIGHT;
        }

        @Override
        public int rowAt(TabInputContext ctx) {
            return rows.rowAt(ctx, items.size());
        }

        @Override
        public boolean renderContent(TabRenderContext ctx) {
            if (selected >= items.size()) selected = items.size() - 1;

            int y = rows.render(ctx, items.size(), (row, i) -> {
                RelicSetDep item = items.get(i);
                int colour = RARITY_COLOURS[Math.max(0, RelicSetDep.RARITIES.indexOf(item.rarity()))];
                // A four-pixel stripe rather than a square: a square at the left of a row reads as
                // an item icon that failed to load, which is exactly what it is not.
                row.leading(4, (g, x, top, w, h) -> g.fill(x, top + 1, x + w, top + h - 1, colour));
                row.text((i == selected ? "▸ " : "  ") + item.count() + "x " + item.relic());
                row.badge("§b" + item.rarity());
                row.button(item.rarity(), () -> cycleRarity(i));
            });

            if (items.isEmpty()) return true;

            // The stepper edits whichever row is marked, so it says which one rather than sitting
            // there as an unlabelled pair of arrows.
            y += STEPPER_TOP_GAP;
            String caption = net.minecraft.network.chat.Component.translatable(
                    "editor.historystages.demo.count_label", items.get(selected).relic()).getString();
            ctx.graphics().drawString(ctx.font(), caption, ctx.x() + 6, y + 6, 0xAAAAAA, false);

            countStepper.setEnabled(true);
            countStepper.setPosition(ctx.x() + 12 + ctx.font().width(caption), y);
            countStepper.render(ctx.graphics(), ctx.font(), ctx.mouseX(), ctx.mouseY());
            return true;
        }

        @Override
        public boolean mouseClicked(TabInputContext ctx, int button) {
            if (button != 0) return false;
            if (countStepper.mouseClicked(ctx.mouseX(), ctx.mouseY())) return true;
            if (rows.mouseClicked(ctx)) return true;

            int row = rows.rowAt(ctx, items.size());
            if (row >= 0) {
                // Selecting a row is what the stepper below then edits.
                selected = row;
                countStepper.setValue(items.get(row).count());
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return countStepper.keyPressed(keyCode);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return countStepper.charTyped(codePoint);
        }

        private void applyCount(int count) {
            if (selected < 0 || selected >= items.size()) return;
            items.set(selected, items.get(selected).withCount(count));
            refreshRows();
            markChanged();
        }

        /** The dimensions this entry counts in, which is what the built-in filter action edits. */
        List<String> dimensionsAt(int index) {
            return index >= 0 && index < items.size()
                    ? dimensions.getOrDefault(index, List.of()) : List.of();
        }

        void setDimensionsAt(int index, List<String> allowed) {
            if (index < 0 || index >= items.size()) return;
            dimensions.put(index, List.copyOf(allowed));
            markChanged();
        }

        String relicAt(int index) {
            return index >= 0 && index < items.size() ? items.get(index).relic() : "";
        }

        void cycleRarity(int index) {
            if (index < 0 || index >= items.size()) return;
            items.set(index, items.get(index).withNextRarity());
            refreshRows();
            markChanged();
        }

        /** What the own picker parks before it hands the id over. */
        void setPendingRarity(String rarity) {
            this.pendingRarity = rarity;
        }

        @Override
        protected void onSelected(String relic) {
            items.add(new RelicSetDep(relic, pendingRarity, 1));
            selected = items.size() - 1;
            countStepper.setValue(1);
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
