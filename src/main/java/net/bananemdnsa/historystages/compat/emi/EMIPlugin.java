package net.bananemdnsa.historystages.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.ScrollVariants;
import net.minecraft.world.item.ItemStack;

@EmiEntrypoint
public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Register the locked recipe decorator globally for all categories
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());

        // Treat scrolls with different StageResearch as distinct EMI entries (parity with JEI subtypes)
        Comparison stageComparison = Comparison.compareData(
                stack -> ScrollVariants.readStageResearch(stack.getItemStack()));
        registry.setDefaultComparison(ModItems.RESEARCH_SCROLL.get(), stageComparison);
        registry.setDefaultComparison(ModItems.CREATIVE_SCROLL.get(), stageComparison);

        // Add one scroll variant per stage so they appear in EMI
        for (ItemStack scroll : ScrollVariants.buildAllStageScrolls()) {
            registry.addEmiStack(EmiStack.of(scroll));
        }

        BoosterEmiCategory category = new BoosterEmiCategory();
        registry.addCategory(category);

        ResearchBoosterRegistry.forEachStack((stack, booster) -> {
            registry.addRecipe(new BoosterEmiRecipe(
                    category, stack,
                    BoosterUtil.percent(booster.speedReduction()),
                    BoosterUtil.percent(booster.costReduction())));
            registry.addWorkstation(category, EmiStack.of(stack));
        });

        // Pedestal opens the category too (parity with JEI registerRecipeCatalysts)
        registry.addWorkstation(category, EmiStack.of(new ItemStack(ModBlocks.RESEARCH_PEDESTAL.get())));
    }
}
