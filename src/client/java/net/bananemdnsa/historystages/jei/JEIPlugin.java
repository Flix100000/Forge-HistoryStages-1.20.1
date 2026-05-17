package net.bananemdnsa.historystages.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.client.RecipeViewerVisibility;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("historystages", "jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        IIngredientSubtypeInterpreter<ItemStack> interpreter = (stack, context) -> {
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            if (tag.contains("StageResearch")) {
                return tag.getString("StageResearch");
            }
            return IIngredientSubtypeInterpreter.NONE;
        };
        registration.registerSubtypeInterpreter(ModItems.RESEARCH_SCROLL, interpreter);
        registration.registerSubtypeInterpreter(ModItems.CREATIVE_SCROLL, interpreter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.getJeiHelpers().getAllRecipeTypes().forEach(recipeType ->
                registration.addRecipeCategoryDecorator((RecipeType) recipeType, new LockedRecipeDecorator<>()));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        List<ItemStack> scrolls = new ArrayList<>();
        for (String stageId : StageManager.getStages().keySet()) {
            scrolls.add(createScrollVariant(stageId));
        }
        for (String stageId : StageManager.getIndividualStages().keySet()) {
            scrolls.add(createScrollVariant(stageId));
        }
        if (!scrolls.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, scrolls);
        }
    }

    private static ItemStack createScrollVariant(String stageId) {
        ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL);
        CompoundTag tag = new CompoundTag();
        tag.putString("StageResearch", stageId);
        scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return scroll;
    }

    private static class LockedRecipeDecorator<T> implements IRecipeCategoryDecorator<T> {
        @Override
        public void draw(T recipe, IRecipeCategory<T> category, IRecipeSlotsView slots,
                         GuiGraphics guiGraphics, double mouseX, double mouseY) {
            if (!isRecipeLocked(recipe, category, slots)) {
                return;
            }

            int width = category.getWidth();
            int height = category.getHeight();
            Font font = Minecraft.getInstance().font;
            String text = "\u00A7c\u00A7l\u2716 Locked";
            int textWidth = font.width(text);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 300);
            guiGraphics.fill(0, 0, width, height, 0xBB000000);
            guiGraphics.drawString(font, text, (width - textWidth) / 2, height / 2 - 4, 0xFFFFFF, true);
            guiGraphics.pose().popPose();
        }

        @Override
        public void decorateTooltips(ITooltipBuilder tooltip, T recipe,
                                     IRecipeCategory<T> category,
                                     IRecipeSlotsView slots,
                                     double mouseX, double mouseY) {
            if (isRecipeLocked(recipe, category, slots)) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("\u00A7c\u00A7lStage Locked"));
                tooltip.add(Component.literal("\u00A77This recipe requires a stage that"));
                tooltip.add(Component.literal("\u00A77has not been unlocked yet."));
            }
        }

        private boolean isRecipeLocked(T recipe, IRecipeCategory<T> category, IRecipeSlotsView slots) {
            ResourceLocation recipeId = category.getRegistryName(recipe);
            if (RecipeViewerVisibility.isRecipeIdLocked(recipeId == null ? null : recipeId.toString())) {
                return true;
            }

            for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
                if (slot.getDisplayedItemStack().filter(RecipeViewerVisibility::isItemRecipeActionLocked).isPresent()) {
                    return true;
                }
            }
            return false;
        }
    }
}
