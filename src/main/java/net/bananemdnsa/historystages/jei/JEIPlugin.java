package net.bananemdnsa.historystages.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static IJeiRuntime jeiRuntime;
    private static final Map<String, List<ItemStack>> STAGE_ITEM_CACHE = new HashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(HistoryStages.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        refreshJei();
    }

    public static void clearStageItemCache() {
        STAGE_ITEM_CACHE.clear();
    }

    /**
     * Aktualisiert die Sichtbarkeit von Items in JEI basierend auf den freigeschalteten Stages.
     */
    public static void refreshJei() {
        if (jeiRuntime == null || !Config.CLIENT.hideInJei.get()) {
            return;
        }

        Map<String, ItemStack> allRelevant = new LinkedHashMap<>();
        for (Map.Entry<String, StageEntry> stage : StageManager.getStages().entrySet()) {
            for (ItemStack stack : getItemsForStage(stage.getKey(), stage.getValue())) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    allRelevant.putIfAbsent(id.toString(), stack);
                }
            }
        }

        applyVisibilityUpdate(new ArrayList<>(allRelevant.values()));
    }

    /**
     * Targeted refresh for changed stage ids only.
     * Evaluates final lock-state per affected item to avoid full JEI refresh.
     */
    public static void refreshJeiForStageChanges(Set<String> changedStageIds) {
        if (jeiRuntime == null || !Config.CLIENT.hideInJei.get()) {
            return;
        }
        if (changedStageIds == null || changedStageIds.isEmpty()) {
            return;
        }

        Map<String, StageEntry> stages = StageManager.getStages();
        Set<String> ids = new HashSet<>(changedStageIds);
        ids.retainAll(stages.keySet());

        Map<String, ItemStack> affected = new LinkedHashMap<>();
        for (String stageId : ids) {
            StageEntry entry = stages.get(stageId);
            if (entry == null) {
                continue;
            }
            for (ItemStack stack : getItemsForStage(stageId, entry)) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    affected.putIfAbsent(id.toString(), stack);
                }
            }
        }
        if (affected.isEmpty()) {
            return;
        }
        applyVisibilityUpdate(new ArrayList<>(affected.values()));
    }

    private static void applyVisibilityUpdate(List<ItemStack> items) {
        List<ItemStack> toHide = new ArrayList<>();
        List<ItemStack> toShow = new ArrayList<>();
        for (ItemStack stack : items) {
            if (StageManager.isItemLocked(stack, true)) {
                toHide.add(stack);
            } else {
                toShow.add(stack);
            }
        }
        if (!toHide.isEmpty()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
        }
        if (!toShow.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toShow);
        }
        LOGGER.info("[HistoryStages] JEI Refresh: {} items hidden, {} items shown.", toHide.size(), toShow.size());
    }

    private static List<ItemStack> getItemsForStage(String stageId, StageEntry entry) {
        return STAGE_ITEM_CACHE.computeIfAbsent(stageId, ignored -> collectStageItems(entry));
    }

    private static List<ItemStack> collectStageItems(StageEntry entry) {
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
