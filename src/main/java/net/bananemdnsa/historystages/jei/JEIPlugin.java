package net.bananemdnsa.historystages.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;


@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getPluginUid() {
        return MOD_RESOURCE_LOCATION("jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Tell JEI that scrolls with different StageResearch values are different items
        IIngredientSubtypeInterpreter<ItemStack> interpreter = (stack, context) -> {
            if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                return stack.getTag().getString("StageResearch");
            }
            return IIngredientSubtypeInterpreter.NONE;
        };
        registration.registerSubtypeInterpreter(ModItems.RESEARCH_SCROLL.get(), interpreter);
        registration.registerSubtypeInterpreter(ModItems.CREATIVE_SCROLL.get(), interpreter);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Add one scroll variant per stage so they appear in JEI
        List<ItemStack> scrolls = new ArrayList<>();

        for (String stageId : StageManager.getStages().keySet()) {
            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
            scroll.getOrCreateTag().putString("StageResearch", stageId);
            scrolls.add(scroll);
        }

        for (String stageId : StageManager.getIndividualStages().keySet()) {
            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
            scroll.getOrCreateTag().putString("StageResearch", stageId);
            scrolls.add(scroll);
        }

        if (!scrolls.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, scrolls);
            LOGGER.info("[HistoryStages] Added {} research scroll variants to JEI.", scrolls.size());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.getJeiHelpers().getAllRecipeTypes().forEach(recipeType -> {
            registration.addRecipeCategoryDecorator((mezz.jei.api.recipe.RecipeType) recipeType, new LockedRecipeDecorator<>());
        });
        LOGGER.info("[HistoryStages] Registered locked recipe decorators for all JEI recipe types.");
    }
}