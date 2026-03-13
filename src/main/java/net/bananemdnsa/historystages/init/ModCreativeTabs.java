package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
    public static final CreativeModeTab HISTORY_TAB = new CreativeModeTab("historystages.history_tab") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.RESEARCH_SCROLL.get());
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("creativetab.history_tab");
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            super.fillItemList(items);

            // Dynamisch für jede geladene Stage einen Research Scroll erstellen
            for (String stageId : StageManager.getStages().keySet()) {
                ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                CompoundTag nbt = scroll.getOrCreateTag();
                nbt.putString("StageResearch", stageId);
                items.add(scroll);
            }
        }
    };

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        // In 1.19.2, creative tabs are created as static instances.
        // No DeferredRegister needed.
    }
}
