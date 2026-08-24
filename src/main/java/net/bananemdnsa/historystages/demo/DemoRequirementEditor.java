package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RequirementEditor;
import net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent;
import net.bananemdnsa.historystages.api.editor.widget.FormattedTextScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The client half of the stand-in addon's requirement: one call, and it has a tab that looks and
 * behaves like a built-in.
 *
 * <p>This is the whole free tier. The searchable picker, the amount dialog, the row list, add,
 * duplicate and remove, and saving into the stage file all come from having said which ids exist
 * and that entries carry an amount.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DemoRequirementEditor {

    private DemoRequirementEditor() {}

    @SubscribeEvent
    public static void onRegisterRequirementEditors(RegisterRequirementEditorsEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(RequirementEditor.ofIdCount(
                DemoRequirement.REQUIREMENT_ID,
                "editor.historystages.demo.search.relics",
                "editor.historystages.demo.dep.dialog.relic_count",
                DemoAddonCategory::candidateRelics));
    }

    /**
     * The screen behind the demo's {@code CUSTOM_SCREEN} setting.
     *
     * <p>A real addon would write its own; this one borrows the mod's formatted-text screen,
     * because what is being demonstrated is the wiring — a field the host cannot render, edited by
     * a screen the addon chose, with the value coming back as a string.
     */
    @SubscribeEvent
    public static void onRegisterCustomFieldScreens(RegisterCustomFieldScreensEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(DemoSettingsGroup.RELIC_LAYOUT, (parent, current, onDone) ->
                new FormattedTextScreen(parent,
                        Component.translatable("settings.hsdemo.settings.field.relic_layout"),
                        current,
                        Component.translatable("settings.hsdemo.settings.field.relic_layout.hint")
                                .getString(),
                        java.util.List.of("{relic}", "{rarity}"),
                        onDone));
    }
}
