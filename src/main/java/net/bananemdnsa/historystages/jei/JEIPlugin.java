package net.bananemdnsa.historystages.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static IJeiRuntime jeiRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(HistoryStages.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        refreshJei();
    }

    /**
     * Aktualisiert die Sichtbarkeit von Items in JEI basierend auf den freigeschalteten Stages.
     */
    public static void refreshJei() {
        // KORREKTUR: Zugriff über Config.CLIENT und .get()
        if (jeiRuntime == null || !Config.CLIENT.hideInJei.get()) {
            return;
        }

        List<ItemStack> toHide = new ArrayList<>();
        List<ItemStack> toShow = new ArrayList<>();

        for (Map.Entry<String, StageEntry> entrySet : StageManager.getStages().entrySet()) {
            String stageId = entrySet.getKey();
            StageEntry entry = entrySet.getValue();

            List<ItemStack> stageItems = getItemsForStage(entry);

            // Nutzt deine Methode isStageUnlocked aus dem ClientStageCache
            if (!ClientStageCache.isStageUnlocked(stageId)) {
                toHide.addAll(stageItems);
            } else {
                toShow.addAll(stageItems);
            }
        }

        // Änderungen live an JEI übertragen
        if (!toHide.isEmpty()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
        }
        if (!toShow.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toShow);
        }

        LOGGER.info("[HistoryStages] JEI Refresh: {} items hidden, {} items shown.", toHide.size(), toShow.size());
    }

    private static List<ItemStack> getItemsForStage(StageEntry entry) {
        List<ItemStack> items = new ArrayList<>();

        // 1. Einzelne Items
        if (entry.getItems() != null) {
            for (String itemId : entry.getItems()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    items.add(new ItemStack(item));
                }
            }
        }

        // 2. Ganze Mods
        if (entry.getMods() != null) {
            for (String modId : entry.getMods()) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
                    if (res != null && res.getNamespace().equals(modId)) {
                        items.add(new ItemStack(item));
                    }
                }
            }
        }

        // 3. Tags
        if (entry.getTags() != null) {
            for (String tagId : entry.getTags()) {
                ResourceLocation tagRes = new ResourceLocation(tagId);
                for (Item item : ForgeRegistries.ITEMS) {
                    ItemStack stack = new ItemStack(item);
                    if (stack.getTags().anyMatch(t -> t.location().equals(tagRes))) {
                        items.add(stack);
                    }
                }
            }
        }

        return items;
    }
}