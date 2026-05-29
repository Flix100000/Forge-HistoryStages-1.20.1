package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Register the locked recipe decorator globally for all categories
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());

        ResearchBoosterRegistry.forEachStack((stack, booster) -> {
            List<Component> lines = new ArrayList<>(BoosterUtil.describe(booster));
            lines.add(Component.translatable("jei.historystages.research_booster.usage"));
            List<EmiIngredient> stacks = List.of(EmiStack.of(stack));
            registry.addRecipe(new EmiInfoRecipe(stacks, lines, null));
        });
    }
}
