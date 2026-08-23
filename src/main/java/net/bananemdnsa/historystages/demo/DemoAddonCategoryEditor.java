package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.tab.CategoryEditor;
import net.bananemdnsa.historystages.client.editor.tab.RegisterCategoryEditorsEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The client half of the stand-in addon: one call, and the category has a tab that looks and
 * behaves like a built-in.
 *
 * <p>This is the whole free tier. Everything the tab does — the searchable picker, multi-select,
 * the row list, add and remove, saving into the stage file — comes from having said which ids
 * exist.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DemoAddonCategoryEditor {

    private DemoAddonCategoryEditor() {}

    @SubscribeEvent
    public static void onRegisterEditors(RegisterCategoryEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(CategoryEditor.ofIdList(
                DemoAddonCategory.CATEGORY_ID,
                "editor.historystages.demo.search.relics",
                DemoAddonCategory::candidateRelics));
    }

    @SubscribeEvent
    public static void onRegisterTriggerEditors(
            net.bananemdnsa.historystages.client.editor.trigger.RegisterTriggerEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(net.bananemdnsa.historystages.client.editor.trigger.TriggerEditor.ofIdList(
                DemoAddonCategory.TRIGGER_TYPE,
                "editor.historystages.demo.auto_trigger.relic_found",
                "editor.historystages.demo.search.relics",
                DemoAddonCategory::candidateRelics,
                RelicFoundTrigger::new,
                t -> t instanceof RelicFoundTrigger r ? r.relic() : ""));

        // The other kind: a trigger with a number and no id, which a picker cannot author because
        // there is nothing to pick. It supplies a screen instead.
        event.register(new net.bananemdnsa.historystages.client.editor.trigger.TriggerEditor() {
            @Override public String type() { return RelicHoardTrigger.TYPE; }
            @Override public String labelLangKey() {
                return "editor.historystages.demo.auto_trigger.relic_hoard";
            }
            @Override public String searchPlaceholderLangKey() {
                return "editor.historystages.demo.search.relics"; // unused; authoring is a screen
            }
            @Override public java.util.Collection<String> candidates() { return java.util.List.of(); }
            @Override public net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition
                    create(String chosenId) {
                // Never reached while authoringScreen answers; a sane value rather than a throw,
                // because a future caller finding this should get a trigger, not a crash.
                return new RelicHoardTrigger(1);
            }
            @Override public net.minecraft.client.gui.screens.Screen authoringScreen(
                    net.minecraft.client.gui.screens.Screen parent,
                    java.util.function.Consumer<net.bananemdnsa.historystages.data.auto.conditions
                            .TriggerCondition> onCreated) {
                return new net.bananemdnsa.historystages.client.editor.dialog.CountInputScreen(
                        parent,
                        net.minecraft.network.chat.Component.translatable(
                                "editor.historystages.demo.auto_trigger.relic_hoard"),
                        "", 5, 1, 999,
                        count -> onCreated.accept(new RelicHoardTrigger(count)));
            }
            @Override public String valueText(
                    net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition trigger) {
                return trigger instanceof RelicHoardTrigger h ? String.valueOf(h.count()) : "";
            }
        });
    }
}
