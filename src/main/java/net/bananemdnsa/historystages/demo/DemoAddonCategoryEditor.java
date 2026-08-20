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
}
