package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.widget.ChoiceOverlay;
import net.bananemdnsa.historystages.api.editor.widget.ChoiceScreen;
import net.bananemdnsa.historystages.api.editor.widget.CountInputScreen;
import net.bananemdnsa.historystages.api.editor.CategoryEditor;
import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.editor.GenericIdPicker;
import net.bananemdnsa.historystages.api.editor.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterTriggerEditorsEvent;
import net.bananemdnsa.historystages.api.editor.TriggerEditor;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The client half of the stand-in addon: one call, and the category has a tab that looks and
 * behaves like a built-in.
 *
 * <p>The category registers a tab of its own rather than taking the free tier, because the lock
 * axis needs the same proof the dependency axis has: that a tab drawing itself works in the
 * <em>stage</em> editor as well. {@code CategoryEditor.ofIdList} is still the one-call alternative
 * and is what an addon that only wants a list should use — the trigger below is registered that
 * way, so both tiers stay visible in one file.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DemoAddonCategoryEditor {

    private DemoAddonCategoryEditor() {}

    @SubscribeEvent
    public static void onRegisterEditors(RegisterCategoryEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(new CategoryEditor() {
            @Override
            public String categoryId() {
                return DemoAddonCategory.CATEGORY_ID;
            }

            @Override
            public CategoryTab createTab(Runnable onChanged, StageScope scope) {
                return new DemoCategoryTab(DemoAddonCategory.category(),
                        (onSelect, alreadyAdded) -> {
                            GenericIdPicker picker = new GenericIdPicker(
                                    "editor.historystages.demo.search.relics",
                                    DemoAddonCategory::candidateRelics, onSelect, alreadyAdded);
                            picker.setMultiSelect(true);
                            return picker;
                        },
                        onChanged);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterTriggerEditors(RegisterTriggerEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(TriggerEditor.ofIdList(
                DemoAddonCategory.TRIGGER_TYPE,
                "editor.historystages.demo.auto_trigger.relic_found",
                "editor.historystages.demo.search.relics",
                DemoAddonCategory::candidateRelics,
                RelicFoundTrigger::new,
                t -> t instanceof RelicFoundTrigger r ? r.relic() : ""));

        // The other kind: a trigger with a number and no id, which a picker cannot author because
        // there is nothing to pick. It supplies a screen instead.
        event.register(new TriggerEditor() {
            @Override
            public String type() {
                return RelicHoardTrigger.TYPE;
            }

            @Override
            public String labelLangKey() {
                return "editor.historystages.demo.auto_trigger.relic_hoard";
            }

            @Override
            public String searchPlaceholderLangKey() {
                return "editor.historystages.demo.search.relics"; // unused; authoring is a screen
            }

            @Override
            public Collection<String> candidates() {
                return List.of();
            }

            @Override
            public TriggerCondition create(String chosenId) {
                // Never reached while authoringScreen answers; a sane value rather than a throw,
                // because a future caller finding this should get a trigger, not a crash.
                return new RelicHoardTrigger(1);
            }

            @Override
            public Screen authoringScreen(Screen parent, Consumer<TriggerCondition> onCreated) {
                return new CountInputScreen(parent,
                        Component.translatable("editor.historystages.demo.auto_trigger.relic_hoard"),
                        "", 5, 1, 999,
                        count -> onCreated.accept(new RelicHoardTrigger(count)));
            }

            @Override
            public String valueText(TriggerCondition trigger) {
                return trigger instanceof RelicHoardTrigger h ? String.valueOf(h.count()) : "";
            }
        });

        // The third kind: one of a handful of named things. A searchable list over three rows is
        // ceremony and there is nothing to count, so it authors through a choice screen.
        event.register(new TriggerEditor() {
            @Override
            public String type() {
                return RelicRarityTrigger.TYPE;
            }

            @Override
            public String labelLangKey() {
                return "editor.historystages.demo.auto_trigger.relic_rarity";
            }

            @Override
            public String searchPlaceholderLangKey() {
                return "editor.historystages.demo.search.relics"; // unused; authoring is a screen
            }

            @Override
            public Collection<String> candidates() {
                return RelicRarityTrigger.RARITIES;
            }

            @Override
            public TriggerCondition create(String chosenId) {
                return new RelicRarityTrigger(chosenId);
            }

            @Override
            public Screen authoringScreen(Screen parent, Consumer<TriggerCondition> onCreated) {
                List<ChoiceOverlay.Option> rows = RelicRarityTrigger.RARITIES.stream()
                        .map(rarity -> ChoiceOverlay.Option.of(
                                Component.translatable(
                                        "editor.historystages.demo.rarity." + rarity).getString(),
                                () -> onCreated.accept(new RelicRarityTrigger(rarity))))
                        .toList();
                return new ChoiceScreen(parent,
                        Component.translatable("editor.historystages.demo.auto_trigger.relic_rarity"),
                        rows);
            }

            @Override
            public String valueText(TriggerCondition trigger) {
                return trigger instanceof RelicRarityTrigger r
                        ? Component.translatable("editor.historystages.demo.rarity." + r.rarity()).getString()
                        : "";
            }
        });
    }
}
