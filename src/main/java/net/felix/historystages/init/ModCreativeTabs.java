package net.felix.historystages.init;

import net.felix.historystages.HistoryStages;
import net.felix.historystages.data.StageManager;
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
                    .icon(() -> new ItemStack(ModItems.RESEARCH_BOOK.get())) // Das Icon des Tabs
                    .title(Component.translatable("creativetab.history_tab"))
                    .displayItems((parameters, output) -> {
                        // 1. Die Forschungsstation hinzufügen
                        output.accept(ModItems.RESEARCH_STATION_ITEM.get());

                        // 2. Dynamisch für jede geladene Stage ein Buch erstellen
                        for (String stageId : StageManager.getStages().keySet()) {
                            ItemStack book = new ItemStack(ModItems.RESEARCH_BOOK.get());
                            CompoundTag nbt = book.getOrCreateTag();
                            nbt.putString("StageResearch", stageId);
                            output.accept(book);
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}