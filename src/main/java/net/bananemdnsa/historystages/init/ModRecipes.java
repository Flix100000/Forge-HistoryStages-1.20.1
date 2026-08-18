package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.recipe.ResealScrollRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, HistoryStages.MOD_ID);

    /**
     * Registered as a special recipe rather than a shaped one because the result has to carry the
     * stage id read off the input; a data-driven recipe has no way to copy it.
     */
    public static final RegistryObject<RecipeSerializer<ResealScrollRecipe>> RESEAL_SCROLL =
            RECIPE_SERIALIZERS.register("reseal_scroll",
                    () -> new SimpleCraftingRecipeSerializer<>(ResealScrollRecipe::new));

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
