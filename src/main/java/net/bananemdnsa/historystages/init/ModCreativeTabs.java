package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HistoryStages.MOD_ID);

    public static final RegistryObject<CreativeModeTab> HISTORY_TAB = CREATIVE_MODE_TABS.register("history_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.RESEARCH_SCROLL.get())) // Das Icon des Tabs
                    .title(Component.translatable("creativetab.history_tab"))
                    .displayItems((parameters, output) -> {
                        // 1. Forschungsstation und Creative Scroll
                        output.accept(ModItems.RESEARCH_PEDESTAL_ITEM.get());

                        ItemStack creativeScroll = new ItemStack(ModItems.CREATIVE_SCROLL.get());
                        creativeScroll.getOrCreateTag().putString("StageResearch", ModItems.CREATIVE_STAGE_ID);
                        output.accept(creativeScroll);

                        // 2. Dynamisch für jede geladene Stage ein Scroll erstellen (Global)
                        for (String stageId : StageManager.getStages().keySet()) {
                            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                            CompoundTag nbt = scroll.getOrCreateTag();
                            nbt.putString("StageResearch", stageId);
                            output.accept(scroll);
                        }

                        // 3. Individual Stages
                        for (String stageId : StageManager.getIndividualStages().keySet()) {
                            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                            CompoundTag nbt = scroll.getOrCreateTag();
                            nbt.putString("StageResearch", stageId);
                            output.accept(scroll);
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}