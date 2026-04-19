package net.bananemdnsa.historystages.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IAdvancedRegistration;
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

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static IJeiRuntime jeiRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        return MOD_RESOURCE_LOCATION("jei_plugin");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // Register decorator for ALL recipe types (vanilla + modded)
        registration.getJeiHelpers().getAllRecipeTypes().forEach(recipeType -> {
            registration.addRecipeCategoryDecorator((mezz.jei.api.recipe.RecipeType) recipeType, new LockedRecipeDecorator<>());
        });
        LOGGER.info("[HistoryStages] Registered locked recipe decorators for all JEI recipe types.");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        LOGGER.info("[HistoryStages] JEI onRuntimeAvailable called — refreshing items.");
        refreshJei();
    }

    /**
     * Aktualisiert die Sichtbarkeit von Items UND Rezepten in JEI basierend auf den freigeschalteten Stages.
     */
    public static void refreshJei() {
        if (jeiRuntime == null || !Config.CLIENT.hideInJei.get()) {
            return;
        }

        // --- Item-Sichtbarkeit ---
        List<ItemStack> toHide = new ArrayList<>();
        List<ItemStack> toShow = new ArrayList<>();

        for (Map.Entry<String, StageEntry> entrySet : StageManager.getStages().entrySet()) {
            String stageId = entrySet.getKey();
            StageEntry entry = entrySet.getValue();

            List<ItemStack> stageItems = getItemsForStage(entry);

            if (!ClientStageCache.isStageUnlocked(stageId)) {
                toHide.addAll(stageItems);
            } else {
                toShow.addAll(stageItems);
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

    private static List<ItemStack> getItemsForStage(StageEntry entry) {
        List<ItemStack> items = new ArrayList<>();

        // 1. Einzelne Items
        if (entry.getItems() != null) {
            for (String itemId : entry.getItems()) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    items.add(new ItemStack(item));
                }
            }
        }

        // 2. Ganze Mods (respecting mod exceptions)
        if (entry.getMods() != null) {
            for (String modId : entry.getMods()) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
                    if (res != null && res.getNamespace().equals(modId)) {
                        ItemStack stack = new ItemStack(item);
                        if (!entry.isModExcepted(res.toString(), stack)) {
                            items.add(stack);
                        }
                    }
                }
            }
        }

        // 3. Tags
        if (entry.getTags() != null) {
            for (String tagId : entry.getTags()) {
                ResourceLocation tagRes = ResourceLocation.parse(tagId);
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