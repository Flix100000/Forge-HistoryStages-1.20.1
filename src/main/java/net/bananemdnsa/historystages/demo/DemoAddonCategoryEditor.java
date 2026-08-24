package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.client.editor.dialog.CountInputScreen;
import net.bananemdnsa.historystages.client.editor.tab.CategoryEditor;
import net.bananemdnsa.historystages.client.editor.tab.CategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.GenericIdPicker;
import net.bananemdnsa.historystages.client.editor.tab.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.client.editor.trigger.RegisterTriggerEditorsEvent;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerEditor;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
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
            public CategoryTab createTab(Runnable onChanged) {
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
    }
}
