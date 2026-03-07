package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HistoryStages.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HISTORY_TAB = CREATIVE_MODE_TABS.register("history_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.RESEARCH_SCROLL.get()))
                    .title(Component.translatable("creativetab.history_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RESEARCH_PEDESTAL_ITEM.get());

                        for (String stageId : StageManager.getStages().keySet()) {
                            ItemStack book = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                            CompoundTag nbt = new CompoundTag();
                            nbt.putString("StageResearch", stageId);
                            book.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                            output.accept(book);
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
