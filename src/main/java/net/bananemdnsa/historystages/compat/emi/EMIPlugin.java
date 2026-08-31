package net.bananemdnsa.historystages.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.compat.StageDisplayPath;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

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

        registry.setDefaultComparison(ModItems.RESEARCH_SCROLL_OPEN.get(), stageComparison);

        // Add one scroll variant per stage so they appear in EMI
        for (ItemStack scroll : ScrollVariants.buildAllStageScrolls()) {
            registry.addEmiStack(EmiStack.of(scroll));
        }

        // The resealing recipe is a special recipe with no declared ingredients, so no viewer can
        // derive it — one display entry per stage is added by hand. EMI can show the open scroll
        // as a remainder, which is exactly what happens: only the paper is spent.
        if (Config.GAMEPLAY.enableScrollResealing.get()) {
            for (String stageId : ScrollVariants.scrollableStageIds()) {
                EmiStack open = EmiStack.of(ScrollVariants.createOpenScroll(stageId));
                registry.addEmiStack(open);
                registry.addRecipe(new EmiCraftingRecipe(
                        List.of(open.copy().setRemainder(open.copy()), EmiStack.of(Items.PAPER)),
                        EmiStack.of(ScrollVariants.createScroll(stageId)),
                        ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID,
                                "reseal_scroll/" + StageDisplayPath.of(stageId))));
            }
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
