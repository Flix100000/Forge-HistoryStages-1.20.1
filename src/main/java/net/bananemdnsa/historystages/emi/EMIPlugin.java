package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Nutzt dieselbe Config-Option wie JEI
        if (!Config.CLIENT.hideInJei.get()) return;

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            String stageId = entry.getKey();
            StageEntry stageData = entry.getValue();

            if (!ClientStageCache.isStageUnlocked(stageId)) {
                // 1. Items verstecken
                for (String itemId : stageData.getItems()) {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                    if (item != null) registry.removeEmiStacks(EmiStack.of(item));
                }

                // 2. Mods verstecken
                for (String modId : stageData.getMods()) {
                    for (Item item : ForgeRegistries.ITEMS) {
                        ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
                        if (res != null && res.getNamespace().equals(modId)) {
                            registry.removeEmiStacks(EmiStack.of(item));
                        }
                    }
                }

                // 3. Tags verstecken
                for (String tagId : stageData.getTags()) {
                    ResourceLocation tagRes = new ResourceLocation(tagId);
                    for (Item item : ForgeRegistries.ITEMS) {
                        ItemStack stack = new ItemStack(item);
                        if (stack.getTags().anyMatch(t -> t.location().equals(tagRes))) {
                            registry.removeEmiStacks(EmiStack.of(item));
                        }
                    }
                }
            }
        }
    }
}