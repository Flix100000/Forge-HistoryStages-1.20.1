package net.bananemdnsa.historystages.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Custom JEI category that lists every configured research booster as its own
 * "recipe" page. Catalysts are the Research Pedestal block + every booster
 * block, so right-clicking any of them opens the category.
 */
public class BoosterRecipeCategory implements IRecipeCategory<BoosterRecipe> {
    public static final ResourceLocation UID =
            new ResourceLocation(HistoryStages.MOD_ID, "research_boosters");
    public static final RecipeType<BoosterRecipe> TYPE =
            new RecipeType<>(UID, BoosterRecipe.class);

    private static final int WIDTH = 140;
    private static final int HEIGHT = 56;
    private static final int TEXT_X = 28;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int VALUE_COLOR = 0xFF707070;

    private final IDrawable background;
    private final IDrawable icon;

    public BoosterRecipeCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ModBlocks.RESEARCH_PEDESTAL.get()));
    }

    @Override
    public RecipeType<BoosterRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.historystages.research_booster.category");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BoosterRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 18).addItemStack(recipe.blockStack());
    }

    @Override
    public void draw(BoosterRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int y = 4;

        graphics.drawString(font, recipe.blockStack().getHoverName(), TEXT_X, y, LABEL_COLOR, false);
        y += 14;

        if (recipe.speedPercent() > 0) {
            String mText = BoosterUtil.formatMultiplier(recipe.speedPercent() / 100.0);
            graphics.drawString(font,
                    Component.translatable("jei.historystages.research_booster.speed", mText),
                    TEXT_X, y, VALUE_COLOR, false);
            y += 11;
        }

        if (recipe.costPercent() > 0) {
            graphics.drawString(font,
                    Component.translatable("jei.historystages.research_booster.cost", recipe.costPercent()),
                    TEXT_X, y, VALUE_COLOR, false);
        }
    }
}
