package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.CustomFieldScreens;
import net.bananemdnsa.historystages.api.editor.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRecipeTypeMetaEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterTriggerEditorsEvent;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditors;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeTypeMetas;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerEditors;

import net.bananemdnsa.historystages.HistoryStages;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Opens and closes the windows in which an addon may give its category an editor tab and its
 * auto-trigger type a way to be authored.
 *
 * <p>Client-only by annotation, so nothing here is ever loaded on a dedicated server — the editor
 * classes this reaches are pure UI, and pulling them onto the server is how the crash fixed in
 * commit 0469f73 happened.
 *
 * <p>Client setup runs after common setup, so the lock categories themselves are already
 * registered and frozen by the time an editor can be attached to one.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientCategoryEditorSetup {

    private ClientCategoryEditorSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModLoader.postEvent(new RegisterCategoryEditorsEvent());
            CategoryEditors.freeze();

            ModLoader.postEvent(new RegisterTriggerEditorsEvent());
            TriggerEditors.freeze();

            ModLoader.postEvent(new RegisterRequirementEditorsEvent());
            RequirementEditors.freeze();

            // One window for both declarative axes: a CUSTOM_SCREEN field on a stage setting and
            // one in the config screen ask the same question, which screen edits this value.
            ModLoader.postEvent(new RegisterCustomFieldScreensEvent());
            CustomFieldScreens.freeze();

            // Cosmetic only: what block stands for a recipe type, what colour its card gets,
            // what it is called. Client setup, not common, because nothing off the client has
            // any use for it.
            ModLoader.postEvent(new RegisterRecipeTypeMetaEvent());
            RecipeTypeMetas.freeze();
        });
    }
}
