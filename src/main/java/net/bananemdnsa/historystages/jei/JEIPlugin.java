package net.bananemdnsa.historystages.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;


@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

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
}