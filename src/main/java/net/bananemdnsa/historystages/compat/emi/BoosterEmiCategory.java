package net.bananemdnsa.historystages.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** EMI category listing every configured research booster (parity with JEI's BoosterRecipeCategory). */
public class BoosterEmiCategory extends EmiRecipeCategory {
    public static final ResourceLocation ID =
            new ResourceLocation(HistoryStages.MOD_ID, "research_boosters");

    public BoosterEmiCategory() {
        super(ID, EmiStack.of(new ItemStack(ModBlocks.RESEARCH_PEDESTAL.get())));
    }

    @Override
    public Component getName() {
        return Component.translatable("jei.historystages.research_booster.category");
    }
}
