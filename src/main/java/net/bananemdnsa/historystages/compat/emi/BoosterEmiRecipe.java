package net.bananemdnsa.historystages.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One booster entry rendered as an EMI recipe (parity with JEI's BoosterRecipe). */
public class BoosterEmiRecipe extends BasicEmiRecipe {
    private static final int TEXT_X = 28;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int VALUE_COLOR = 0xFF707070;

    private final ItemStack blockStack;
    private final int speedPercent;
    private final int costPercent;

    public BoosterEmiRecipe(EmiRecipeCategory category, ItemStack blockStack,
                            int speedPercent, int costPercent) {
        super(category, recipeId(blockStack), 134, 56);
        this.blockStack = blockStack;
        this.speedPercent = speedPercent;
        this.costPercent = costPercent;
        this.catalysts.add(EmiStack.of(blockStack));
    }

    private static ResourceLocation recipeId(ItemStack blockStack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(blockStack.getItem());
        return ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID,
                "research_booster/" + item.getNamespace() + "/" + item.getPath());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(EmiStack.of(blockStack), 4, 18);

        int y = 4;
        widgets.addText(blockStack.getHoverName(), TEXT_X, y, LABEL_COLOR, false);
        y += 14;

        if (speedPercent > 0) {
            String mText = BoosterUtil.formatMultiplier(speedPercent / 100.0);
            widgets.addText(Component.translatable("jei.historystages.research_booster.speed", mText),
                    TEXT_X, y, VALUE_COLOR, false);
            y += 11;
        }
        if (costPercent > 0) {
            widgets.addText(Component.translatable("jei.historystages.research_booster.cost", costPercent),
                    TEXT_X, y, VALUE_COLOR, false);
        }
    }
}
