package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ModCreativeTabs {
    public static final CreativeModeTab HISTORY_TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("creativetab.history_tab"))
            .icon(ModItems::createCreativeScrollStack)
            .displayItems((parameters, output) -> {
                output.accept(ModItems.RESEARCH_PEDESTAL_ITEM);
                output.accept(ModItems.createCreativeScrollStack());

                for (String stageId : StageManager.getStages().keySet()) {
                    output.accept(createResearchScroll(stageId));
                }
                for (String stageId : StageManager.getIndividualStages().keySet()) {
                    output.accept(createResearchScroll(stageId));
                }
            })
            .build();

    private ModCreativeTabs() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HistoryStages.id("history_tab"), HISTORY_TAB);
    }

    private static ItemStack createResearchScroll(String stageId) {
        ItemStack stack = new ItemStack(ModItems.RESEARCH_SCROLL);
        net.minecraft.nbt.CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("StageResearch", stageId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
