package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.util.ScrollVariants;
import net.minecraft.core.registries.Registries;
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
                        // 1. Research pedestals and Creative Scroll
                        output.accept(ModItems.RESEARCH_PEDESTAL_ITEM.get());
                        output.accept(ModItems.RESEARCH_PEDESTAL_TIER_2_ITEM.get());
                        output.accept(ModItems.RESEARCH_PEDESTAL_TIER_3_ITEM.get());
                        output.accept(ModItems.RESEARCH_PEDESTAL_TIER_4_ITEM.get());

                        ItemStack creativeScroll = new ItemStack(ModItems.CREATIVE_SCROLL.get());
                        creativeScroll.getOrCreateTag().putString("StageResearch", ModItems.CREATIVE_STAGE_ID);
                        output.accept(creativeScroll);

                        // One blank open scroll, not one per stage: the tab already carries a
                        // closed scroll for every stage, and doubling that list would bury the
                        // pedestals. An untagged copy opens to the "no known research" page,
                        // which is exactly what a blank keepsake should say.
                        output.accept(new ItemStack(ModItems.RESEARCH_SCROLL_OPEN.get()));

                        // 2. Global stage scrolls (skip AUTO-mode: those have no scroll)
                        for (var stageEntry : StageManager.getStages().entrySet()) {
                            if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                            output.accept(ScrollVariants.createScroll(stageEntry.getKey()));
                        }

                        // 3. Individual stage scrolls (skip AUTO-mode)
                        for (var stageEntry : StageManager.getIndividualStages().entrySet()) {
                            if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                            output.accept(ScrollVariants.createScroll(stageEntry.getKey()));
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}